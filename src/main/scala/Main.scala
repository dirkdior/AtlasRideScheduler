import akka.actor.ActorSystem
import akka.stream.IOResult
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl._
import akka.util.ByteString

import java.nio.file.Paths
import scala.concurrent.Future

object Main extends App {

  implicit val system: ActorSystem = ActorSystem("atlas-ride-scheduler-system")

//  Try(
//    getClass.getClassLoader.getResource("test_data/ride_schedule.csv").getPath
//  ) match {
//    case Success(filePath) =>
//      val file = Paths.get(filePath)
//      val source = FileIO.fromPath(file)
//
//      source
//        .via(CsvParsing.lineScanner())
//        .via(Flow[List[ByteString]].map(_.map(_.utf8String)))
//        .runWith(Sink.foreach(println))
//
//      source
//        .via(Flow[ByteString].map(_.utf8String))
//        .runWith(Sink.foreach(println))
//
//    case Failure(ex) =>
//      println(s"Error while getting path for ride_schedule file: $ex")
//  }

  val rideScheduleSource = createSourceFromFile("ride_schedule.csv")
  val stopDetailsSource = createSourceFromFile("stop_details.csv")

  val rideGraph = rideScheduleSource
    .via(CsvParsing.lineScanner())
    .drop(1)
    .via(Flow[List[ByteString]].map(_.map(_.utf8String)))

  stopDetailsSource
    .via(CsvParsing.lineScanner())
    .drop(1)
    .via(Flow[List[ByteString]].map(_.map(_.utf8String)))
    .fold(Map[String, String]())((m, e) => {
      m + (e.head -> e.last)
    })
    .flatMapConcat(a =>
      rideGraph
        .via(
          Flow[List[String]].map(b =>
            (a.get(b(b.length - 2)), a.get(b.last)) match {
              case (Some(value1), Some(value2)) =>
                b.splitAt(3)._1 ++ List(value1, value2)
              case (None, Some(value)) =>
                b.updated(b.length - 1, value)
              case (Some(value), None) =>
                b.updated(b.length - 2, value)
              case _ =>
                b
            }
          )
        )
    )
    .runWith(Sink.foreach(println))

  def createSourceFromFile(
      fileName: String
  ): Source[ByteString, Future[IOResult]] =
    FileIO.fromPath(
      Paths.get(
        getClass.getClassLoader.getResource(s"test_data/$fileName").getPath
      )
    )

}

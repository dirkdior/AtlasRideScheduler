import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, IOResult}
import akka.stream.alpakka.csv.scaladsl.{CsvFormatting, CsvParsing}
import akka.stream.scaladsl.{
  Broadcast,
  FileIO,
  Flow,
  GraphDSL,
  RunnableGraph,
  Sink,
  Source
}
import akka.util.ByteString

import java.nio.file.Paths
import scala.collection.immutable.TreeMap
import scala.concurrent.Future

class AtlasSolution()(implicit val system: ActorSystem) {
  val rideScheduleSource: Source[ByteString, Future[IOResult]] =
    createSourceFromFile("ride_schedule.csv")
  val stopDetailsSource: Source[ByteString, Future[IOResult]] =
    createSourceFromFile("stop_details.csv")

  val csvToStringFlow: Flow[ByteString, List[String], NotUsed] =
    CsvParsing
      .lineScanner()
      .via(Flow[List[ByteString]].map(_.map(_.utf8String)))

  val stop_details_enriched_flow: Flow[ByteString, List[String], NotUsed] =
    csvToStringFlow
      .fold(Map[String, String]())((m, e) => {
        m + (e.head -> e.last)
      })
      .flatMapConcat(a =>
        rideScheduleSource
          .via(csvToStringFlow)
          .via(
            Flow[List[String]].map(b =>
              b.head.toLowerCase.filterNot(_.isWhitespace) match {
                case "rideid" =>
                  b.splitAt(5)._1 ++ List("fromStopName", "toStopName")
                case _ =>
                  (a.get(b(b.length - 2)), a.get(b.last)) match {
                    case (Some(value1), Some(value2)) =>
                      b ++ List(value1, value2)
                    case (None, Some(value)) =>
                      b ++ List(b(b.length - 2), value)
                    case (Some(value), None) =>
                      b ++ List(value, b.last)
                    case _ =>
                      b ++ List(b(b.length - 2), b.last)
                  }
              }
            )
          )
      )

  val ride_schedule_flattened_flow: Flow[List[String], List[String], NotUsed] =
    Flow[List[String]]
      .map(b =>
        b.head.toLowerCase.filterNot(_.isWhitespace) match {
          case "rideid" =>
            val newList =
              b.splitAt(2)._1 ++ List("StopSequence", "stopId", "stopName")
            List(newList, newList)
          case _ =>
            val newStopSequence = (b(2).toInt + 1).toString
            List(
              b.splitAt(3)._1 ++ List(b(3), b(5)),
              b.splitAt(2)._1 ++ List(newStopSequence, b(4), b(6))
            )
        }
      )
      .mapConcat(identity)
      .fold(TreeMap[(Int, Int), List[String]]())((m, e) => {
        e.head.toLowerCase.filterNot(_.isWhitespace) match {
          case "rideid" =>
            m + ((0, 0) -> e)
          case _ =>
            m + ((e.head.toInt, e(2).toInt) -> e)
        }
      })
      .map(m => m.values.toList)
      .mapConcat(identity)

  val stop_details_enriched_sink: Sink[ByteString, Future[IOResult]] =
    writeToSinkFile("stop_details_enriched")
  val ride_schedule_flattened_sink: Sink[ByteString, Future[IOResult]] =
    writeToSinkFile("stop_details_flattened")

  val graph: RunnableGraph[NotUsed] =
    RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[List[String]](2))

      stopDetailsSource ~> stop_details_enriched_flow ~> broadcast.in
      broadcast.out(0) ~> CsvFormatting.format() ~> stop_details_enriched_sink
      broadcast.out(1) ~> ride_schedule_flattened_flow ~> CsvFormatting
        .format() ~> ride_schedule_flattened_sink

      ClosedShape
    })

  def createSourceFromFile(
      fileName: String
  ): Source[ByteString, Future[IOResult]] = {
    FileIO.fromPath(
      Paths.get(
        getClass.getClassLoader.getResource(s"test_data/$fileName").getPath
      )
    )
  }

  def writeToSinkFile(
      fileName: String
  ): Sink[ByteString, Future[IOResult]] = {
    FileIO.toPath(
      Paths.get(
        s"./result/$fileName.csv"
      )
    )
  }

}

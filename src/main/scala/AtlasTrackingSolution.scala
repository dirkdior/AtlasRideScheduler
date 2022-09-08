import akka.NotUsed
import akka.stream.IOResult
import akka.stream.alpakka.csv.scaladsl.CsvParsing
import akka.stream.scaladsl.{FileIO, Flow, RunnableGraph, Sink, Source}
import akka.util.ByteString

import org.geolatte.geom.ProjectedGeometryOperations
import org.geolatte.geom.builder.DSL._
import org.geolatte.geom._
import org.geolatte.geom.crs.CoordinateReferenceSystems.WEB_MERCATOR

import java.nio.file.Paths
import scala.concurrent.Future

object AtlasTrackingSolution {

  val source = createSourceFromFile("tracking_data.csv")

  val pgo: ProjectedGeometryOperations = ProjectedGeometryOperations.Default
  val restrictedArea: Polygon[C2D] = polygon(
    WEB_MERCATOR,
    ring(
      c(28.835163116455078, 41.24160999808356),
      c(28.831558227539062, 41.232638431545986),
      c(28.84623527526855, 41.22327829087563),
      c(28.855504989624023, 41.23851204236082),
      c(28.835163116455078, 41.24160999808356)
    )
  )

//  val restrictedArea = ConvexPolygon(
//    Vec2Array(
//      Vec2(28.835163116455078, 41.24160999808356),
//      Vec2(28.831558227539062, 41.232638431545986),
//      Vec2(28.84623527526855, 41.22327829087563),
//      Vec2(28.855504989624023, 41.23851204236082),
//      Vec2(28.835163116455078, 41.24160999808356)
//    )
//  )

  val csvToStringFlow: Flow[ByteString, List[String], NotUsed] =
    CsvParsing
      .lineScanner()
      .via(Flow[List[ByteString]].map(_.map(_.utf8String)))

  val mainFlow: RunnableGraph[Future[IOResult]] = source
    .via(csvToStringFlow)
    .drop(1)
    .via(
      Flow[List[String]]
        .map(b =>
          b.head.toLowerCase.filterNot(_.isWhitespace) match {
            case "rideid" =>
              b ++ List("EnterRestrictedArea")
            case _ =>
              val circlePoint: Point[C2D] =
                point(WEB_MERCATOR, c(b(2).toDouble, b(3).toDouble))
              b ++ List(pgo.contains(restrictedArea, circlePoint).toString)
          }
        )
        .fold(Map[String, List[String]]())((m, e) => {
          e.head.toLowerCase.filterNot(_.isWhitespace) match {
            case "rideid" =>
              m + (e.head -> List(
                e.head,
                "EnterRestrictedArea",
                "fromTime",
                "toTime"
              ))
            case _ =>
              e.last match {
                case "true" =>
                  m.get(e.head) match {
                    case Some(value) =>
                      if (value.contains("null")) {
                        m + (e.head -> List(e.head, e.last, value.last, e(1)))
                      } else {
                        m + (e.head -> List(e.head, e.last, value(2), e(1)))
                      }
                    case None =>
                      m + (e.head -> List(e.head, e.last, "null", e(1)))
                  }
                case "false" =>
                  m.get(e.head) match {
                    case Some(value) =>
                      if (value.head == "false") {
                        m + (e.head -> List(e.head, e.last, "null", "null"))
                      } else m
                    case None =>
                      m + (e.head -> List(e.head, e.last, "null", "null"))
                  }
              }
          }
        })
        .map(m => m.values.toList)
        .mapConcat(identity)
    )
    .to(Sink.foreach(println))

  def createSourceFromFile(
      fileName: String
  ): Source[ByteString, Future[IOResult]] = {
    FileIO.fromPath(
      Paths.get(
        getClass.getClassLoader.getResource(s"test_data/$fileName").getPath
      )
    )
  }
}

import akka.actor.ActorSystem
import akka.stream.alpakka.csv.scaladsl.{CsvFormatting, CsvParsing}
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.IOResult
import akka.util.ByteString
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.{File, PrintWriter}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class AtlasSolutionSpec
    extends AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem(
    "atlas-ride-scheduler-test-system"
  )

  val rideDetails =
    "\"rideId\",\"departureDate\",\"segmentSequence\",\"fromStopId\",\"toStopId\"\n\"124603531\",\"2021-06-02\",\"1\",\"2425\",\"12448\"\n\"124603531\",\"2021-06-02\",\"2\",\"12448\",\"15228\""
  val stopDetails =
    "\"stopId\",\"stopName\"\n\"2425\",\"Rome Tiburtina Bus station\"\n\"12448\",\"Avezzano\"\n\"15228\",\"Ascoli Piceno\""

  val testRideScheduleSource: Source[ByteString, Future[IOResult]] =
    sourceFromTempFile(rideDetails)
  val testStopDetailsSource: Source[ByteString, Future[IOResult]] =
    sourceFromTempFile(stopDetails)

  val streamResource: AtlasSolution = new AtlasSolution() {
    override def createSourceFromFile(
        fileName: String
    ): Source[ByteString, Future[IOResult]] = fileName match {
      case "ride_schedule.csv" => testRideScheduleSource
      case "stop_details.csv"  => testStopDetailsSource
    }

    override def writeToSinkFile(
        fileName: String
    ): Sink[ByteString, Future[IOResult]] = tempFileForSink(fileName)
  }

  "The StreamResource" must {
    "load valid csv sources" in {

      val rideScheduleSourceTest = streamResource.rideScheduleSource

      rideScheduleSourceTest
        .via(CsvParsing.lineScanner())
        .via(Flow[List[ByteString]].map(_.map(_.utf8String)))
        .runWith(TestSink[List[String]]())
        .request(3)
        .expectNext(
          List(
            "rideId",
            "departureDate",
            "segmentSequence",
            "fromStopId",
            "toStopId"
          ),
          List("124603531", "2021-06-02", "1", "2425", "12448"),
          List("124603531", "2021-06-02", "2", "12448", "15228")
        )
        .expectComplete()

      val stopDetailsSourceTest = streamResource.stopDetailsSource

      stopDetailsSourceTest
        .via(CsvParsing.lineScanner())
        .via(Flow[List[ByteString]].map(_.map(_.utf8String)))
        .runWith(TestSink[List[String]]())
        .request(4)
        .expectNext(
          List(
            "stopId",
            "stopName"
          ),
          List("2425", "Rome Tiburtina Bus station"),
          List("12448", "Avezzano"),
          List("15228", "Ascoli Piceno")
        )
        .expectComplete()
    }

    "yield valid flow results" in {
      val test_stop_details_enriched_flow =
        streamResource.stop_details_enriched_flow

      test_stop_details_enriched_flow
        .runWith(TestSink[List[String]]())
        .request(3)
        .expectNext(
          List(
            "rideId",
            "departureDate",
            "segmentSequence",
            "fromStopId",
            "toStopId",
            "fromStopName",
            "toStopName"
          ),
          List(
            "124603531",
            "2021-06-02",
            "1",
            "2425",
            "12448",
            "Rome Tiburtina Bus station",
            "Avezzano"
          ),
          List(
            "124603531",
            "2021-06-02",
            "2",
            "12448",
            "15228",
            "Avezzano",
            "Ascoli Piceno"
          )
        )
        .expectComplete()

      val test_ride_schedule_flattened_flow =
        streamResource.ride_schedule_flattened_flow

      test_ride_schedule_flattened_flow
        .runWith(TestSink[List[String]]())
        .request(4)
        .expectNext(
          List(
            "rideId",
            "departureDate",
            "StopSequence",
            "stopId",
            "stopName"
          ),
          List(
            "124603531",
            "2021-06-02",
            "1",
            "2425",
            "Rome Tiburtina Bus station"
          ),
          List("124603531", "2021-06-02", "2", "12448", "Avezzano"),
          List("124603531", "2021-06-02", "3", "15228", "Ascoli Piceno")
        )
        .expectComplete()
    }

    "successfully sink to files" in {

      val enrichedDetailsTestSource = Source(
        List(
          List(
            "rideId",
            "departureDate",
            "segmentSequence",
            "fromStopId",
            "toStopId",
            "fromStopName",
            "toStopName"
          ),
          List(
            "124603531",
            "2021-06-02",
            "1",
            "2425",
            "12448",
            "Rome Tiburtina Bus station",
            "Avezzano"
          ),
          List(
            "124603531",
            "2021-06-02",
            "2",
            "12448",
            "15228",
            "Avezzano",
            "Ascoli Piceno"
          )
        )
      )

      val flattenedScheduleTestSource = Source(
        List(
          List(
            "rideId",
            "departureDate",
            "segmentSequence",
            "fromStopId",
            "toStopId"
          ),
          List("124603531", "2021-06-02", "1", "2425", "12448"),
          List("124603531", "2021-06-02", "2", "12448", "15228")
        )
      )

      val stop_details_enriched_fut = enrichedDetailsTestSource
        .via(CsvFormatting.format())
        .toMat(streamResource.stop_details_enriched_sink)(Keep.right)
        .run()

      val ride_schedule_flattened_fut = flattenedScheduleTestSource
        .via(CsvFormatting.format())
        .toMat(streamResource.ride_schedule_flattened_sink)(Keep.right)
        .run()

      assert(
        Await.result(stop_details_enriched_fut, 3.seconds).status.isSuccess
      )

      assert(
        Await.result(ride_schedule_flattened_fut, 3.seconds).status.isSuccess
      )
    }
  }

  def sourceFromTempFile(
      contents: String
  ): Source[ByteString, Future[IOResult]] = {
    val tempFi = File.createTempFile(
      "atlas_test",
      ".csv"
    )
    tempFi.deleteOnExit()
    new PrintWriter(tempFi) {
      try {
        write(contents)
      } finally {
        close()
      }
    }
    FileIO.fromPath(tempFi.toPath)
  }

  def tempFileForSink(
      fileName: String
  ): Sink[ByteString, Future[IOResult]] = {
    val tempFi = File.createTempFile(
      fileName,
      ".csv"
    )
    tempFi.deleteOnExit()

    FileIO.toPath(tempFi.toPath)
  }

  override def afterAll(): Unit = {
    Await.ready(system.terminate(), FiniteDuration(100, SECONDS))
  }
}

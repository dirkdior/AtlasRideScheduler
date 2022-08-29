import akka.actor.{ActorSystem, Props}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Main extends App {

  implicit val system: ActorSystem = ActorSystem("atlas-ride-scheduler-system")

  val atlasMonitor =
    system.actorOf(Props[AtlasMonitor], name = "AtlasMonitorActor")

  val streamResource = new AtlasSolution()
  val graphResult = streamResource.graph.run()

  graphResult._1 onComplete {
    case Success(value) =>
      atlasMonitor ! value
    case Failure(ex) =>
      println(ex)
  }
  graphResult._2 onComplete {
    case Success(value) =>
      atlasMonitor ! value
    case Failure(ex) =>
      println(ex)
  }

}

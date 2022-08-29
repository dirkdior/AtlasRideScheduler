import akka.actor.ActorSystem

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Main extends App {

  implicit val system: ActorSystem = ActorSystem("atlas-ride-scheduler-system")

  val streamResource = new AtlasSolution()
  streamResource.graph.run()
}

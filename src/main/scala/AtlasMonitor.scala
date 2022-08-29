import akka.actor.Actor
import akka.stream.IOResult

class AtlasMonitor extends Actor {
  var materializedStreams = 0
  override def receive: Receive = {
    case req: IOResult =>
      println(req)
      materializedStreams += 1
      if(materializedStreams == 2)
        context.system.terminate()

  }
}

package consumer

import akka.actor.ActorRef
import com.spingo.op_rabbit._
import Directives._

import scala.concurrent.ExecutionContext.Implicits.global

class CharCountComponent(rabbitControl: ActorRef) {
  var lastMessageCount: Int = 0


  def start(): SubscriptionRef = {
    Subscription.run(rabbitControl) {
      channel(qos = 1) {
        consume(topic(queue("my-queue", true), List("some-topic.#"))) {
          (body(as[String])) {
            (message) =>
              println(message)
              println("well I did run....")
              lastMessageCount = message.size
              ack
          }
        }
      }
    }
  }

}

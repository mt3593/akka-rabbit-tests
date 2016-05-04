import akka.actor.{ActorSystem, Props}
import com.spingo.op_rabbit._
import Directives._

import scala.concurrent.ExecutionContext.Implicits.global

class Rabbit(exchangeName: String, queueName: String) {
  lazy val rabbitControl = actorSystem.actorOf(Props[RabbitControl])
  val actorSystem = ActorSystem("such-system")

  val q = queue(queueName, true)

  val subscriptionRef = {
    Subscription.run(rabbitControl) {
      channel(qos = 1) {
        consume(topic(q, List())) {
          (body(as[String])) {
            (message) =>
              println(message)
              println("well I did run....")
              ack
          }
        }
      }
    }
  }

  def sendMessage(message: String) = {
    subscriptionRef.initialized.foreach { _ =>
      rabbitControl ! Message.topic(message, routingKey = "root")
    }
  }


}

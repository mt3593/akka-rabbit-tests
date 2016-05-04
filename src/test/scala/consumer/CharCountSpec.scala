package consumer

import java.util.concurrent.TimeoutException

import akka.actor.{ActorSystem, Props}
import com.spingo.op_rabbit.Directives._
import com.spingo.op_rabbit._
import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.util.Random

class CharCountSpec extends FlatSpec with Matchers {
  val actorSystem = ActorSystem("such-system")
  val rabbitControl = actorSystem.actorOf(Props[RabbitControl])

  // use FlatSpec for unit tests
  // FeatureSpec for acceptance tests

  "A message" should "be consumed" in {
    val charCount = new CharCountComponent(rabbitControl)
    val subscriptionRef = charCount.start()
    subscriptionRef.initialized.foreach { _ =>
      rabbitControl ! Message.topic("This is a message", "some-topic.cool")
    }
    Thread.sleep(1000)
    subscriptionRef.close()
    charCount.lastMessageCount should be(17)

  }

  it should "only put messages on set queues for direct exchange" in {
    // given
    val actorSystem = ActorSystem("such-system")
    val rabbitControl = actorSystem.actorOf(Props[RabbitControl])
    val message: String = "This is a message"
    val exchangeName: String = randomName()
    val consumeQueueA = directConsume(exchangeName, randomName(), List("RoutingKey1"))
    val consumeQueueB = directConsume(exchangeName, randomName(), List("RoutingKey2"))
    // when
    rabbitControl ! Message.exchange(message, exchangeName, "RoutingKey1")
    // then
    assertResult(message) {
      Await.result(consumeQueueA, 10.seconds)
    }
    intercept[TimeoutException] {
      Await.result(consumeQueueB, 10.seconds)
    }
  }

  it should "put messages on on all queues with same RoutingKeys" in {
    // given
    val actorSystem = ActorSystem("such-system")
    val rabbitControl = actorSystem.actorOf(Props[RabbitControl])
    val message: String = "This is a message"
    val exchangeName: String = randomName()
    val consumeQueueA = directConsume(exchangeName, randomName(), List("RoutingKey1"))
    val consumeQueueB = directConsume(exchangeName, randomName(), List("RoutingKey1", "RoutingKey2"))
    // when
    rabbitControl ! Message.exchange(message, exchangeName, "RoutingKey1")
    // then
    assertResult(message) {
      Await.result(consumeQueueA, 10.seconds)
    }
    assertResult(message) {
      Await.result(consumeQueueB, 10.seconds)
    }
  }

  "fanout" should "put messages on on all queues" in {
    // given
    val actorSystem = ActorSystem("such-system")
    val rabbitControl = actorSystem.actorOf(Props[RabbitControl])
    val message: String = "This is a message"
    val exchangeName: String = randomName()
    val consumeQueueA = fanoutConsume(exchangeName, randomName())
    val consumeQueueB = fanoutConsume(exchangeName, randomName())
    // when
    rabbitControl ! Message.exchange(message, exchangeName)
    // then
    assertResult(message) {
      Await.result(consumeQueueA, 10.seconds)
    }
    assertResult(message) {
      Await.result(consumeQueueB, 10.seconds)
    }
  }

  def fanoutConsume(exchangeName: String, queueName: String): Future[String] = {
    consumeExchange(Binding.fanout(queue(queueName, durable = false, autoDelete = true),
      Exchange.fanout(exchangeName, autoDelete = true, durable = false)))
  }

  def directConsume(exchangeName: String, queueName: String, routing: List[String]): Future[String] = {
    consumeExchange(Binding.direct(queue(queueName, durable = false, autoDelete = true),
      Exchange.direct(exchangeName, autoDelete = true, durable = false), routing))
  }

  def consumeExchange(binding: Binding): Future[String] = {
    val messageFromQueue = Promise[String]
    val subscriptionRef: SubscriptionRef = Subscription.run(rabbitControl) {
      channel(qos = 1) {
        consume(binding) {
          body(as[String]) {
            message =>
              messageFromQueue.success(message)
              ack
          }
        }
      }
    }
    Await.ready(subscriptionRef.initialized, 5.seconds)
    Future[String] {
      val result: String = Await.result(messageFromQueue.future, 10.seconds)
      subscriptionRef.close()
      result
    }
  }

  def randomName(size: Int = 5): String = {
    val x = Random.alphanumeric
    (x take size).foldLeft[String]("") { (r: String, a: Char) => r + a }
  }
}

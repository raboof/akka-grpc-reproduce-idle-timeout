package com.example.helloworld

//#import
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import scala.util.Success
import akka.Done
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.stream.scaladsl.Source
import sun.jvm.hotspot.runtime.Threads

import java.util.concurrent.TimeUnit

//#import

//#client-request-reply
object GreeterClient {

  def main(args: Array[String]): Unit = {
    implicit val sys: ActorSystem[_] = ActorSystem(Behaviors.empty, "GreeterClient")
    implicit val ec: ExecutionContext = sys.executionContext

    val client = GreeterServiceClient(
      GrpcClientSettings.fromConfig("helloworld.GreeterService")
        .withChannelBuilderOverrides(cb => {
          cb
            .keepAliveTime(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
        })
    )

    val names =
      if (args.isEmpty) List("Alice", "Bob")
      else args.toList

    names.foreach(singleRequestReply)

    //#client-request-reply
    if (args.nonEmpty)
      names.foreach(streamingBroadcast)
    //#client-request-reply

    Thread.sleep(10000)

    def singleRequestReply(name: String): Unit = {
      println(s"Performing request: $name")
      val reply = client.sayHello(HelloRequest(name))
      reply.onComplete {
        case Success(msg) =>
          println(msg)
        case Failure(e) =>
          println(s"Error: $e")
      }
    }

    //#client-request-reply
    //#client-stream
    def streamingBroadcast(name: String): Unit = {
      println(s"Performing streaming requests: $name")

      val requestStream: Source[HelloRequest, NotUsed] =
        Source
          .tick(10.second, 10.second, "tick")
          .zipWithIndex
          .map { case (_, i) => i }
          .map(i => HelloRequest(s"$name-$i"))
          .mapMaterializedValue(_ => NotUsed)

      val responseStream: Source[HelloReply, NotUsed] = client.sayHelloToAll(requestStream)
      val done: Future[Done] =
        responseStream.runForeach(reply => println(s"$name got streaming reply: ${reply.message}"))

      done.onComplete {
        case Success(_) =>
          println("streamingBroadcast done")
        case Failure(e) =>
          println(s"Error streamingBroadcast: $e")
      }
    }
    //#client-stream
    //#client-request-reply

    sys.terminate()
  }

}
//#client-request-reply

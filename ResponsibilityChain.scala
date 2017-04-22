package dp

import scala.annotation.tailrec
import scala.util.Try
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.util.Success
import scala.util.Failure
import akka.dispatch.Futures
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.lang.scala.Observable
import rx.schedulers.Schedulers
import rx.lang.scala.schedulers.ComputationScheduler

case class Input(value: Char)
case class Output(value: String)

/**
 * @author liuwenzhe2008@qq.com
 */
object ResponsibilityChain {
  def main(args: Array[String]) {
    val cafebabe = "cafebabe wenzhe"
    println("----- 0. NoDp -----")
    for (ch <- cafebabe) {
      NoDp handle Input(ch) map (_.value) foreach println
    }
    println("----- 1. Oop1: inherit -----")
    for (ch <- cafebabe) {
      Oop1 handle Input(ch) map (_.value) foreach println
    }
    println("----- 2. Oop2: aggregation -----")
    for (ch <- cafebabe) {
      Oop2 handle Input(ch) map (_.value) foreach println
    }
    println("----- 3. MixOopFp -----")
    for (ch <- cafebabe) {
      MixOopFp handle Input(ch) map (_.value) foreach println
    }
    println("----- 4. Fp1: partial apply function -----")
    for (ch <- cafebabe) {
      Fp1 handle Input(ch) map (_.value) foreach println
    }
    println("----- 5. Fp2: partial function -----")
    for (ch <- cafebabe) {
      Fp2 handle Input(ch) map (_.value) foreach println
    }

    println("----- 6. Rp1: akka actor Reactive Programming -----")
    val futures = cafebabe map (Rp1 handle Input(_))
    futures foreach (_.onComplete {
      case Success(output) => output map (_.value) foreach println
      case Failure(exception) => exception printStackTrace
    })
    val mergedFuture = Future sequence futures
    val outputs = Await result (mergedFuture, 5 seconds)
    println("----- 6. Rp1: Now all output ready, print by sequence -----")
    outputs.flatten map (_.value) foreach println
    Rp1.system terminate

    println("----- 7. Rp2: RX Reactive Programming (single thread)-----")
    Observable from cafebabe map (Input(_)) flatMap (Rp2 handle _) map (_.value) foreach println

    println("----- 7. Rp2: RX Reactive Programming (multiple thread)-----")
    Observable from cafebabe map (Input(_)) flatMap (Rp2 asyncHandle _) map (_.value) foreach println
    Thread sleep 1000
  }

  private def canHandleByCustomService(request: Input) = "aw" contains request.value
  private def handleByCustomService(request: Input) = Output("Customer Service handles " + request.value)
  private def canHandleByManager(request: Input) = "bwz" contains request.value
  private def handleByManager(request: Input) = Output("Manager handles " + request.value)
  private def canHandleByEngineer(request: Input) = "cz" contains request.value
  private def handleByEngineer(request: Input) = Output("Engineer handles " + request.value)

  object NoDp {
    def handle(request: Input): Option[Output] =
        if (canHandleByCustomService(request)) {
          Some(handleByCustomService(request))
        } else if (canHandleByManager(request)) {
          Some(handleByManager(request))
        } else if (canHandleByEngineer(request)) {
          Some(handleByEngineer(request))
        } else {
          None
        }
  }

  // OOP1: inherit
  object Oop1 {
    val customerService = new CustomerService
    val manager = new Manager
    val engineer = new Engineer
    customerService.next = manager
    manager.next = engineer

    def handle(request: Input): Option[Output] = {
        customerService handle request
    }

    abstract class HandlerNode {
      var next: HandlerNode = _
      @tailrec final def handle(request: Input): Option[Output] = {
          if (canHandle(request)) Some(doHandle(request))
          else if (next == null) None
          else next handle request
    }
    protected def canHandle(request: Input): Boolean
    protected def doHandle(request: Input): Output
    }

    class CustomerService extends HandlerNode {
      override protected def canHandle(request: Input): Boolean = canHandleByCustomService(request)
      override protected def doHandle(request: Input): Output = handleByCustomService(request)
    }

    class Manager extends HandlerNode {
      override protected def canHandle(request: Input): Boolean = canHandleByManager(request)
      override protected def doHandle(request: Input): Output = handleByManager(request)
    }

    class Engineer extends HandlerNode {
      override protected def canHandle(request: Input): Boolean = canHandleByEngineer(request)
      override protected def doHandle(request: Input): Output = handleByEngineer(request)
    }
  }

  // Oop2: aggregation
  object Oop2 {
    val customerService = new Node(new CustomerService)
    val manager = new Node(new Manager)
    val engineer = new Node(new Engineer)
    customerService.nextNode = manager
    manager.nextNode = engineer

    def handle(request: Input): Option[Output] = {
        customerService.handle(request)
    }

    trait Handler {
      def canHandle(request: Input): Boolean
      def handle(request: Input): Output
    }

    class Node(handler: Handler) {
      var nextNode: Node = _
      @tailrec final def handle(request: Input): Option[Output] = {
          if (handler canHandle request) Some(handler handle request)
          else if (nextNode == null) None
          else nextNode handle request
      }
    }

    class CustomerService extends Handler {
      def canHandle(request: Input): Boolean = canHandleByCustomService(request)
      def handle(request: Input): Output = handleByCustomService(request)
    }

    class Manager extends Handler {
      def canHandle(request: Input): Boolean = canHandleByManager(request)
      def handle(request: Input): Output = handleByManager(request)
    }

    class Engineer extends Handler {
      def canHandle(request: Input): Boolean = canHandleByEngineer(request)
      def handle(request: Input): Output = handleByEngineer(request)
    }
  }
  
  object MixOopFp {
    val customerService = new Node(canHandleByCustomService, handleByCustomService)
    val manager = new Node(canHandleByManager, handleByManager)
    val engineer = new Node(canHandleByEngineer, handleByEngineer)
    
    customerService.nextNode = manager
    manager.nextNode = engineer

    def handle(request: Input): Option[Output] = {
        customerService.handle(request)
    }
    
    class Node(canHandle: Input => Boolean, doHandle: Input => Output) {
      var nextNode: Node = _
      @tailrec final def handle(request: Input): Option[Output] = {
        if (canHandle(request)) Some(doHandle(request))
        else if (nextNode == null) None
        else nextNode handle request
      }
    }
  }

  // Fp1: partial apply function
  object Fp1 {
    val customerService = handleByChainNode(canHandleByCustomService)(handleByCustomService) _
    val manager = handleByChainNode(canHandleByManager)(handleByManager) _
    val engineer = handleByChainNode(canHandleByEngineer)(handleByEngineer) _
    val handleChain = customerService(manager(engineer(null)))

    def handle(request: Input): Option[Output] = {
        handleChain(request)
    }

    def handleByChainNode(canHandle: Input => Boolean)
                         (handle: Input => Output)
                         (nextHandle: Input => Option[Output])
                         (request: Input): Option[Output] = {
      if (canHandle(request)) Some(handle(request))
      else if (nextHandle == null) None
      else nextHandle(request)
    }
  }

  // Fp2: partial function
  object Fp2 {
    val customService = handler(canHandleByCustomService, handleByCustomService)
    val manager = handler(canHandleByManager, handleByManager)
    val engineer = handler(canHandleByEngineer, handleByEngineer)
    val handleChain = customService orElse manager orElse engineer

    def handle(request: Input): Option[Output] = {
      handleChain lift request
    }

    def handler(canHandle: Input => Boolean, handle: Input => Output): PartialFunction[Input, Output] = {
      case request: Input if canHandle(request) => handle(request)
    }
  }

  // Rp1: akka actor Reactive Programming
  object Rp1 {
    val system = ActorSystem("ActorSystem")
    val customService = system actorOf (Props(new HandlerActor(canHandleByCustomService, handleByCustomService)), "customService")
    val manager = system actorOf (Props(new HandlerActor(canHandleByManager, handleByManager)), "manager")
    val engineer = system actorOf (Props(new HandlerActor(canHandleByEngineer, handleByEngineer)), "engineer")
    customService ! SetNextHandler(manager)
    manager ! SetNextHandler(engineer)

    def handle(request: Input): Future[Option[Output]] = {
        implicit val timeout = Timeout(5 seconds)
        val future = customService ? Handle(request)
        future.mapTo[Result] map (_.value)
    }

  case class SetNextHandler(nextHandler: ActorRef)
  case class Handle(request: Input)
  case class Result(value: Option[Output])

  class HandlerActor(canHandle: Input => Boolean, handle: Input => Output) extends Actor with ActorLogging {
      private var nextHandler: ActorRef = _
      def receive = {
        case SetNextHandler(nextHandler) => this.nextHandler = nextHandler
        case handleEvent @ Handle(request) => {
          log debug s"${request.value}"
          if (canHandle(request)) sender ! Result(Some(handle(request)))
          else if (nextHandler == null) sender ! Result(None)
          else nextHandler forward handleEvent
        }
      }
    }
  }

  // Rp2: RX Reactive Programming
  object Rp2 {
    val customService = handler(canHandleByCustomService)(handleByCustomService) _
    val manager = handler(canHandleByManager)(handleByManager) _
    val engineer = handler(canHandleByEngineer)(handleByEngineer) _

    def handle(request: Input): Observable[Output] = {
        customService(request) switchIfEmpty manager(request) switchIfEmpty engineer(request)
    }

    def asyncHandle(request: Input): Observable[Output] = {
        val asyncHandleByCustomService = customService(request) subscribeOn ComputationScheduler()
        val asyncHandleByManager = manager(request) subscribeOn ComputationScheduler()
        val asyncHandleByEngineer = engineer(request) subscribeOn ComputationScheduler()
        asyncHandleByCustomService switchIfEmpty asyncHandleByManager switchIfEmpty asyncHandleByEngineer
    }

    def handler(canHandle: Input => Boolean)(handle: Input => Output)(request: Input) = {
      Observable just request filter canHandle map handle
    }
  }

  trait Loggable {
    def log = LoggerFactory getLogger getClass
  }
}

/* console output:
----- 0. NoDp -----
Engineer handles c
Customer Service handles a
Manager handles b
Customer Service handles a
Manager handles b
Customer Service handles w
Manager handles z
----- 1. Oop1: inherit -----
Engineer handles c
Customer Service handles a
Manager handles b
Customer Service handles a
Manager handles b
Customer Service handles w
Manager handles z
----- 2. Oop2: aggregation -----
Engineer handles c
Customer Service handles a
Manager handles b
Customer Service handles a
Manager handles b
Customer Service handles w
Manager handles z
----- 3. MixOopFp -----
Engineer handles c
Customer Service handles a
Manager handles b
Customer Service handles a
Manager handles b
Customer Service handles w
Manager handles z
----- 4. Fp1: partial apply function -----
Engineer handles c
Customer Service handles a
Manager handles b
Customer Service handles a
Manager handles b
Customer Service handles w
Manager handles z
----- 5. Fp2: partial function -----
Engineer handles c
Customer Service handles a
Manager handles b
Customer Service handles a
Manager handles b
Customer Service handles w
Manager handles z
----- 6. Rp1: akka actor Reactive Programming -----
Customer Service handles w
Customer Service handles a
Manager handles b
Customer Service handles a
Engineer handles c
Manager handles b
Manager handles z
----- 6. Rp1: Now all output ready, print by sequence -----
Engineer handles c
Customer Service handles a
Manager handles b
Customer Service handles a
Manager handles b
Customer Service handles w
Manager handles z
----- 7. Rp2: RX Reactive Programming (single thread)-----
Engineer handles c
Customer Service handles a
Manager handles b
Customer Service handles a
Manager handles b
Customer Service handles w
Manager handles z
----- 7. Rp2: RX Reactive Programming (multiple thread)-----
Customer Service handles a
Engineer handles c
Manager handles b
Manager handles b
Customer Service handles w
Customer Service handles a
Manager handles z
 */

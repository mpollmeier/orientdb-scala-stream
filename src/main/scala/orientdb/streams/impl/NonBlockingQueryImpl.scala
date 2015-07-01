package orientdb.streams.impl

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.stream.actor.ActorPublisher
import com.orientechnologies.orient.core.command._
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.sql.OCommandExecutorSQLDelegate
import org.reactivestreams.Publisher
import orientdb.streams.NonBlockingQuery
import orientdb.streams.impl.ActorSource.{ErrorOccurred, Complete, Enqueue}
import orientdb.streams.wrappers.SmartOSQLNonBlockingQuery

import scala.concurrent.Future
import scala.reflect.ClassTag

private[streams] class NonBlockingQueryImpl[A: ClassTag](query: String,
    limit: Int,
    fetchPlan: String,
    arguments: scala.collection.immutable.Map[Object, Object])(implicit system: ActorSystem) extends NonBlockingQuery[A] {

  OCommandManager.instance().registerExecutor(classOf[SmartOSQLNonBlockingQuery[_]], classOf[OCommandExecutorSQLDelegate])

  import collection.JavaConverters._
  def execute(args: Any*)(implicit db: ODatabaseDocumentTx): Publisher[A] = {
    val actorRef = system.actorOf(Props(new ActorSource[A]))
    val listener = createListener(actorRef)

    val oQuery =
      new SmartOSQLNonBlockingQuery[A](query, limit, fetchPlan, arguments.asJava, listener)

    import scala.concurrent.ExecutionContext.Implicits.global
    val future: Future[Unit] = db.command(oQuery).execute(args)
    future.onFailure { case t: Throwable ⇒ actorRef ! ErrorOccurred(t) }

    ActorPublisher[A](actorRef)
  }

  private def createListener(ref: ActorRef) = new OCommandResultListener {
    override def result(iRecord: Any): Boolean = {
      ref ! Enqueue(iRecord)
      true // todo we always request all
    }

    override def end(): Unit = {
      ref ! Complete
    }
  }
}

private[streams] abstract class NonBlockingQueryImpl2[A]()(implicit system: ActorSystem) extends NonBlockingQuery[A] {
  // ask from db only what you need
  /*
  override def execute(args: Object*)(implicit db: ODatabaseDocumentTx): Publisher[A] = {
    val actorRef = system.actorOf(Props(new ActorSource[Object]))
    val listener = createListener(actorRef)
    val oQuery =
      new OSQLNonBlockingQuery[Object](query, limit, fetchPlan, arguments.asJava, listener)

    db.command(oQuery).execute(args)
    ActorPublisher[A](actorRef)
  }*/

}


package se.marcuslonnberg.stark.auth.storage

import akka.actor.ActorSystem
import akka.util.ByteString
import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import org.json4s.native.Serialization._
import redis.{ByteStringFormatter, RedisClient}
import se.marcuslonnberg.stark.auth.AuthActor.AuthInfo

import scala.concurrent.Future

trait RedisAuthStore extends RedisConnection with RedisAuthHeadersStore with RedisAuthSessionsStore

trait RedisConnection {
  implicit def system: ActorSystem

  private val config = ConfigFactory.load().getConfig("redis")
  val host = config.as[String]("host")
  val port = config.as[Int]("port")
  val password = config.as[Option[String]]("password")
  val db = config.as[Option[Int]]("db")

  val client = RedisClient(host, port, password, db)
}

trait RedisAuthSessionsStore {
  def client: RedisClient

  implicit val authInfoFormatter = new ByteStringFormatter[AuthInfo] {

    import se.marcuslonnberg.stark.JsonProtocol._

    def serialize(data: AuthInfo) = ByteString(write(data))

    def deserialize(bs: ByteString) = read[AuthInfo](bs.utf8String)
  }

  def saveSession(id: String, auth: AuthInfo) = {
    val key = sessionKey(id)
    val expirationInSeconds = auth.expires.map(date => (date - DateTime.now.millis).millis / 100)
    client.set(key, auth, exSeconds = expirationInSeconds, NX = true)
  }

  def getSession(id: String): Future[Option[AuthInfo]] = {
    val key = sessionKey(id)
    client.get(key)
  }

  def sessionKey(id: String): String = "auth:sessions:" + id
}

trait RedisAuthHeadersStore {
  def client: RedisClient

  val headersKey = "auth:headers"

  def saveHeader(id: String) = {
    client.sadd(headersKey, id)
  }

  def removeHeader(id: String) = {
    client.srem(headersKey, id)
  }

  def authorizedHeader(id: String) = {
    client.sismember(headersKey, id)
  }

}

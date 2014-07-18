package se.marcuslonnberg.stark.auth.storage

import akka.util.ByteString
import com.github.nscala_time.time.Imports._
import org.json4s.native.Serialization._
import redis.{ByteStringFormatter, RedisClient}
import se.marcuslonnberg.stark.JsonSupport
import se.marcuslonnberg.stark.auth.AuthActor.AuthInfo
import se.marcuslonnberg.stark.storage.RedisConnection

import scala.concurrent.Future

trait RedisAuthStore extends RedisConnection with RedisAuthHeadersStore with RedisAuthSessionsStore

/**
 * Sessions are stored as "auth:sessions:id" where "id" is the id of the session.
 */
trait RedisAuthSessionsStore {
  def client: RedisClient

  private implicit def executionContext = client.executionContext

  implicit val authInfoFormatter = new ByteStringFormatter[AuthInfo] with JsonSupport {
    def serialize(data: AuthInfo) = ByteString(write(data))

    def deserialize(bs: ByteString) = read[AuthInfo](bs.utf8String)
  }

  def saveSession(id: String, auth: AuthInfo) = {
    val key = sessionKey(id)
    val expirationInSeconds = auth.expires.map(date => (date.getMillis - DateTime.now.getMillis) / 1000)
    client.set(key, auth, exSeconds = expirationInSeconds, NX = true)
  }

  def getSession(id: String): Future[Option[AuthInfo]] = {
    val key = sessionKey(id)
    client.get(key)
  }

  def sessionKeys: Future[Seq[String]] = client.keys(sessionKey("*"))

  def sessionIds: Future[Seq[String]] = sessionKeys.map(_.map(sessionKeyToId))

  private def sessionKeyToId(key: String): String = key.substring(sessionKeyPrefix.length)

  def getSessions: Future[Map[String, Option[AuthInfo]]] = {
    for {
      keys <- sessionKeys
      sessions <- client.mget(keys: _*)
    } yield keys.map(sessionKeyToId).zip(sessions).toMap
  }

  val sessionKeyPrefix = "auth:session:"

  def sessionKey(id: String): String = sessionKeyPrefix + id
}

/**
 * Headers are stored at "auth:headers".
 */
trait RedisAuthHeadersStore {
  def client: RedisClient

  val headersKey = "auth:headers"

  def saveHeader(id: String) = {
    client.sadd(headersKey, id)
  }

  def removeHeader(id: String) = {
    client.srem(headersKey, id)
  }

  def getHeaders: Future[Seq[String]] = client.smembers[String](headersKey)

  def containsHeader(id: String) = {
    client.sismember(headersKey, id)
  }
}

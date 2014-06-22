package se.marcuslonnberg.stark.storage

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import redis.RedisClient
import net.ceedubs.ficus.Ficus._

trait RedisConnection {
  implicit def system: ActorSystem

  private val config = ConfigFactory.load().getConfig("redis")
  val host = config.as[String]("host")
  val port = config.as[Int]("port")
  val password = config.as[Option[String]]("password")
  val db = config.as[Option[Int]]("db")

  val client = RedisClient(host, port, password, db)
}


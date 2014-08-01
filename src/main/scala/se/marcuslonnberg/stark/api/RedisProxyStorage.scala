package se.marcuslonnberg.stark.api

import akka.util.ByteString
import org.json4s.native.Serialization._
import redis.ByteStringFormatter
import se.marcuslonnberg.stark.JsonSupport
import se.marcuslonnberg.stark.site.{Location, ProxyConf}
import se.marcuslonnberg.stark.storage.RedisConnection

trait RedisProxyStorage extends RedisConnection {
  implicit def executionContext = client.executionContext

  val proxyKeyPrefix = "proxy:"

  implicit val authInfoFormatter = new ByteStringFormatter[ProxyConf] with JsonSupport {
    def serialize(data: ProxyConf) = ByteString(write(data))

    def deserialize(bs: ByteString) = read[ProxyConf](bs.utf8String)
  }

  def proxyKey(location: Location): String = proxyKeyPrefix + location.toString

  def getProxyKeys = client.keys(proxyKeyPrefix + "*")

  def getProxies = getProxyKeys.flatMap(client.mget[ProxyConf])

  def getProxy(location: Location) = client.get[ProxyConf](proxyKey(location))

  def removeProxy(location: Location) = client.del(proxyKey(location))

  def addProxy(proxy: ProxyConf) = client.set(proxyKey(proxy.location), proxy, NX = true)
}

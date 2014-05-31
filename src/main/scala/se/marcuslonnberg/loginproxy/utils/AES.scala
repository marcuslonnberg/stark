package se.marcuslonnberg.loginproxy.utils

import javax.crypto.{KeyGenerator, Cipher}
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base64

object AES {
  def generateKey(keySize: Int): Array[Byte] = {
    val keyGen = KeyGenerator.getInstance("AES")
    keyGen.init(keySize)
    keyGen.generateKey().getEncoded
  }

  def encrypt(plainText: String, encryptionKey: Array[Byte]): String = {
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    val key = new SecretKeySpec(encryptionKey, "AES")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val out = cipher.doFinal(plainText.getBytes("UTF-8"))
    new Base64().encodeAsString(out)
  }

  def decrypt(cipherText: String, encryptionKey: Array[Byte]): String = {
    val cipherData = new Base64().decode(cipherText)
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    val key = new SecretKeySpec(encryptionKey, "AES")
    cipher.init(Cipher.DECRYPT_MODE, key)
    new String(cipher.doFinal(cipherData), "UTF-8")
  }
}

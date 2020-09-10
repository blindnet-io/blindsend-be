package io.blindsend.app

import java.security.MessageDigest
import java.security.SecureRandom

import cats.effect.IO
import com.google.api.client.util.Base64

object Crypto {

  val jceProvider = "BC"

  // for hashes
  val rng = SecureRandom.getInstance("DEFAULT", jceProvider)

  def generateHash(rng: SecureRandom): IO[String] = IO {
    val bytes = Array.fill[Byte](8)(0)
    rng.nextBytes(bytes)
    val digest = MessageDigest.getInstance("SHA-256", jceProvider)
    digest.update(bytes)
    Base64.encodeBase64URLSafeString(digest.digest())
  }
}

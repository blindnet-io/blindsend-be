package io.blindsend.app

import java.security.MessageDigest
import java.security.SecureRandom

import cats.effect.IO
import com.google.api.client.util.Base64

object Crypto {

  val jceProvider = "BC"

  // for hashes
  val rng = SecureRandom.getInstance("DEFAULT", jceProvider)

  // TODO: length parameter
  def randomHash(rng: SecureRandom): IO[String] = IO {
    val bytes = Array.fill[Byte](20)(0)
    rng.nextBytes(bytes)
    val digest = MessageDigest.getInstance("SHA-1", jceProvider)
    digest.update(bytes)
    Base64.encodeBase64URLSafeString(digest.digest())
  }
}

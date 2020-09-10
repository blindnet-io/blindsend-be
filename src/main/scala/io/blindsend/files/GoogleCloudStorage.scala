package io.blindsend.files

import java.nio.file.Paths
import java.security._
import java.security.spec.PKCS8EncodedKeySpec

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._
import com.google.api.client.util.Base64
import fs2.Stream
import fs2.text
import io.blindsend.config.{ GoogleCloudStorage => GoogleCloudStorageConf }
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.implicits._

case class ServiceAccountCredentials(
  secretKey: String,
  clientId: String,
  clientEmail: String
)
object ServiceAccountCredentials {
  implicit val enc: Decoder[ServiceAccountCredentials] =
    Decoder.forProduct3("private_key", "client_id", "client_email")(ServiceAccountCredentials.apply)
}

case class JWTClaimSet(
  clientEmail: String,
  scope: String,
  assertionTarget: String,
  expirationTime: Long,
  issuedTime: Long
)
object JWTClaimSet {
  implicit val enc: Encoder[JWTClaimSet] =
    Encoder.forProduct5("iss", "scope", "aud", "exp", "iat")(r =>
      (r.clientEmail, r.scope, r.assertionTarget, r.expirationTime, r.issuedTime)
    )
}

case class GetTokenResp(accessToken: String)
object GetTokenResp {
  implicit val enc: Decoder[GetTokenResp] =
    Decoder.forProduct1("access_token")(GetTokenResp.apply)
  implicit val ed = jsonOf[IO, GetTokenResp]
}

object GoogleCloudStorage {

  val logger: org.log4s.Logger = org.log4s.getLogger

  def toBase64Url(s: String) = Base64.encodeBase64URLSafeString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))

  def getUri(uri: String) =
    IO.fromEither(Uri.fromString(uri).leftMap(e => new Throwable(s"Wrong uri: ${e.details}")))

  def apply(
    httpClient: Client[IO],
    cpuTP: ExecutionContext,
    blocker: Blocker,
    conf: GoogleCloudStorageConf
  )(implicit cs: ContextShift[IO], timer: Timer[IO]): IO[FileStorage] = {

    def getSignature(credentials: ServiceAccountCredentials, jwtHeader: String, jwtClaimSet: String) =
      cs.evalOn(cpuTP)(IO {
        val spec = new PKCS8EncodedKeySpec(
          Base64.decodeBase64(
            credentials.secretKey
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replace("\n", "")
          )
        )
        val kf         = KeyFactory.getInstance("RSA")
        val privateKey = kf.generatePrivate(spec)

        val signatureHandler = Signature.getInstance("SHA256withRSA", "BC")
        signatureHandler.initSign(privateKey)
        signatureHandler.update(s"$jwtHeader.$jwtClaimSet".getBytes)
        signatureHandler.sign()
      })

    val jwtHeaderEncoded = toBase64Url("""{"alg":"RS256","typ":"JWT"}""")
    val scope =
      "https://www.googleapis.com/auth/devstorage.read_write https://www.googleapis.com/auth/cloud-platform.read-only https://www.googleapis.com/auth/devstorage.full_control https://www.googleapis.com/auth/devstorage.read_only https://www.googleapis.com/auth/cloud-platform"

    val getAccessToken =
      for {
        _ <- IO(logger.info("Obtaining GCS access token"))

        accountDataStr <- fs2.io.file
                           .readAll[IO](Paths.get(conf.accountFilePath), blocker, 1024)
                           .through(text.utf8Decode)
                           .compile
                           .foldMonoid

        credentials <- IO.fromEither(decode[ServiceAccountCredentials](accountDataStr).leftMap(e => new Throwable(e)))
        curTime     <- IO(System.currentTimeMillis() / 1000)

        // TODO: use a dedicated library for JWT
        jwtClaimSet = JWTClaimSet(
          credentials.clientEmail,
          scope,
          "https://oauth2.googleapis.com/token",
          curTime + 3600,
          curTime
        )
        jwtClaimSetEncoded = toBase64Url(jwtClaimSet.asJson.noSpaces)

        signature <- getSignature(credentials, jwtHeaderEncoded, jwtClaimSetEncoded)
        jwt       = s"$jwtHeaderEncoded.$jwtClaimSetEncoded.${Base64.encodeBase64URLSafeString(signature)}"

        req = Request[IO](
          method = Method.POST,
          uri = uri"https://oauth2.googleapis.com/token"
        ).withEntity(UrlForm("grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion" -> jwt))

        resp <- httpClient.expect[GetTokenResp](req)

        _ <- IO(logger.info("Fresh GCS access token obtained"))
      } yield resp

    def refreshTokenLoop(accessTokenRef: Ref[IO, String]): IO[Unit] =
      for {
        _ <- IO.sleep(conf.tokenRefreshRate seconds)
        _ <- getAccessToken
              .flatMap(resp => accessTokenRef.set(resp.accessToken))
              .timeout(1 minute)
              .handleErrorWith(e => IO(logger.error(s"Failed obtaining GCS access token - ${e.toString}")))
        _ <- refreshTokenLoop(accessTokenRef)
      } yield ()

    for {
      resp           <- getAccessToken
      accessTokenRef <- Ref.of[IO, String](resp.accessToken)

      _ <- refreshTokenLoop(accessTokenRef).start
    } yield (new FileStorage {

      def saveFile(id: String, file: Stream[IO, Byte]): IO[Unit] =
        for {
          _ <- IO(logger.info(s"Sending file ${id} to GCS"))

          accessToken <- accessTokenRef.get
          uri         <- getUri(s"https://storage.googleapis.com/upload/storage/v1/b/${conf.bucketName}/o?uploadType=media&name=${id}")

          req = Request[IO](
            method = Method.POST,
            uri = uri,
            headers = Headers.of(
              Header(
                "Authorization",
                s"Bearer $accessToken"
              )
            ),
            body = file
          )
          status <- httpClient.status(req)
          _      <- if (status == Status.Ok) IO.unit else IO.raiseError(new Throwable(s"Error while uploading file: Status ${status.code}"))
        } yield ()

      def getFile(id: String): IO[Stream[IO, Byte]] =
        for {
          _ <- IO(logger.info(s"Fetching file ${id} from GCS"))

          accessToken <- accessTokenRef.get
          uri         <- getUri(s"https://storage.googleapis.com/download/storage/v1/b/${conf.bucketName}/o/$id?alt=media")

          req = Request[IO](
            uri = uri,
            headers = Headers.of(
              Header(
                "Authorization",
                s"Bearer $accessToken"
              )
            )
          )
          // https://github.com/http4s/http4s/issues/2528
          // https://gitter.im/http4s/http4s/archives/2016/03/03
          // watch out for body must be run, for connection to be released
          resp <- httpClient.toHttpApp(req)
          newResp <- if (resp.status == Status.Ok) IO(resp.body)
                    else
                      EntityDecoder
                        .decodeText(resp)
                        .flatMap(body => IO.raiseError(new Throwable(s"Error while getting file. Status: ${resp.status}; body: $body")))
        } yield newResp

      def deleteFile(id: String): IO[Unit] =
        for {
          _ <- IO(logger.info(s"Deleting file ${id} from GCS"))

          accessToken <- accessTokenRef.get
          uri         <- getUri(s"https://storage.googleapis.com/storage/v1/b/${conf.bucketName}/o/${id}")

          req = Request[IO](
            method = Method.DELETE,
            uri = uri,
            headers = Headers.of(
              Header(
                "Authorization",
                s"Bearer $accessToken"
              )
            )
          )
          status <- httpClient.status(req)
          _      <- if (status == Status.Ok) IO.unit else IO.raiseError(new Throwable(s"Error while deleting file: status ${status.code}"))
        } yield ()
    })
  }
}

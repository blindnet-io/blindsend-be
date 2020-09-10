package io.blindsend.app

import java.security._
import java.time.LocalDateTime

import scala.concurrent.duration._

import cats.effect.IO
import cats.effect._
import cats.effect.concurrent._
import fs2.Stream
import io.blindsend.config.Config
import io.blindsend.files._
import io.blindsend.links._
import io.blindsend.model._
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.bouncycastle.util.{ Arrays => BCArrays }
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._

object Api {

  val logger: org.log4s.Logger = org.log4s.getLogger

  implicit val gsed  = jsonOf[IO, ReqGetStatus]
  implicit val bhed  = jsonOf[IO, ReqBeginHandshake]
  implicit val ched  = jsonOf[IO, ReqContinueHandshake]
  implicit val fhed  = jsonOf[IO, ReqFinishHandshake]
  implicit val gfed  = jsonOf[IO, ReqGetFileRequest]
  implicit val gked  = jsonOf[IO, ReqGetKeys]
  implicit val gfmed = jsonOf[IO, ReqGetFileMetadata]

  def saveFile(
    linkRepo: LinkRepository,
    fileStorage: FileStorage,
    body: Stream[IO, Byte],
    linkId: String,
    rng: SecureRandom,
    conf: Config
  )(implicit cs: ContextShift[IO], t: Timer[IO]) =
    for {
      fileId <- Crypto.generateHash(rng)
      _      <- linkRepo.storeFileUploading(linkId, fileId)

      _ <- IO(logger.info(s"Generated file id ${fileId}"))

      maxFileSizeReached <- Deferred.tryable[IO, Unit]

      fileStream = body
        .zipLeft(
          fs2.Stream.constant[IO, Unit]((), 10000).take(conf.maxFileSize) ++
            Stream.eval(maxFileSizeReached.complete(()))
        )
        .interruptWhen(maxFileSizeReached.get.attempt)

      _ <- fileStorage.saveFile(fileId, fileStream)

      response <- maxFileSizeReached.tryGet.flatMap {
                   case None =>
                     for {
                       _ <- IO(logger.info(s"File ${fileId} for link ${linkId} uploaded successfully"))
                       _ <- linkRepo.storeFileUploadingSuccess(linkId, fileId)
                       r <- Ok()
                     } yield r

                   case _ =>
                     for {
                       _ <- linkRepo.storeFileUploadingFailed(linkId, fileId)
                       _ <- fileStorage.deleteFile(fileId).delayBy(5 seconds).start.attempt
                       _ <- IO(logger.error(s"File size ${fileId} for link ${linkId} larger than maximum allowed, Content-Length lying"))
                       r <- BadRequest("File too large")
                     } yield r
                 }

    } yield response

  def service(
    linkRepo: LinkRepository,
    fileStorage: FileStorage,
    filesBeingUploaded: Ref[IO, Set[String]],
    rng: SecureRandom,
    conf: Config
  )(implicit cs: ContextShift[IO], t: Timer[IO]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case GET -> Root / "get-upload-params" =>
        Ok(RespUploadParams(conf.maxFileSize).asJson)

      case req @ POST -> Root / "get-status" =>
        for {
          ReqGetStatus(linkId) <- req.as[ReqGetStatus]

          _        <- IO(logger.info(s"Received get-status request for ${linkId}"))
          status   <- linkRepo.getStatus(linkId)
          response <- if (status == 0) BadRequest("Wrong link id") else Ok(RespGetStatus(linkId, status).asJson)
        } yield response

      case GET -> Root / "get-link" =>
        for {
          _ <- IO(logger.info("Received get-link request"))

          linkId       <- Crypto.generateHash(rng)
          dateTime     <- IO(LocalDateTime.now())
          _            <- linkRepo.storeLinkGenerated(linkId, dateTime)
          _            <- IO(logger.info(s"Created link id ${linkId}"))
          responseData = RespLinkId(linkId)
          response     <- Ok(responseData.asJson)
        } yield response

      case req @ POST -> Root / "begin-hs" =>
        for {
          bh <- req.as[ReqBeginHandshake]

          _ <- IO(logger.info(s"Received begin-hs request for ${bh.linkId}"))

          response <- linkRepo.getLinkState(bh.linkId).flatMap {
                       case Some(_: LinkCreated) =>
                         linkRepo
                           .storeReceivedRequesterKeys(
                             bh.linkId,
                             bh.pk1,
                             bh.sk1EncNonce,
                             bh.sk1Encrypted,
                             bh.sk1EkKdfSalt,
                             bh.sk1EkKdfOps,
                             bh.sk1EkKdfMemLimit,
                             bh.sk1EkeyHash
                           ) *>
                           Ok(RespBeginHandshake(s"${conf.domain}/${bh.linkId}").asJson)
                       case _ =>
                         IO(logger.warn(s"Bad state for ${bh.linkId}")) *> BadRequest("Bad link state")
                     }

        } yield response

      case req @ POST -> Root / "cont-hs" =>
        for {
          ReqContinueHandshake(linkId) <- req.as[ReqContinueHandshake]

          _ <- IO(logger.info(s"Received cont-hs request for ${linkId}"))

          response <- linkRepo.getLinkState(linkId).flatMap {
                       case Some(data: UploadFileMandatoryData) =>
                         for {
                           uploadId <- Crypto.generateHash(rng)
                           _        <- linkRepo.storeAwaitingFileUpload(linkId, uploadId)

                           response <- Ok(RespContinueHandshake(linkId, data.pk1, uploadId).asJson)
                         } yield response
                       case _ => IO(logger.warn(s"Bad state for ${linkId}")) *> BadRequest("Bad link State")
                     }

        } yield response

      case req @ POST -> Root / "send-file" / linkId / uploadId =>
        for {
          _ <- IO(
                logger.info(
                  s"Received send-file request for ${linkId}, upload ${uploadId}," +
                    s"Content-Length: ${req.contentLength.map(_.toString).getOrElse("unknown")}"
                )
              )

          allowed <- filesBeingUploaded.modify { s =>
                      if (s.contains(linkId)) (s, false)
                      else (s + linkId, true)
                    }

          response <- if (allowed)
                       (IO.sleep(5 seconds) *> filesBeingUploaded.update(_ - linkId)).start *>
                         linkRepo.getLinkState(linkId).flatMap {
                           case Some(data: AwaitingFileUpload) if data.uploadId == uploadId =>
                             if (req.contentLength.exists(_ > conf.maxFileSize))
                               IO(
                                 logger.info(
                                   s"File content length ${req.contentLength.get} larger than maximum allowed" +
                                     s"size ${conf.maxFileSize} for link ${linkId}. Aborting file upload."
                                 )
                               ) *> BadRequest("File size too large")
                             else
                               saveFile(linkRepo, fileStorage, req.body, linkId, rng, conf).handleErrorWith { e =>
                                 IO(logger.error(s"Error occured while uploading file, link ${linkId}: ${e.toString()}")) *>
                                   InternalServerError("Error while uploading file")
                               }

                           case _ => IO(logger.warn(s"Bad state for ${linkId}")) *> BadRequest("Bad link state")
                         }
                     else IO(logger.warn(s"Already processing link id ${linkId}")) *> BadRequest("Already processing the upload")

        } yield response

      case req @ POST -> Root / "finish-hs" =>
        for {
          ReqFinishHandshake(linkId, pk2, keyHash, streamEncHeader, fileName, fileSize) <- req.as[ReqFinishHandshake]

          _ <- IO(logger.info(s"Received finish-hs request for ${linkId}"))

          response <- linkRepo.getLinkState(linkId).flatMap {
                       case Some(_: FileUploaded) =>
                         linkRepo.storeAwaitingDownload(linkId, pk2, keyHash, streamEncHeader, fileName, fileSize) *>
                           Ok(Json.fromString("{}"))
                       case _ =>
                         IO(logger.warn(s"Bad state for ${linkId}")) *> BadRequest("Bad link state")
                     }

        } yield response

      case req @ POST -> Root / "get-file-metadata" =>
        for {
          ReqGetFileMetadata(linkId) <- req.as[ReqGetFileMetadata]

          _ <- IO(logger.info(s"Received get-file-metadata request for ${linkId}"))

          data <- linkRepo.getLinkState(linkId)

          response <- data match {
                       case Some(data: AwaitingDownload) =>
                         Ok(RespGetFileMetadata(linkId, data.fileName, data.fileSize).asJson)

                       case _ => IO(logger.warn(s"Bad state for ${linkId}")) *> BadRequest("Bad link state")
                     }

        } yield response

      case req @ POST -> Root / "get-keys" =>
        for {
          ReqGetKeys(linkId) <- req.as[ReqGetKeys]

          _ <- IO(logger.info(s"Received get-keys request for ${linkId}"))

          data <- linkRepo.getLinkState(linkId)

          response <- data match {
                       case Some(data: AwaitingDownload) =>
                         Ok(
                           RespGetKeys(
                             linkId,
                             data.pk1,
                             data.sk1EncNonce,
                             data.sk1Encrypted,
                             data.sk1EkKdfSalt,
                             data.sk1EkKdfOps,
                             data.sk1EkKdfMemLimit,
                             data.pk2,
                             data.streamEncHeader
                           ).asJson
                         )

                       case _ => IO(logger.warn(s"Bad state for ${linkId}")) *> BadRequest("Bad link state")
                     }

        } yield response

      case req @ POST -> Root / "get-file" =>
        for {
          ReqGetFileRequest(linkId, sk1EkeyHash) <- req.as[ReqGetFileRequest]

          _ <- IO(logger.info(s"Received get-file request for ${linkId}"))

          data <- linkRepo.getLinkState(linkId)

          response <- data match {
                       case Some(data: AwaitingDownload)
                           if BCArrays.constantTimeAreEqual(data.sk1EkeyHash.getBytes, sk1EkeyHash.getBytes) =>
                         fileStorage
                           .getFile(data.fileId)
                           .flatMap { file =>
                             val r = file.onFinalizeCase {
                               // TODO: delete file on success or something
                               case ExitCase.Completed =>
                                 IO(logger.info(s"File ${data.fileId} for link ${linkId} transferred successfully."))
                               case _ => IO(logger.error(s"Error while transferring the file ${data.fileId} for link ${linkId}."))
                             }
                             Ok(r)
                           }
                           .handleErrorWith(e =>
                             IO(logger.error(s"Error while fetching file ${data.fileId} for link ${linkId}: ${e}")) *>
                               InternalServerError("Error getting file")
                           )

                       case Some(_: AwaitingDownload) =>
                         IO(logger.info(s"Wrong password provided for ${linkId}")) *> BadRequest("Wrong password")

                       case _ => IO(logger.warn(s"Bad state for ${linkId}")) *> BadRequest("Bad link state")
                     }

        } yield response
    }
}

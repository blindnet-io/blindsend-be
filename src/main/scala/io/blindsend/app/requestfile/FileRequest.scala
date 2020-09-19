package io.blindsend.app.requestfile

import java.security._
import java.time.LocalDateTime

import scala.concurrent.duration._

import cats.effect.IO
import cats.effect._
import cats.effect.concurrent._
import fs2.Stream
import io.blindsend.config.Config
import io.blindsend.files._
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import io.blindsend.app.Crypto
import io.blindsend.app.requestfile.model._
import io.blindsend.app.requestfile.repo._

object FileRequest {

  val logger: org.log4s.Logger = org.log4s.getLogger

  implicit val gsed  = jsonOf[IO, ReqGetStatus]
  implicit val bhed  = jsonOf[IO, ReqInitSession]
  implicit val ched  = jsonOf[IO, ReqPrepareUpload]
  implicit val fhed  = jsonOf[IO, ReqFinishUpload]
  implicit val gfed  = jsonOf[IO, ReqGetFile]
  implicit val gked  = jsonOf[IO, ReqGetKeys]
  implicit val gfmed = jsonOf[IO, ReqGetFileMetadata]
  implicit val isfed = jsonOf[IO, ReqInitUploadFile]

  def saveFile(
    linkRepo: LinkRepository,
    fileStorage: FileStorage,
    body: Stream[IO, Byte],
    linkId: String,
    rng: SecureRandom,
    conf: Config
  )(implicit cs: ContextShift[IO], t: Timer[IO]) =
    for {
      fileId <- Crypto.randomHash(rng)
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
                       _ <- linkRepo.storeFileUploadingSuccess(linkId)
                       r <- Ok()
                     } yield r

                   case _ =>
                     for {
                       _ <- linkRepo.storeFileUploadingFailed(linkId)
                       _ <- fileStorage.deleteFile(fileId).delayBy(5 seconds).start.attempt
                       _ <- IO(logger.error(s"File size ${fileId} for link ${linkId} larger than maximum allowed, Content-Length lying"))
                       r <- BadRequest("File too large")
                     } yield r
                 }

    } yield response

  object PartId    extends QueryParamDecoderMatcher[Int]("part_id")
  object ChunkSize extends QueryParamDecoderMatcher[Long]("chunk_size")
  object Last      extends QueryParamDecoderMatcher[Boolean]("last")

  case class PartialUploadState(fileId: String, uploadId: String, totalSize: Long, uploadedSize: Long, curPart: Int)

  def service(
    linkRepo: LinkRepository,
    fileStorage: FileStorage,
    rng: SecureRandom,
    conf: Config
  )(implicit cs: ContextShift[IO], t: Timer[IO]): IO[HttpRoutes[IO]] =
    for {
      filesSetToUpload <- Ref.of[IO, Set[String]](Set.empty)
      filesUploadState <- Ref.of[IO, Map[String, PartialUploadState]](Map.empty)
    } yield HttpRoutes.of[IO] {

      case GET -> Root / "get-upload-params" =>
        Ok(RespUploadParams(conf.maxFileSize).asJson)

      case req @ POST -> Root / "get-status" =>
        for {
          ReqGetStatus(linkId) <- req.as[ReqGetStatus]

          _        <- IO(logger.info(s"Received get-status request for ${linkId}"))
          status   <- linkRepo.getStatus(linkId)
          response <- if (status == 0) BadRequest("Wrong link id") else Ok(RespGetStatus(linkId, status).asJson)
        } yield response

      case GET -> Root / "init-link-id" =>
        for {
          _ <- IO(logger.info("Received get-link request"))

          linkId       <- Crypto.randomHash(rng)
          dateTime     <- IO(LocalDateTime.now())
          _            <- linkRepo.storeLinkGenerated(linkId, dateTime)
          _            <- IO(logger.info(s"Created link id ${linkId}"))
          responseData = RespInitLinkId(linkId)
          response     <- Ok(responseData.asJson)
        } yield response

      case req @ POST -> Root / "init-session" =>
        for {
          ReqInitSession(linkId, kdfSalt, kdfOps, kdfMemLimit) <- req.as[ReqInitSession]

          _ <- IO(logger.info(s"Received init-session request for ${linkId}"))

          response <- linkRepo.getLinkState(linkId).flatMap {
                       case Some(_: LinkCreated) =>
                         linkRepo
                           .storeReceivedRequesterKeys(
                             linkId,
                             kdfSalt,
                             kdfOps,
                             kdfMemLimit
                           ) *>
                           Ok(RespInitSession(s"${conf.domain}/${linkId}").asJson)
                       case _ =>
                         IO(logger.warn(s"Bad state for ${linkId}")) *> BadRequest("Bad link state")
                     }

        } yield response

      case req @ POST -> Root / "prepare-upload" =>
        for {
          ReqPrepareUpload(linkId) <- req.as[ReqPrepareUpload]

          _ <- IO(logger.info(s"Received prepare-upload request for ${linkId}"))

          response <- linkRepo.getLinkState(linkId).flatMap {
                       case Some(_: SessionInitialized) | Some(_: FileUploading) =>
                         for {
                           _ <- filesSetToUpload.update(_ - linkId)
                           _ <- filesUploadState.update(_.removed(linkId))

                           uploadId <- Crypto.randomHash(rng)
                           _        <- linkRepo.storeAwaitingFileUpload(linkId, uploadId)

                           response <- Ok(RespPrepareUpload(linkId, uploadId).asJson)
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

          allowed <- filesSetToUpload.modify { s =>
                      if (s.contains(linkId)) (s, false)
                      else (s + linkId, true)
                    }

          response <- if (allowed)
                       (IO.sleep(5 seconds) *> filesSetToUpload.update(_ - linkId)).start *>
                         linkRepo.getLinkState(linkId).flatMap {
                           case Some(data: AwaitingFileUpload) if data.uploadId == uploadId =>
                             if (req.contentLength.exists(_ > conf.maxFileSize))
                               IO(
                                 logger.warn(
                                   s"File content length ${req.contentLength.get} larger than maximum allowed " +
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

      case req @ POST -> Root / "init-send-file" =>
        for {
          ReqInitUploadFile(linkId, uploadId, size) <- req.as[ReqInitUploadFile]

          _ <- IO(logger.info(s"Received init-send-file request for ${linkId}, upload ${uploadId}, file size ${size}"))

          allowed <- filesSetToUpload.modify { s =>
                      if (s.contains(linkId)) (s, false)
                      else (s + linkId, true)
                    }

          response <- if (allowed)
                       (IO.sleep(5 seconds) *> filesSetToUpload.update(_ - linkId)).start *>
                         linkRepo.getLinkState(linkId).flatMap {
                           case Some(data: AwaitingFileUpload) if data.uploadId == uploadId =>
                             if (size > conf.maxFileSize)
                               IO(
                                 logger.warn(
                                   s"File content length ${req.contentLength.get} larger than maximum allowed " +
                                     s"size ${conf.maxFileSize} for link ${linkId}. Aborting file upload."
                                 )
                               ) *> BadRequest("File size too large")
                             else {
                               val res = for {
                                 fileId <- Crypto.randomHash(rng)
                                 _      <- linkRepo.storeFileUploading(linkId, fileId)
                                 _      <- filesUploadState.update(_ + (linkId -> PartialUploadState(fileId, uploadId, size.toLong, 0L, 0)))
                                 _      <- (IO.sleep(30 minutes) *> filesUploadState.update(_.removed(linkId))).start
                                 _      <- fileStorage.initSaveFile(fileId)
                                 resp   <- Ok("{}")
                               } yield resp
                               res.handleErrorWith(e =>
                                 IO(logger.error(s"Error occured while initializing the file upload, link ${linkId}: ${e.toString}")) *>
                                   InternalServerError("Error on server")
                               )
                             }
                           case _ => IO(logger.warn(s"Bad state for ${linkId}")) *> BadRequest("Bad link state")
                         }
                     else IO(logger.warn(s"Already processing link id ${linkId}")) *> BadRequest("Already processing the upload")

        } yield response

      case req @ POST -> Root / "send-file-part" / linkId / uploadId :? PartId(partId) +& ChunkSize(chunkSize) +& Last(last) =>
        for {
          _ <- IO(logger.info(s"Received send-file-part request for ${linkId}, upload ${uploadId}, ${partId}, ${chunkSize}"))

          uploadData <- filesUploadState.get.map(_.get(linkId))

          response <- uploadData match {
                       case Some(PartialUploadState(fileId, upId, totalSize, uploadedSize, curPart))
                           if partId == curPart + 1 && upId == uploadId =>
                         if (req.contentLength.exists(len => len + uploadedSize > totalSize) || uploadedSize + chunkSize > totalSize)
                           IO(
                             logger.warn(
                               s"File content length larger than maximum allowed size ${conf.maxFileSize} for link ${linkId}. Aborting file upload."
                             )
                           ) *> BadRequest("File size too large")
                         else {
                           val program =
                             for {

                               saveRes <- fileStorage.saveFilePart(
                                           fileId,
                                           req.body,
                                           totalSize,
                                           chunkSize,
                                           uploadedSize,
                                           uploadedSize + chunkSize - 1
                                         )

                               response <- saveRes match {
                                            case PartialSaveRespType.Success =>
                                              for {
                                                _ <- filesUploadState.update(
                                                      _.updatedWith(linkId)(v =>
                                                        v.map(_.copy(uploadedSize = uploadedSize + chunkSize, curPart = partId))
                                                      )
                                                    )
                                                _ <- if (last)
                                                      for {
                                                        _ <- IO(logger.info(s"File ${fileId} for link ${linkId} uploaded successfully"))
                                                        _ <- linkRepo.storeFileUploadingSuccess(linkId)
                                                        _ <- filesUploadState.update(_.removed(linkId))
                                                      } yield ()
                                                    else
                                                      IO.unit
                                                response <- Ok("{}")
                                              } yield response

                                            case PartialSaveRespType.WrongParameters =>
                                              IO(logger.warn(s"Wrong upload parameters for $linkId")) *>
                                                BadRequest(s"Bad upload parameters")

                                            case PartialSaveRespType.UnexpectedError =>
                                              IO(logger.warn(s"Unexpected error for $linkId")) *>
                                                BadRequest(s"Unexpected error")
                                          }

                             } yield response

                           program.handleErrorWith(e =>
                             IO(logger.error(s"Error occured while uploading the file part ${partId}, link ${linkId}: ${e.toString}")) *>
                               InternalServerError("Error on server")
                           )
                         }

                       case Some(PartialUploadState(_, upId, _, _, curPart)) =>
                         IO(logger.warn(s"Wrong part id and upload id (${partId}, ${uploadId}), expected (${curPart + 1}, ${upId})")) *>
                           BadRequest(s"Wrong parameters")

                       case None =>
                         IO(logger.warn(s"Bad state for ${linkId}")) *>
                           BadRequest("Bad link state")
                     }

        } yield response

      case req @ POST -> Root / "finish-upload" =>
        for {
          ReqFinishUpload(linkId, pk2, streamEncHeader, fileName, fileSize) <- req.as[ReqFinishUpload]

          _ <- IO(logger.info(s"Received finish-upload request for ${linkId}"))

          _ <- filesSetToUpload.update(_ - linkId)
          _ <- filesUploadState.update(_.removed(linkId))

          response <- linkRepo.getLinkState(linkId).flatMap {
                       case Some(_: FileUploaded) =>
                         linkRepo.storeAwaitingDownload(linkId, pk2, streamEncHeader, fileName, fileSize) *>
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
                             data.kdfSalt,
                             data.kdfOps,
                             data.kdfMemLimit,
                             data.pk2,
                             data.streamEncHeader
                           ).asJson
                         )

                       case _ => IO(logger.warn(s"Bad state for ${linkId}")) *> BadRequest("Bad link state")
                     }

        } yield response

      case req @ POST -> Root / "get-file" =>
        for {
          ReqGetFile(linkId) <- req.as[ReqGetFile]

          _ <- IO(logger.info(s"Received get-file request for ${linkId}"))

          data <- linkRepo.getLinkState(linkId)

          response <- data match {
                       case Some(data: AwaitingDownload) =>
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

                       case _ => IO(logger.warn(s"Bad state for ${linkId}")) *> BadRequest("Bad link state")
                     }

        } yield response
    }
}

package io.blindsend.app.sendfile

import java.security._
import java.time.LocalDateTime

import scala.concurrent.duration._

import cats.effect.IO
import cats.effect._
import cats.effect.concurrent._
import io.blindsend.config.Config
import io.blindsend.files._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import io.blindsend.app.Crypto
import io.blindsend.app.sendfile.model._
import io.blindsend.app.sendfile.repo._

object FileSend {

  val logger: org.log4s.Logger = org.log4s.getLogger

  implicit val iued  = jsonOf[IO, ReqInitUpload]
  implicit val gfmed = jsonOf[IO, ReqGetFileMetadata]
  implicit val gfed  = jsonOf[IO, ReqGetFile]

  object PartId    extends QueryParamDecoderMatcher[Int]("part_id")
  object ChunkSize extends QueryParamDecoderMatcher[Long]("chunk_size")
  object Last      extends QueryParamDecoderMatcher[Boolean]("last")

  case class PartialUploadState(fileId: String, totalSize: Long, uploadedSize: Long, curPart: Int)

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

      case req @ POST -> Root / "init-session" =>
        for {
          _ <- IO(logger.info("Received init-session request"))

          r <- req.as[ReqInitUpload]

          linkId   <- Crypto.randomHash(rng)
          dateTime <- IO(LocalDateTime.now())
          fileId   <- Crypto.randomHash(rng)

          _ <- IO(logger.info(s"Created link id ${linkId}, file id ${fileId}"))

          _ <- linkRepo.storeUploadInitialized(
                linkId,
                dateTime,
                fileId,
                r.size,
                r.encFileMetadata,
                r.fileEncNonce,
                r.metadataEncNonce,
                r.kdfSalt,
                r.kdfOps,
                r.kdfMemLimit
              )

          _ <- (IO.sleep(5 seconds) *> filesSetToUpload.update(_ - linkId)).start

          // TODO: this is encrypted size, transform to real size
          response <- if (r.size > conf.maxFileSize)
                       IO(
                         logger.warn(
                           s"File content length ${req.contentLength.get} larger than maximum allowed " +
                             s"size ${conf.maxFileSize} for link ${linkId}. Aborting file upload."
                         )
                       ) *> BadRequest("File size too large")
                     else {
                       val res = for {
                         _    <- filesUploadState.update(_ + (linkId -> PartialUploadState(fileId, r.size, 0L, 0)))
                         _    <- (IO.sleep(30 minutes) *> filesUploadState.update(_.removed(linkId))).start
                         _    <- fileStorage.initSaveFile(fileId)
                         resp <- Ok(RespInitUpload(linkId).asJson)
                       } yield resp

                       res.handleErrorWith(e =>
                         IO(logger.error(s"Error occured while initializing the file upload, link ${linkId}: ${e.toString}")) *>
                           InternalServerError("Error on server")
                       )
                     }

        } yield response

      // TODO: last wont work for parallel uploads, refactor
      case req @ POST -> Root / "send-file-part" / linkId :? PartId(partId) +& ChunkSize(chunkSize) +& Last(last) =>
        for {
          _ <- IO(logger.info(s"Received send-file-part request for ${linkId}, part ${partId}, size ${chunkSize}"))

          uploadData <- filesUploadState.get.map(_.get(linkId))

          response <- uploadData match {
                       case Some(PartialUploadState(fileId, totalSize, uploadedSize, curPart)) if partId == curPart + 1 =>
                         if (req.contentLength.exists(len => len + uploadedSize > totalSize) || uploadedSize + chunkSize > totalSize)
                           IO(
                             logger.warn(
                               s"File content length larger than advertised size ${totalSize} for link ${linkId}. Aborting file upload."
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
                                                response <- if (last)
                                                             for {
                                                               _ <- IO(
                                                                     logger.info(s"File ${fileId} for link ${linkId} uploaded successfully")
                                                                   )
                                                               _  <- linkRepo.storeFileUploadingSuccess(linkId)
                                                               _  <- filesSetToUpload.update(_ - linkId)
                                                               _  <- filesUploadState.update(_.removed(linkId))
                                                               ok <- Ok(s"${conf.domain}/send/${linkId}")
                                                             } yield ok
                                                           else
                                                             Ok("")
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

                       case None =>
                         IO(logger.warn(s"Bad state for ${linkId}")) *>
                           BadRequest("Bad link state")
                     }

        } yield response

      case req @ POST -> Root / "get-file-metadata" =>
        for {
          ReqGetFileMetadata(linkId) <- req.as[ReqGetFileMetadata]

          _ <- IO(logger.info(s"Received get-file-metadata request for ${linkId}"))

          data <- linkRepo.getLinkState(linkId)

          response <- data match {
                       case Some(data: FileUploaded) =>
                         Ok(
                           RespGetFileMetadata(
                             linkId,
                             data.size,
                             data.encFileMetadata,
                             data.fileEncNonce,
                             data.metadataEncNonce,
                             data.kdfSalt,
                             data.kdfOps,
                             data.kdfMemLimit
                           ).asJson
                         )

                       case Some(_) => IO(logger.warn(s"Bad state for ${linkId}")) *> BadRequest("Bad link state")
                       case None    => IO(logger.warn(s"Requesting non-existend link id ${linkId}")) *> NotFound("")
                     }

        } yield response

      case req @ POST -> Root / "get-file" =>
        for {
          ReqGetFile(linkId) <- req.as[ReqGetFile]

          _ <- IO(logger.info(s"Received get-file request for ${linkId}"))

          data <- linkRepo.getLinkState(linkId)

          response <- data match {
                       case Some(data: FileUploaded) =>
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

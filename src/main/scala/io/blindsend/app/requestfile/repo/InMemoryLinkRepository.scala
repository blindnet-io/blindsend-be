package io.blindsend.app.requestfile.repo

import java.time.LocalDateTime

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._
import io.blindsend.app.requestfile.model._

object InMemoryLinkRepository {

  def apply(state: Ref[IO, Map[String, LinkState]]) = new LinkRepository {

    def getStatus(linkId: String): IO[Int] =
      state.get.map(s =>
        s.get(linkId)
          .map {
            case _: LinkCreated        => 1
            case _: SessionInitialized => 2
            case _: AwaitingFileUpload => 3
            case _: FileUploading      => 4
            case _: FileUploaded       => 5
            case _: AwaitingDownload   => 6
          }
          .getOrElse(0)
      )

    def getLinkState(linkId: String): IO[Option[LinkState]] =
      state.get.map(s => s.get(linkId))

    def storeLinkGenerated(linkId: String, dateTime: LocalDateTime): IO[Unit] =
      state.update(_ + (linkId -> LinkCreated(linkId, dateTime)))

    def storeReceivedRequesterKeys(
      linkId: String,
      kdfSalt: String,
      kdfOps: Int,
      kdfMemLimit: Int
    ): IO[Unit] =
      state.update { s =>
        s.get(linkId)
          .map {
            case data: LinkCreated =>
              s.updated(
                linkId,
                SessionInitialized(
                  data.id,
                  data.dateTime,
                  kdfSalt,
                  kdfOps,
                  kdfMemLimit
                )
              )
          }
          .getOrElse(s)
      }.handleError(_ => ())

    def storeAwaitingFileUpload(
      linkId: String,
      uploadId: String
    ): IO[Unit] =
      state.update { s =>
        s.get(linkId)
          .map {
            case data: AwaitingFileUpload =>
              s.updated(
                linkId,
                data.copy(uploadId = uploadId)
              )
            case data: SessionInitialized =>
              s.updated(
                linkId,
                AwaitingFileUpload(
                  data.id,
                  data.dateTime,
                  data.kdfSalt,
                  data.kdfOps,
                  data.kdfMemLimit,
                  uploadId
                )
              )
            case data: FileUploading =>
              s.updated(
                linkId,
                AwaitingFileUpload(
                  data.id,
                  data.dateTime,
                  data.kdfSalt,
                  data.kdfOps,
                  data.kdfMemLimit,
                  uploadId
                )
              )
            case data: FileUploaded =>
              s.updated(
                linkId,
                AwaitingFileUpload(
                  data.id,
                  data.dateTime,
                  data.kdfSalt,
                  data.kdfOps,
                  data.kdfMemLimit,
                  uploadId
                )
              )
          }
          .getOrElse(s)
      }.handleError(_ => ())

    def storeFileUploading(linkId: String, fileId: String): IO[Unit] =
      state.update { s =>
        s.get(linkId)
          .map {
            case data: AwaitingFileUpload =>
              s.updated(
                linkId,
                FileUploading(
                  data.id,
                  data.dateTime,
                  data.kdfSalt,
                  data.kdfOps,
                  data.kdfMemLimit,
                  fileId
                )
              )
          }
          .getOrElse(s)
      }.handleError(_ => ())

    def storeFileUploadingFailed(linkId: String): IO[Unit] =
      state.update { s =>
        s.get(linkId)
          .map {
            case data: FileUploading =>
              s.updated(
                linkId,
                SessionInitialized(
                  linkId,
                  data.dateTime,
                  data.kdfSalt,
                  data.kdfOps,
                  data.kdfMemLimit
                )
              )
          }
          .getOrElse(s)
      }.handleError(_ => ())

    def storeFileUploadingSuccess(linkId: String): IO[Unit] =
      state.update { s =>
        s.get(linkId)
          .map {
            case data: FileUploading =>
              s.updated(
                linkId,
                FileUploaded(
                  data.id,
                  data.dateTime,
                  data.kdfSalt,
                  data.kdfOps,
                  data.kdfMemLimit,
                  data.fileId
                )
              )
          }
          .getOrElse(s)
      }.handleError(_ => ())

    def storeAwaitingDownload(
      linkId: String,
      pk2: String,
      streamEncHeader: String,
      fileName: String,
      fileSize: Long
    ): IO[Unit] =
      state.update { s =>
        s.get(linkId)
          .map {
            case data: FileUploaded =>
              s.updated(
                linkId,
                AwaitingDownload(
                  data.id,
                  data.dateTime,
                  data.kdfSalt,
                  data.kdfOps,
                  data.kdfMemLimit,
                  pk2,
                  streamEncHeader,
                  data.fileId,
                  fileName,
                  fileSize
                )
              )
          }
          .getOrElse(s)
      }.handleError(_ => ())
  }

}

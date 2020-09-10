package io.blindsend.links

import java.time.LocalDateTime

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._
import io.blindsend.model._

object InMemoryLinkRepository {

  def apply(state: Ref[IO, Map[String, LinkState]]) = new LinkRepository {

    def getStatus(linkId: String): IO[Int] =
      state.get.map(s =>
        s.get(linkId)
          .map {
            case _: LinkCreated           => 1
            case _: ReceivedRequesterKeys => 2
            case _: AwaitingFileUpload    => 3
            case _: FileUploading         => 4
            case _: FileUploaded          => 5
            case _: AwaitingDownload      => 6
          }
          .getOrElse(0)
      )

    def getLinkState(linkId: String): IO[Option[LinkState]] =
      state.get.map(s => s.get(linkId))

    def storeLinkGenerated(linkId: String, dateTime: LocalDateTime): IO[Unit] =
      state.update(_ + (linkId -> LinkCreated(linkId, dateTime)))

    def storeReceivedRequesterKeys(
      linkId: String,
      pk1: String,
      sk1EncNonce: String,
      sk1Encrypted: String,
      sk1EkKdfSalt: String,
      sk1EkKdfOps: Int,
      sk1EkKdfMemLimit: Int,
      sk1EkeyHash: String
    ): IO[Unit] =
      state.update { s =>
        s.get(linkId)
          .map {
            case data: LinkCreated =>
              s.updated(
                linkId,
                ReceivedRequesterKeys(
                  data.id,
                  data.dateTime,
                  pk1,
                  sk1EncNonce,
                  sk1Encrypted,
                  sk1EkKdfSalt,
                  sk1EkKdfOps,
                  sk1EkKdfMemLimit,
                  sk1EkeyHash
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
            case data: ReceivedRequesterKeys =>
              s.updated(
                linkId,
                AwaitingFileUpload(
                  data.id,
                  data.dateTime,
                  data.pk1,
                  data.sk1EncNonce,
                  data.sk1Encrypted,
                  data.sk1EkKdfSalt,
                  data.sk1EkKdfOps,
                  data.sk1EkKdfMemLimit,
                  data.sk1EkeyHash,
                  uploadId
                )
              )
            case data: FileUploading =>
              s.updated(
                linkId,
                AwaitingFileUpload(
                  data.id,
                  data.dateTime,
                  data.pk1,
                  data.sk1EncNonce,
                  data.sk1Encrypted,
                  data.sk1EkKdfSalt,
                  data.sk1EkKdfOps,
                  data.sk1EkKdfMemLimit,
                  data.sk1EkeyHash,
                  uploadId
                )
              )
            case data: FileUploaded =>
              s.updated(
                linkId,
                AwaitingFileUpload(
                  data.id,
                  data.dateTime,
                  data.pk1,
                  data.sk1EncNonce,
                  data.sk1Encrypted,
                  data.sk1EkKdfSalt,
                  data.sk1EkKdfOps,
                  data.sk1EkKdfMemLimit,
                  data.sk1EkeyHash,
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
                  data.pk1,
                  data.sk1EncNonce,
                  data.sk1Encrypted,
                  data.sk1EkKdfSalt,
                  data.sk1EkKdfOps,
                  data.sk1EkKdfMemLimit,
                  data.sk1EkeyHash,
                  fileId
                )
              )
          }
          .getOrElse(s)
      }.handleError(_ => ())

    def storeFileUploadingFailed(linkId: String, fileId: String): IO[Unit] =
      state.update { s =>
        s.get(linkId)
          .map {
            case data: FileUploading =>
              s.updated(
                linkId,
                ReceivedRequesterKeys(
                  linkId,
                  data.dateTime,
                  data.pk1,
                  data.sk1EncNonce,
                  data.sk1Encrypted,
                  data.sk1EkKdfSalt,
                  data.sk1EkKdfOps,
                  data.sk1EkKdfMemLimit,
                  data.sk1EkeyHash
                )
              )
          }
          .getOrElse(s)
      }.handleError(_ => ())

    def storeFileUploadingSuccess(linkId: String, fileId: String): IO[Unit] =
      state.update { s =>
        s.get(linkId)
          .map {
            case data: FileUploading =>
              s.updated(
                linkId,
                FileUploaded(
                  data.id,
                  data.dateTime,
                  data.pk1,
                  data.sk1EncNonce,
                  data.sk1Encrypted,
                  data.sk1EkKdfSalt,
                  data.sk1EkKdfOps,
                  data.sk1EkKdfMemLimit,
                  data.sk1EkeyHash,
                  data.fileId
                )
              )
          }
          .getOrElse(s)
      }.handleError(_ => ())

    def storeAwaitingDownload(
      linkId: String,
      pk2: String,
      ecdhKeyHash: String,
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
                  data.pk1,
                  data.sk1EncNonce,
                  data.sk1Encrypted,
                  data.sk1EkKdfSalt,
                  data.sk1EkKdfOps,
                  data.sk1EkKdfMemLimit,
                  data.sk1EkeyHash,
                  pk2,
                  ecdhKeyHash,
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

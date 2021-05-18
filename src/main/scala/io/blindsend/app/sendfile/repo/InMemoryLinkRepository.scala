package io.blindsend.app.sendfile.repo

import java.time.LocalDateTime

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._
import io.blindsend.app.sendfile.model._

object InMemoryLinkRepository {

  def apply(state: Ref[IO, Map[String, LinkState]]) = new LinkRepository {

    def getLinkState(linkId: String): IO[Option[LinkState]] =
      state.get.map(s => s.get(linkId))

    def storeUploadInitialized(
      linkId: String,
      dateTime: LocalDateTime,
      fileId: String,
      size: Long,
      encFileMetadata: String,
      fileEncNonce: String,
      metadataEncNonce: String,
      kdfSalt: String,
      kdfOps: Int,
      kdfMemLimit: Int
    ): IO[Unit] =
      state.update(
        _ + (linkId -> FileUploadInitialized(
          linkId,
          dateTime,
          fileId,
          size,
          encFileMetadata,
          fileEncNonce,
          metadataEncNonce,
          kdfSalt,
          kdfOps,
          kdfMemLimit
        ))
      )

    def storeFileUploadingSuccess(linkId: String): IO[Unit] =
      state.update { s =>
        s.get(linkId)
          .map {
            case data: FileUploadInitialized =>
              s.updated(
                linkId,
                FileUploaded(
                  data.id,
                  data.dateTime,
                  data.fileId,
                  data.size,
                  data.encFileMetadata,
                  data.fileEncNonce,
                  data.metadataEncNonce,
                  data.kdfSalt,
                  data.kdfOps,
                  data.kdfMemLimit
                )
              )
          }
          .getOrElse(s)
      }.handleError(_ => ())
  }
}

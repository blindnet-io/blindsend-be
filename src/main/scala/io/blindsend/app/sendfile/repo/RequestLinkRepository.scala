package io.blindsend.app.sendfile.repo

import java.time.LocalDateTime

import cats.effect.IO
import io.blindsend.app.sendfile.model.LinkState

trait LinkRepository {

  def getLinkState(linkId: String): IO[Option[LinkState]]

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
  ): IO[Unit]

  def storeFileUploadingSuccess(linkId: String): IO[Unit]
}

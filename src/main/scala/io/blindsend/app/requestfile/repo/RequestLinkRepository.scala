package io.blindsend.app.requestfile.repo

import java.time.LocalDateTime

import cats.effect.IO
import io.blindsend.app.requestfile.model.LinkState

trait LinkRepository {
  def getStatus(linkId: String): IO[Int]

  def getLinkState(linkId: String): IO[Option[LinkState]]

  def storeLinkGenerated(linkId: String, dateTime: LocalDateTime): IO[Unit]

  def storeReceivedRequesterKeys(
    linkId: String,
    kKdfSalt: String,
    kKdfOps: Int,
    kKdfMemLimit: Int
  ): IO[Unit]

  def storeAwaitingFileUpload(linkId: String, uploadId: String): IO[Unit]

  def storeAwaitingDownload(
    linkId: String,
    pk2: String,
    streamEncHeader: String,
    fileName: String,
    fileSize: Long
  ): IO[Unit]

  def storeFileUploading(linkId: String, fileId: String): IO[Unit]

  def storeFileUploadingSuccess(linkId: String): IO[Unit]

  def storeFileUploadingFailed(linkId: String): IO[Unit]
}

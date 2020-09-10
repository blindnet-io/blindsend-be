package io.blindsend.links

import java.time.LocalDateTime

import cats.effect.IO
import io.blindsend.model.LinkState

trait LinkRepository {
  def getStatus(linkId: String): IO[Int]

  def getLinkState(linkId: String): IO[Option[LinkState]]

  def storeLinkGenerated(linkId: String, dateTime: LocalDateTime): IO[Unit]

  def storeReceivedRequesterKeys(
    linkId: String,
    pk1: String,
    sk1EncNonce: String,
    sk1Encrypted: String,
    sk1EkKdfSalt: String,
    sk1EkKdfOps: Int,
    sk1EkKdfMemLimit: Int,
    keyHash: String
  ): IO[Unit]

  def storeAwaitingFileUpload(linkId: String, uploadId: String): IO[Unit]

  def storeAwaitingDownload(
    linkId: String,
    pk2: String,
    ecdhKeyHash: String,
    streamEncHeader: String,
    fileName: String,
    fileSize: Long
  ): IO[Unit]

  def storeFileUploading(linkId: String, fileId: String): IO[Unit]

  def storeFileUploadingSuccess(linkId: String, fileId: String): IO[Unit]

  def storeFileUploadingFailed(linkId: String, fileId: String): IO[Unit]
}

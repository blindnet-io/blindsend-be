package io.blindsend.model

import java.time.LocalDateTime

// TODO: model should be seriously refactored, this is ugly
trait LinkState

case class LinkCreated(
  id: String,
  dateTime: LocalDateTime
) extends LinkState

trait UploadFileMandatoryData {
  val pk1: String
}

case class ReceivedRequesterKeys(
  id: String,
  dateTime: LocalDateTime,
  pk1: String,
  sk1EncNonce: String,
  sk1Encrypted: String,
  sk1EkKdfSalt: String,
  sk1EkKdfOps: Int,
  sk1EkKdfMemLimit: Int,
  sk1EkeyHash: String
) extends LinkState
    with UploadFileMandatoryData

case class AwaitingFileUpload(
  id: String,
  dateTime: LocalDateTime,
  pk1: String,
  sk1EncNonce: String,
  sk1Encrypted: String,
  sk1EkKdfSalt: String,
  sk1EkKdfOps: Int,
  sk1EkKdfMemLimit: Int,
  sk1EkeyHash: String,
  uploadId: String
) extends LinkState
    with UploadFileMandatoryData

case class FileUploading(
  id: String,
  dateTime: LocalDateTime,
  pk1: String,
  sk1EncNonce: String,
  sk1Encrypted: String,
  sk1EkKdfSalt: String,
  sk1EkKdfOps: Int,
  sk1EkKdfMemLimit: Int,
  sk1EkeyHash: String,
  fileId: String
) extends LinkState
    with UploadFileMandatoryData

case class FileUploaded(
  id: String,
  dateTime: LocalDateTime,
  pk1: String,
  sk1EncNonce: String,
  sk1Encrypted: String,
  sk1EkKdfSalt: String,
  sk1EkKdfOps: Int,
  sk1EkKdfMemLimit: Int,
  sk1EkeyHash: String,
  fileId: String
) extends LinkState
    with UploadFileMandatoryData

case class AwaitingDownload(
  id: String,
  dateTime: LocalDateTime,
  pk1: String,
  sk1EncNonce: String,
  sk1Encrypted: String,
  sk1EkKdfSalt: String,
  sk1EkKdfOps: Int,
  sk1EkKdfMemLimit: Int,
  sk1EkeyHash: String,
  pk2: String,
  ecdhKeyHash: String,
  streamEncHeader: String,
  fileId: String,
  fileName: String,
  fileSize: Long
) extends LinkState

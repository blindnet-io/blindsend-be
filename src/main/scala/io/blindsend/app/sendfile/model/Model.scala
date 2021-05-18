package io.blindsend.app.sendfile.model

import java.time.LocalDateTime

trait LinkState

case class FileUploadInitialized(
  id: String,
  dateTime: LocalDateTime,
  fileId: String,
  size: Long,
  encFileMetadata: String,
  fileEncNonce: String,
  metadataEncNonce: String,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int
) extends LinkState

case class FileUploaded(
  id: String,
  dateTime: LocalDateTime,
  fileId: String,
  size: Long,
  encFileMetadata: String,
  fileEncNonce: String,
  metadataEncNonce: String,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int
) extends LinkState

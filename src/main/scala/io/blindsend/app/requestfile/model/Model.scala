package io.blindsend.app.requestfile.model

import java.time.LocalDateTime

trait LinkState

case class LinkCreated(
  id: String,
  dateTime: LocalDateTime
) extends LinkState

case class SessionInitialized(
  id: String,
  dateTime: LocalDateTime,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int
) extends LinkState

case class AwaitingFileUpload(
  id: String,
  dateTime: LocalDateTime,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int,
  uploadId: String
) extends LinkState

case class FileUploading(
  id: String,
  dateTime: LocalDateTime,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int,
  fileId: String
) extends LinkState

case class FileUploaded(
  id: String,
  dateTime: LocalDateTime,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int,
  fileId: String
) extends LinkState

case class AwaitingDownload(
  id: String,
  dateTime: LocalDateTime,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int,
  pk2: String,
  streamEncHeader: String,
  fileId: String,
  fileName: String,
  fileSize: Long
) extends LinkState

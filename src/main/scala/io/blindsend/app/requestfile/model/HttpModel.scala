package io.blindsend.app.requestfile.model

import io.circe.{ Decoder, Encoder }

case class RespUploadParams(maxFileSize: Long)
object RespUploadParams {
  implicit val enc: Encoder[RespUploadParams] = Encoder.forProduct1("max_file_size")(r => r.maxFileSize)
}

case class ReqGetStatus(linkId: String)
object ReqGetStatus {
  implicit val enc: Decoder[ReqGetStatus] = Decoder.forProduct1("link_id")(ReqGetStatus.apply)
}

case class RespGetStatus(linkId: String, status: Int)
object RespGetStatus {
  implicit val enc: Encoder[RespGetStatus] = Encoder.forProduct2("link_id", "status")(r => (r.linkId, r.status))
}

case class RespInitLinkId(linkId: String)
object RespInitLinkId {
  implicit val enc: Encoder[RespInitLinkId] = Encoder.forProduct1("link_id")(r => r.linkId)
}

case class ReqInitSession(
  linkId: String,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int
)
object ReqInitSession {
  implicit val enc: Decoder[ReqInitSession] =
    Decoder.forProduct4(
      "link_id",
      "kdf_salt",
      "kdf_ops",
      "kdf_memory_limit"
    )(ReqInitSession.apply)
}

case class RespInitSession(link: String)
object RespInitSession {
  implicit val enc: Encoder[RespInitSession] = Encoder.forProduct1("link")(r => r.link)
}

case class ReqPrepareUpload(
  linkId: String
)
object ReqPrepareUpload {
  implicit val enc: Decoder[ReqPrepareUpload] =
    Decoder.forProduct1("link_id")(ReqPrepareUpload.apply)
}

case class RespPrepareUpload(linkId: String, uploadId: String)
object RespPrepareUpload {
  implicit val enc: Encoder[RespPrepareUpload] =
    Encoder.forProduct2("link_id", "upload_id")(r => (r.linkId, r.uploadId))
}

case class ReqInitUploadFile(
  linkId: String,
  uploadId: String,
  fileSize: Long
)
object ReqInitUploadFile {
  implicit val dec: Decoder[ReqInitUploadFile] =
    Decoder.forProduct3("link_id", "upload_id", "file_size")(ReqInitUploadFile.apply)
}

case class ReqFinishUpload(
  linkId: String,
  pk2: String,
  streamEncHeader: String,
  fileName: String,
  fileSize: Long
)
object ReqFinishUpload {
  implicit val enc: Decoder[ReqFinishUpload] =
    Decoder.forProduct5("link_id", "pk2", "header", "file_name", "file_size")(ReqFinishUpload.apply)
}

case class ReqGetFileMetadata(
  linkId: String
)
object ReqGetFileMetadata {
  implicit val enc: Decoder[ReqGetFileMetadata] =
    Decoder.forProduct1("link_id")(ReqGetFileMetadata.apply)
}

case class RespGetFileMetadata(
  linkId: String,
  fileName: String,
  fileSize: Long
)
object RespGetFileMetadata {
  implicit val enc: Encoder[RespGetFileMetadata] =
    Encoder.forProduct3("link_id", "file_name", "file_size")(r => (r.linkId, r.fileName, r.fileSize))
}

case class ReqGetKeys(
  linkId: String
)
object ReqGetKeys {
  implicit val enc: Decoder[ReqGetKeys] =
    Decoder.forProduct1("link_id")(ReqGetKeys.apply)
}

case class RespGetKeys(
  linkId: String,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int,
  pk2: String,
  streamEncHeader: String
)
object RespGetKeys {
  implicit val enc: Encoder[RespGetKeys] =
    Encoder.forProduct6(
      "link_id",
      "kdf_salt",
      "kdf_ops",
      "kdf_memory_limit",
      "public_key_2",
      "stream_enc_header"
    )(r =>
      (
        r.linkId,
        r.kdfSalt,
        r.kdfOps,
        r.kdfMemLimit,
        r.pk2,
        r.streamEncHeader
      )
    )
}

case class ReqGetFile(
  linkId: String
)
object ReqGetFile {
  implicit val dec: Decoder[ReqGetFile] =
    Decoder.forProduct1("link_id")(ReqGetFile.apply)
}

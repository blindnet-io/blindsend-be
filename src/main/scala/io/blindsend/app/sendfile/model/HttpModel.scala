package io.blindsend.app.sendfile.model

import io.circe.{ Decoder, Encoder }

case class RespUploadParams(maxFileSize: Long)
object RespUploadParams {
  implicit val enc: Encoder[RespUploadParams] = Encoder.forProduct1("max_file_size")(r => r.maxFileSize)
}

case class ReqInitUpload(
  fileSize: Long,
  fileName: String,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int
)
object ReqInitUpload {
  implicit val dec: Decoder[ReqInitUpload] =
    Decoder.forProduct5("file_size", "file_name", "kdf_salt", "kdf_ops", "kdf_mem_limit")(ReqInitUpload.apply)
}

case class RespInitUpload(linkId: String)
object RespInitUpload {
  implicit val enc: Encoder[RespInitUpload] = Encoder.forProduct1("link_id")(r => (r.linkId))
}

case class RespFinishUpload(link: String)
object RespFinishUpload {
  implicit val enc: Encoder[RespFinishUpload] = Encoder.forProduct1("link")(r => (r.link))
}

case class ReqGetFileMetadata(linkId: String)
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

case class ReqGetFile(linkId: String)
object ReqGetFile {
  implicit val dec: Decoder[ReqGetFile] =
    Decoder.forProduct1("link_id")(ReqGetFile.apply)
}

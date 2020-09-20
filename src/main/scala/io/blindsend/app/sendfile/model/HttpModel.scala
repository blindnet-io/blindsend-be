package io.blindsend.app.sendfile.model

import io.circe.{ Decoder, Encoder }

case class RespUploadParams(maxFileSize: Long)
object RespUploadParams {
  implicit val enc: Encoder[RespUploadParams] = Encoder.forProduct1("max_file_size")(r => r.maxFileSize)
}

case class ReqInitUpload(
  size: Long,
  encFileMetadata: String,
  fileEncNonce: String,
  metadataEncNonce: String,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int
)
object ReqInitUpload {
  implicit val dec: Decoder[ReqInitUpload] =
    Decoder.forProduct7(
      "size",
      "enc_file_meta",
      "file_enc_nonce",
      "meta_enc_nonce",
      "kdf_salt",
      "kdf_ops",
      "kdf_mem_limit"
    )(ReqInitUpload.apply)
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
  size: Long,
  encFileMetadata: String,
  fileEncNonce: String,
  metadataEncNonce: String,
  kdfSalt: String,
  kdfOps: Int,
  kdfMemLimit: Int
)
object RespGetFileMetadata {
  implicit val enc: Encoder[RespGetFileMetadata] =
    Encoder.forProduct8(
      "link_id",
      "size",
      "enc_file_meta",
      "file_enc_nonce",
      "meta_enc_nonce",
      "kdf_salt",
      "kdf_ops",
      "kdf_mem_limit"
    )(r => (r.linkId, r.size, r.encFileMetadata, r.fileEncNonce, r.metadataEncNonce, r.kdfSalt, r.kdfOps, r.kdfMemLimit))
}

case class ReqGetFile(linkId: String)
object ReqGetFile {
  implicit val dec: Decoder[ReqGetFile] =
    Decoder.forProduct1("link_id")(ReqGetFile.apply)
}

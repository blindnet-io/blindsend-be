package io.blindsend.model

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

case class RespLinkId(linkId: String)
object RespLinkId {
  implicit val enc: Encoder[RespLinkId] = Encoder.forProduct1("link_id")(r => r.linkId)
}

case class ReqBeginHandshake(
  linkId: String,
  pk1: String,
  sk1EncNonce: String,
  sk1Encrypted: String,
  sk1EkKdfSalt: String,
  sk1EkKdfOps: Int,
  sk1EkKdfMemLimit: Int,
  sk1EkeyHash: String
)
object ReqBeginHandshake {
  implicit val enc: Decoder[ReqBeginHandshake] =
    Decoder.forProduct8(
      "link_id",
      "public_key",
      "secret_key_encryption_nonce",
      "encrypted_secret_key",
      "kdf_salt",
      "kdf_ops",
      "kdf_memory_limit",
      "key_hash"
    )(ReqBeginHandshake.apply)
}

case class RespBeginHandshake(link: String)
object RespBeginHandshake {
  implicit val enc: Encoder[RespBeginHandshake] = Encoder.forProduct1("link")(r => r.link)
}

case class ReqContinueHandshake(
  linkId: String
)
object ReqContinueHandshake {
  implicit val enc: Decoder[ReqContinueHandshake] =
    Decoder.forProduct1("link_id")(ReqContinueHandshake.apply)
}

case class RespContinueHandshake(linkId: String, pk1: String, uploadId: String)
object RespContinueHandshake {
  implicit val enc: Encoder[RespContinueHandshake] =
    Encoder.forProduct3("link_id", "pk1", "upload_id")(r => (r.linkId, r.pk1, r.uploadId))
}

case class ReqFinishHandshake(
  linkId: String,
  pk2: String,
  keyHash: String,
  streamEncHeader: String,
  fileName: String,
  fileSize: Long
)
object ReqFinishHandshake {
  implicit val enc: Decoder[ReqFinishHandshake] =
    Decoder.forProduct6("link_id", "pk2", "key_hash", "header", "file_name", "file_size")(ReqFinishHandshake.apply)
}

case class ReqGetFileMetadata(
  linkId: String
)
object ReqGetFileMetadata {
  implicit val enc: Decoder[ReqGetFileMetadata] =
    Decoder.forProduct1("link_id")(ReqGetFileMetadata.apply)
}

case class ReqGetKeys(
  linkId: String
)
object ReqGetKeys {
  implicit val enc: Decoder[ReqGetKeys] =
    Decoder.forProduct1("link_id")(ReqGetKeys.apply)
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

case class RespGetKeys(
  linkId: String,
  pk1: String,
  sk1EncNonce: String,
  sk1Encrypted: String,
  sk1EkKdfSalt: String,
  sk1EkKdfOps: Int,
  sk1EkKdfMemLimit: Int,
  pk2: String,
  streamEncHeader: String
)
object RespGetKeys {
  implicit val enc: Encoder[RespGetKeys] =
    Encoder.forProduct9(
      "link_id",
      "public_key_1",
      "secret_key_encryption_nonce",
      "encrypted_secret_key",
      "kdf_salt",
      "kdf_ops",
      "kdf_memory_limit",
      "public_key_2",
      "stream_enc_header"
    )(r =>
      (
        r.linkId,
        r.pk1,
        r.sk1EncNonce,
        r.sk1Encrypted,
        r.sk1EkKdfSalt,
        r.sk1EkKdfOps,
        r.sk1EkKdfMemLimit,
        r.pk2,
        r.streamEncHeader
      )
    )
}

case class ReqGetFileRequest(
  linkId: String,
  sk1EkeyHash: String
)
object ReqGetFileRequest {
  implicit val dec: Decoder[ReqGetFileRequest] =
    Decoder.forProduct2("link_id", "key_hash")(ReqGetFileRequest.apply)
}

package io.blindsend.config

sealed trait StorageConf
case object Fake                                                                                  extends StorageConf
case class PostgresLargeObjects(host: String, db: String, user: String, pass: String)             extends StorageConf
case class GoogleCloudStorage(accountFilePath: String, bucketName: String, tokenRefreshRate: Int) extends StorageConf

sealed trait LinkRepoConf
case object InMemory extends LinkRepoConf

case class Config(
  domain: String,
  assetsDir: String,
  cors: Boolean = false,
  maxFileSize: Long,
  storage: StorageConf,
  linkRepo: LinkRepoConf
)

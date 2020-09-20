package io.blindsend.files

import cats.effect.IO
import fs2.Stream

trait PartialSaveRespType
object PartialSaveRespType {
  case object Success         extends PartialSaveRespType
  case object WrongParameters extends PartialSaveRespType
  case object UnexpectedError extends PartialSaveRespType
}

trait FileStorage {
  def saveFile(id: String, file: Stream[IO, Byte]): IO[Unit]
  def getFile(id: String): IO[Stream[IO, Byte]]
  def deleteFile(id: String): IO[Unit]

  def initSaveFile(id: String): IO[Unit]
  def saveFilePart(
    id: String,
    filePart: Stream[IO, Byte],
    totalSize: Long,
    chunkSize: Long,
    start: Long,
    end: Long
  ): IO[PartialSaveRespType]
}

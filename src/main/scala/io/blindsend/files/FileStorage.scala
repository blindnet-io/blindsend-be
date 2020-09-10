package io.blindsend.files

import cats.effect.IO
import fs2.Stream

trait FileStorage {
  def saveFile(id: String, file: Stream[IO, Byte]): IO[Unit]
  def getFile(id: String): IO[Stream[IO, Byte]]
  def deleteFile(id: String): IO[Unit]
}

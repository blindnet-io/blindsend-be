package io.blindsend.files

import cats.effect._
import fs2.Stream
import fs2.io._

import java.sql._

object PostgresStorageByteaJava {

  def apply(blocker: Blocker)(implicit cs: ContextShift[IO]) =
    new FileStorage {

      Class.forName("org.postgresql.Driver")
      val conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/blindsend", "default", "changeme")

      def saveFile(id: String, file: Stream[IO, Byte]): IO[Unit] =
        // toInputStreamResource(file).use { is =>
        toInputStreamResource(Stream.constant[IO, Byte](0, 10000).take(1024 * 1024 * 400)).use { is =>
          IO(conn.prepareStatement("insert into Files values (?, ?)"))
            .bracket(ps =>
              IO {
                ps.setString(1, id)
                ps.setBinaryStream(2, is)
                ps.executeUpdate()
              }.void
            )(ps => IO(ps.close()))
        }

      def getFile(id: String): IO[Stream[IO, Byte]] =
        IO(conn.prepareStatement("select * from Files where id =?")).map { ps =>
          val res = IO {
            ps.setString(1, id)
            val rs = ps.executeQuery()
            rs.next()
            rs.getBinaryStream(2)
            rs.getBinaryStream(1)
          }

          readInputStream(res, 1024, blocker, true).onFinalize(IO(ps.close()))
        }

      def deleteFile(id: String): IO[Unit] =
        IO(conn.prepareStatement("delete from Files where id =?"))
          .bracket(ps =>
            IO {
              ps.setString(1, id)
              ps.execute()
            }.void
          )(ps => IO(ps.close()))

      def initSaveFile(id: String): IO[Unit] =
        IO.raiseError(new Throwable("Not implemented"))

      def saveFilePart(
        id: String,
        filePart: Stream[IO, Byte],
        totalSize: Long,
        chunkSize: Long,
        start: Long,
        end: Long
      ): IO[PartialSaveRespType] =
        IO.raiseError(new Throwable("Not implemented"))
    }
}

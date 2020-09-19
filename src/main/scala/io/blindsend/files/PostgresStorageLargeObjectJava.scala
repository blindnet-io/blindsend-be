package io.blindsend.files

import cats.effect._
import fs2.Stream
import fs2.io._

import java.sql._
import org.postgresql.largeobject.LargeObjectManager

object PostgresStorageLargeObjectJava {

  def apply(blocker: Blocker)(implicit cs: ContextShift[IO]) =
    new FileStorage {

      Class.forName("org.postgresql.Driver")
      val conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/blindsend", "default", "changeme")
      conn.setAutoCommit(false)
      val lobj = conn.asInstanceOf[org.postgresql.PGConnection].getLargeObjectAPI();

      def saveFile(id: String, file: Stream[IO, Byte]): IO[Unit] = {

        val oid = lobj.createLO(LargeObjectManager.READ | LargeObjectManager.WRITE)
        println(id)
        println(oid)
        val obj = lobj.open(oid, LargeObjectManager.WRITE)

        // Stream.constant[IO, Byte](0, 2048).take(1048576 * 100).chunkN(2048).evalMap(x => IO(obj.write(x.toArray))).compile.drain
        file.chunkN(2048).evalMap(x => IO(obj.write(x.toArray))).compile.drain.guarantee(IO(obj.close())) *> IO {
          val ps = conn.prepareStatement("INSERT INTO FilesLO VALUES (?, ?)")
          ps.setString(1, id)
          ps.setLong(2, oid)
          ps.executeUpdate()
          ps.close()

          conn.commit()
        }

      }

      def getFile(id: String): IO[Stream[IO, Byte]] = {
        val ps = conn.prepareStatement("SELECT fileoid FROM FilesLO WHERE id = ?")
        ps.setString(1, id)
        val rs = ps.executeQuery()
        rs.next()
        val oid = rs.getLong(1)
        println(id)
        println(oid)

        val obj = lobj.open(oid, LargeObjectManager.READ)

        IO(
          readInputStream(IO(obj.getInputStream()), 4096, blocker, true).onFinalize(IO({
            rs.close()
            ps.close()
            conn.commit()
          }))
        )
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

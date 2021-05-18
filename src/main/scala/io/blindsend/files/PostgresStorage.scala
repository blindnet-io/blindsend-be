package io.blindsend.files

import cats.effect._
import fs2.Stream
import fs2.io._
import doobie.implicits._
import doobie.postgres._

import doobie.util.transactor.Transactor
import io.blindsend.config.PostgresLargeObjects

object PostgresStorage {

  def apply(blocker: Blocker, conf: PostgresLargeObjects)(implicit cs: ContextShift[IO]) = {

    val xa = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      s"jdbc:postgresql://${conf.host}/${conf.db}",
      conf.user,
      conf.pass
    )

    def getOid(id: String) =
      sql"SELECT fileoid FROM FilesLO WHERE id = $id".query[Long].unique.transact(xa)

    new FileStorage {

      def saveFile(id: String, file: Stream[IO, Byte]): IO[Unit] =
        toInputStreamResource(file).use { is =>
          val saveLO: LargeObjectManagerIO[Long] =
            PHLOM.createLOFromStream(4096, is)

          for {
            oid <- PHC.pgGetLargeObjectAPI(saveLO).transact(xa)
            q   = sql"INSERT INTO FilesLO VALUES ($id, $oid)".update.run

            _ <- q.transact(xa)
          } yield ()
        }

      def getFile(id: String): IO[Stream[IO, Byte]] =
        getOid(id).map { oid =>
          readOutputStream(blocker, 4096)(os =>
            PHC
              .pgGetLargeObjectAPI(
                PHLOM.createStreamFromLO(4096, oid, os)
              )
              .transact(xa)
          )
        }

      def deleteFile(id: String): IO[Unit] =
        getOid(id).map { oid =>
          PHC
            .pgGetLargeObjectAPI(
              PHLOM.delete(oid)
            )
            .transact(xa)
        }

      def initSaveFile(id: String): IO[Unit] =
        IO.unit

      def saveFilePart(
        id: String,
        filePart: Stream[IO, Byte],
        totalSize: Long,
        chunkSize: Long,
        start: Long,
        end: Long
      ): IO[PartialSaveRespType] =
        saveFile(id, filePart).as(PartialSaveRespType.Success)
    }
  }
}

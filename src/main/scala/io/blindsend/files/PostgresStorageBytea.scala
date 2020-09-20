package io.blindsend.files

import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import fs2.Stream

object PostgresStorageBytea {

  private val saveFileCommand: Command[String ~ Array[Byte]] =
    sql"""
      INSERT INTO Files
      VALUES ($varchar, $bytea)
    """.command

  private val getFileQ: Query[String, Array[Byte]] =
    sql"""
      SELECT file
      FROM   Files
      WHERE  id = $varchar
    """.query(bytea)

  private val deleteFileC: Command[String] =
    sql"""
      DELETE FROM Files
      WHERE id = $varchar
    """.command

  def apply(session: Session[IO]): Resource[IO, FileStorage] =
    for {
      saveFilePC   <- session.prepare(saveFileCommand)
      getFilePQ    <- session.prepare(getFileQ)
      deleteFilePC <- session.prepare(deleteFileC)
    } yield {
      new FileStorage {

        def saveFile(id: String, file: Stream[IO, Byte]): IO[Unit] =
          file.compile.toList.flatMap(bytes => saveFilePC.execute(id ~ bytes.toArray)).void

        def getFile(id: String): IO[Stream[IO, Byte]] =
          getFilePQ.unique(id).map(x => Stream.fromIterator[IO](x.iterator))

        def deleteFile(id: String): IO[Unit] =
          deleteFilePC.execute(id).void

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
}

// import skunk._
// import skunk.implicits._
// import skunk.codec.all._
// import natchez.Trace.Implicits.noop

// case class Row(i: Int, s: String)
// val row: Decoder[Row]  = (int4 ~ varchar).map { case (i, s) => Row(i, s) }
// val row2: Decoder[Row] = (int4 ~ varchar).gmap[Row]

// session <- Session.pooled[IO](
//                   host = "localhost",
//                   port = 5432,
//                   user = "default",
//                   database = "blindsend",
//                   password = Some("changeme"),
//                   max = 10
//                 )

// _ <- Resource.liftF(
//       for {
//         _ <- IO.unit

//         q1    = sql"select * from test".query(int4 ~ varchar).gmap[Row]
//         res1a <- session.use(_.execute(q1))

//         res1b <- session.use(_.prepare(q1).use(ps => ps.stream(Void, 1).compile.toList))

//         q2   = sql"select * from test where int=$int4 and str like $varchar".query(int4 ~ varchar).gmap[Row]
//         res2 <- session.use(_.prepare(q2).use(ps => ps.stream(123 ~ "asd", 1).compile.toList))

//         _ = println(res1a)
//         _ = println(res1b)
//         _ = println(res2)
//       } yield ()
//     )

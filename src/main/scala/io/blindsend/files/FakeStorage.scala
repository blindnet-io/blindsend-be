package io.blindsend.files

import cats.effect.IO
import cats.effect._
import fs2.Stream
import io.blindsend.files._
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._

object FakeStorageServer extends IOApp {

  val fileSize = 5000000000L
  // val fileSize = 1000000000L

  def run(args: List[String]): IO[ExitCode] =
    (for {
      _ <- IO(println(s"Starting fake storage"))
      _ <- BlazeServerBuilder[IO](scala.concurrent.ExecutionContext.global)
            .bindHttp(9001, "0.0.0.0")
            .withHttpApp(
              HttpRoutes
                .of[IO] {
                  case req @ POST -> Root / "post-file" =>
                    req.body
                      .fold(0)((acc, _) => acc + 1)
                      .compile
                      .toList
                      .flatMap(x => IO(println(s"Length: ${x.head}"))) *> Ok()

                  case GET -> Root / "get-file" =>
                    for {
                      _      <- IO.unit
                      stream = Stream.constant[IO, Byte](0, 10000).take(fileSize)

                      response <- Ok(stream)

                    } yield response
                }
                .orNotFound
            )
            .resource
            .use(_ => IO.never)
    } yield ())
      .as(ExitCode.Success)
}

object FakeStorage {

  def apply(httpClient: Client[IO]) = new FileStorage {

    def saveFile(id: String, file: Stream[IO, Byte]): IO[Unit] =
      for {
        _ <- IO.unit

        req = Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString(s"http://0.0.0.0:9001/post-file"),
          body = file
        )
        resp <- httpClient.toHttpApp(req)
        _    = if (resp.status == Status.Ok) IO.unit else IO.raiseError(new Throwable(s"Error while uploading file: $resp"))
      } yield ()

    def getFile(id: String): IO[Stream[IO, Byte]] =
      for {
        _ <- IO.unit

        req = Request[IO](
          uri = Uri.unsafeFromString(s"http://0.0.0.0:9001/get-file")
        )
        resp    <- httpClient.toHttpApp(req)
        newResp <- if (resp.status == Status.Ok) IO(resp.body) else IO.raiseError(new Throwable(s"Error while getting file: $resp"))
      } yield newResp

    def deleteFile(id: String): IO[Unit] =
      IO.unit

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
      IO.raiseError(new Throwable("Not implemented"))
  }
}

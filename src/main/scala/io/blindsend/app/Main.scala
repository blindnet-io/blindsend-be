package io.blindsend.app

import java.security._

import scala.concurrent.duration._

import cats.effect.IO
import cats.effect._
import cats.implicits._
import io.blindsend.config.{
  Config,
  StorageConf,
  LinkRepoConf,
  Fake => FakeStorageConf,
  GoogleCloudStorage => GoogleCloudStorageConf,
  InMemory => InMemoryLinkRepoConf
}
import io.blindsend.files._
import org.http4s.client.blaze._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze._
import org.http4s.server.middleware.CORS
import pureconfig._
import pureconfig.generic.FieldCoproductHint
import pureconfig.generic.auto._
import io.blindsend.config.PostgresLargeObjects
import io.blindsend.app.requestfile.model.{ LinkState => ReqLinkState }
import io.blindsend.app.sendfile.model.{ LinkState => SendLinkState }
import io.blindsend.app.requestfile.FileRequest
import io.blindsend.app.sendfile.FileSend

object Main extends IOApp {

  val logger: org.log4s.Logger = org.log4s.getLogger

  implicit val storageConfHint = new FieldCoproductHint[StorageConf]("type")

  implicit val linkRepoConfHint = new FieldCoproductHint[LinkRepoConf]("type")

  val server =
    for {
      _ <- Resource.liftF(IO(logger.info(s"Starting app")))
      _ <- Resource.liftF(IO(Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())))

      conf <- Resource.liftF(
               IO.fromEither(
                 ConfigSource.file("app.conf").load[Config].leftMap(e => new Throwable(s"Error reading config: ${e.prettyPrint()}"))
               )
             )

      // eventDispatcher <- ThreadPools.eventDispatcherThreadPoolRes
      workStealingTP <- ThreadPools.blockingThreadPoolRes
      cpuBoundedTP   <- ThreadPools.cpuBoundedThreadPoolRes
      blocker        <- Blocker.apply[IO]

      // https://tools.ietf.org/id/draft-thomson-hybi-http-timeout-00.html
      httpClient <- BlazeClientBuilder[IO](workStealingTP)
                     .withResponseHeaderTimeout(1 minute)
                     .withRequestTimeout(3 minutes)
                     .withIdleTimeout(5 minutes)
                     .withMaxTotalConnections(100)
                     .resource

      (reqLinkRepo, sendLinkRepo) <- conf.linkRepo match {
                                      case InMemoryLinkRepoConf =>
                                        val inMem = for {
                                          reqFileState  <- cats.effect.concurrent.Ref.of[IO, Map[String, ReqLinkState]](Map.empty)
                                          sendFileState <- cats.effect.concurrent.Ref.of[IO, Map[String, SendLinkState]](Map.empty)
                                        } yield (
                                          io.blindsend.app.requestfile.repo.InMemoryLinkRepository(reqFileState),
                                          io.blindsend.app.sendfile.repo.InMemoryLinkRepository(sendFileState)
                                        )

                                        Resource.liftF(inMem)
                                    }

      fileStorage <- conf.storage match {
                      case FakeStorageConf =>
                        Resource.pure[IO, FileStorage](FakeStorage(httpClient))
                      case conf: GoogleCloudStorageConf =>
                        Resource.liftF(GoogleCloudStorage(httpClient, cpuBoundedTP, blocker, conf))
                      case conf: PostgresLargeObjects =>
                        Resource.pure[IO, FileStorage](PostgresStorage(blocker, conf))
                    }

      fileRequestService <- Resource.liftF(FileRequest.service(reqLinkRepo, fileStorage, Crypto.rng, conf))
      fileSendService    <- Resource.liftF(FileSend.service(sendLinkRepo, fileStorage, Crypto.rng, conf))
      routes = Router(
        "/api/request" -> (
          if (conf.cors) CORS(fileRequestService) else fileRequestService
        ),
        "/api/send" -> (
          if (conf.cors) CORS(fileSendService) else fileSendService
        ),
        "/" -> Assets.service(blocker, conf.assetsDir)
      )

      // TODO: listen on eventDispatcher, handle on workStealingTP
      _ <- BlazeServerBuilder[IO](workStealingTP)
          // .enableHttp2(true)
            .bindHttp(9000, "0.0.0.0")
            .withHttpApp(routes.orNotFound)
            .withResponseHeaderTimeout(2 minutes)
            .withIdleTimeout(5 minutes)
            .resource

    } yield ExitCode.Success

  def run(args: List[String]): IO[ExitCode] =
    server.use(_ => IO.never).as(ExitCode.Success)

}

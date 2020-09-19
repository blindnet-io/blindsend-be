package io.blindsend.app

import java.io.File

import cats.effect.IO
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

object Assets {

  // TODO: this should be done by nginx/cdn
  def service(
    blocker: Blocker,
    assetsDir: String
  )(implicit cs: ContextShift[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {

    case GET -> Root | GET -> Root / _ =>
      StaticFile
        .fromFile[IO](new File(s"${assetsDir}/index.html"), blocker, None)
        .getOrElseF(NotFound())

    // TODO: this might be dangerous
    // check https://wiki.owasp.org/index.php/Path_Traversal
    case GET -> path =>
      StaticFile
        .fromFile[IO](new File(s"${assetsDir}/${path}"), blocker, None)
        .getOrElseF(NotFound())
  }
}

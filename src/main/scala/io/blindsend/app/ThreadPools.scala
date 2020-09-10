package io.blindsend.app

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor

import cats.effect.IO
import cats.effect._
import com.google.common.util.concurrent.ThreadFactoryBuilder

object ThreadPools {

  def eventDispatcherThreadPoolRes: Resource[IO, ExecutionContextExecutor] =
    Resource(IO {
      val executor = Executors.newFixedThreadPool(2, new ThreadFactoryBuilder().setNameFormat("event-dispatcher-%d").build())
      val ec       = ExecutionContext.fromExecutor(executor)
      (ec, IO(executor.shutdown()))
    })

  def cpuBoundedThreadPoolRes: Resource[IO, ExecutionContextExecutor] =
    Resource(IO {
      val executor = Executors
        .newFixedThreadPool(Runtime.getRuntime.availableProcessors, new ThreadFactoryBuilder().setNameFormat("cpu-bound-%d").build())
      val ec = ExecutionContext.fromExecutor(executor)
      (ec, IO(executor.shutdown()))
    })

  def blockingThreadPoolRes: Resource[IO, ExecutionContextExecutor] =
    Resource(IO {
      // TODO: how to name workers in ForkJoinPool?
      val executor = Executors.newWorkStealingPool()
      val ec       = ExecutionContext.fromExecutor(executor)
      (ec, IO(executor.shutdown()))
    })
}

package com.github.gvolpe.http4s.server.service

import java.io.{File, FileOutputStream, OutputStream}

import cats.effect.Effect
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.gvolpe.http4s.StreamUtils
import fs2.Stream
import org.http4s.multipart.Part

import scala.concurrent.ExecutionContext.Implicits.global

class FileService[F[_]](implicit F: Effect[F], S: StreamUtils[F]) {

  def homeDirectories(depth: Option[Int]): Stream[F, String] =
    S.env("HOME").flatMap { maybePath =>
      val ifEmpty = S.error("HOME environment variable not found!")
      maybePath.fold(ifEmpty)(directories(_, depth.getOrElse(1)))
    }

  def directories(path: String, depth: Int): Stream[F, String] = {

    def dir(f: File, d: Int): Stream[F, File] = {
      val dirs = Stream.emits(f.listFiles().toSeq).filter(_.isDirectory).covary[F]

      if (d <= 0) Stream.empty
      else if (d == 1) dirs
      else dirs ++ dirs.flatMap(x => dir(x, d - 1))
    }

    S.evalF(new File(path)).flatMap { file =>
      dir(file, depth)
        .map(_.getName)
        .filter(!_.startsWith("."))
        .intersperse("\n")
    }
  }

  def store(part: Part[F]): Stream[F, Unit] = {
    val os: F[OutputStream] =
      for {
        home      <- F.delay(sys.env.getOrElse("HOME", "/tmp"))
        filename  <- F.delay(part.filename.getOrElse("sample"))
        output    <- F.delay(new FileOutputStream(s"$home/$filename"))
      } yield output

    part.body to fs2.io.writeOutputStreamAsync(os, closeAfterUse = true)
  }

}

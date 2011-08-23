scalaVersion := "2.9.0-1"

name := "akka-async-http"

organization := "localhost"

version := "1.0-SNAPSHOT"

resolvers += "Akka Repo" at "http://akka.io/repository"

libraryDependencies ++= Seq("se.scalablesolutions.akka" % "akka-actor" % "1.2-RC3" % "compile",
                            "com.ning" % "async-http-client" % "1.6.4" % "compile",
                            //"ch.qos.logback" % "logback-classic" % "0.9.29" % "runtime",
                            "org.slf4j" % "slf4j-nop" % "1.6.0" % "runtime",
                            "org.scalatest" % "scalatest_2.9.0" % "1.6.1" % "test")
                            
autoCompilerPlugins := true

addCompilerPlugin("org.scala-lang.plugins" % "continuations" % "2.9.0-1")

scalacOptions += "-P:continuations:enable"

parallelExecution in Test := false

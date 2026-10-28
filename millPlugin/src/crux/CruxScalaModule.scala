                                                                                                  /*
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                                                                                                  ┃
┃    Crux, version 0.1.0.                                                                          ┃
┃    © Copyright 2026 Jon Pretty, Propensive OÜ.                                                   ┃
┃                                                                                                  ┃
┃    The primary distribution site is:                                                             ┃
┃                                                                                                  ┃
┃        https://github.com/propensive/crux/                                                       ┃
┃                                                                                                  ┃
┃    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file     ┃
┃    except in compliance with the License. You may obtain a copy of the License at                ┃
┃                                                                                                  ┃
┃        https://www.apache.org/licenses/LICENSE-2.0                                               ┃
┃                                                                                                  ┃
┃    Unless required by applicable law or agreed to in writing,  software distributed under the    ┃
┃    License is distributed on an "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    ┃
┃    either express or implied. See the License for the specific language governing permissions    ┃
┃    and limitations under the License.                                                            ┃
┃                                                                                                  ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                                                                                                  */
package crux

import mill.*
import mill.javalib.Dep
import mill.scalalib.ScalaModule

// A Scala module compiled with the pinned Proscala — the Propensive fork of Scala 3, whose
// releases publish the compiler's own modules in the crux layout:
//
//     object deps extends CruxModule:
//       def refsFiles = Task.Sources(BuildCtx.workspaceRoot / "etc" / "refs")
//
//     trait Project extends CruxScalaModule:
//       def crux = deps
//
// with `propensive/proscala  3.10.0-pro-1.0.5` among the pins. The Scala version is the pin's;
// Mill resolves the compiler, the library and the compiler bridge as `org.scala-lang` through the
// module's repositories, which `CruxDeps` has extended with the Proscala release.
//
// A pinned library names the fork at the release it was built with, which need not be the one
// pinned here, and only the pinned release is resolvable; so every `org.scala-lang` dependency,
// transitive ones included, is forced to the Scala version. (Mill does this for the standard
// library alone; a library depending on the compiler, say, needs the rest.) The fork stays
// TASTy-compatible across its patch releases, which is what every such pin assumes.
trait CruxScalaModule extends ScalaModule, CruxDeps:
  // The pinned repository that supplies the compiler.
  def scalaRepository: String = "proscala"

  def scalaVersion: T[String] = Task(crux.version(scalaRepository)())

  // The fork's supplementary runtime — `proscala-library_3`, or its Scala.js or Scala Native
  // build for a module of that platform — which its standard library needs at run time and
  // whose types the pinned libraries' TASTy refers to. Proscala's own POMs have the dependency
  // the other way round (the runtime depends on the standard library), so nothing published
  // pulls it in; it is added here, as the standard library's companion.
  // The platform's suffix, decided by the module's type when the build is defined: a task, since
  // Scala Native's is read from a task of the module.
  private def runtimePlatform: Task[String] = this match
    case _: mill.scalajslib.ScalaJSModule              => Task.Anon("_sjs1")
    case native: mill.scalanativelib.ScalaNativeModule => Task.Anon(s"_native${native.scalaNativeBinaryVersion()}")
    case _                                             => Task.Anon("")

  override def scalaLibraryMvnDeps: T[Seq[Dep]] = Task:
    super.scalaLibraryMvnDeps()
      :+ Dep.parse(s"org.scala-lang:proscala-library${runtimePlatform()}_3:${scalaVersion()}")

  override def mapDependencies: Task[coursier.Dependency => coursier.Dependency] = Task.Anon:
    val scala = coursier.version.VersionConstraint(scalaVersion())
    val previous = super.mapDependencies()

    (dependency: coursier.Dependency) =>
      val mapped = previous(dependency)
      if mapped.module.organization.value == "org.scala-lang" then mapped.withVersionConstraint(scala)
      else mapped

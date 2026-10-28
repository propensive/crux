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
import mill.api.PathRef
import mill.javalib.Dep

import upickle.default.{ReadWriter, macroRW}
import CruxModule.given

// The pins of a build, and the repositories they denote. A build declares one of these — usually
// as `object deps` (not `crux`, which inside `package build` would shadow this package) — and
// says where its pins come from; crux assumes no default file:
//
//     object deps extends CruxModule:
//       def refsFiles = Task.Sources(BuildCtx.workspaceRoot / "etc" / "refs")
//
// or, without a pin file:
//
//     object deps extends CruxModule:
//       def declared = Seq(Pin("propensive/soundness", "0.74.0"))
//
// A pin file is read as a source, so that editing it invalidates the resolution of every module
// that depends on these pins through `CruxDeps`.
trait CruxModule extends Module:
  // Pins declared in the build itself.
  def declared: Seq[Pin] = Nil

  // Pin files, in the format `Refs.parse` reads.
  def refsFiles: T[Seq[PathRef]] = Task.Sources()

  // The organisation of the libraries, as `dep` names them.
  def organisation: String = "dev.propensive"

  // The organisations a pinned release serves; a module of any other organisation is never
  // requested from it. A release serves a module under whichever organisation is asked for, so
  // this is what decides the coordinates: libraries are `dev.propensive`, while a Proscala release
  // is the Scala toolchain, which every build asks for as `org.scala-lang` (its Scala.js modules
  // as `org.scala-js`).
  def organisations(pin: Pin): Set[String] =
    if pin.proscala then Set("org.scala-lang", "org.scala-js") else Set(organisation)

  // Where, within each pinned repository, its own pin file lives, for the transitive walk.
  def upstreamRefs: String = "etc/refs"

  // Where fetched upstream pin files are cached. Content is immutable per tag, so the cache is
  // shared between builds and never invalidated.
  def cacheDirectory: os.Path = os.home / ".cache" / "crux" / "refs"

  // The build's direct pins.
  def refs: T[Seq[Pin]] = Task:
    val fromFiles = refsFiles().flatMap(file => Refs.parse(os.read(file.path)))
    val all = declared ++ fromFiles
    if all.isEmpty then Task.fail("crux: no pins; define `declared` or `refsFiles`")
    all

  // Every pin resolution can reach: the direct pins and, transitively, the pins of each pinned
  // release, read from its repository at its tag.
  def pins: T[Seq[Pin]] = Task:
    Refs.closure(refs(), Refs.Cache(cacheDirectory.toNIO, upstreamRefs).fetch)

  // The repositories as Coursier repository strings, for a `repositories` override or a
  // `//| mill-repositories` header. Ungated: prefer `CruxDeps`.
  def repositories: T[Seq[String]] = Task(pins().map(_.repositoryString))

  // The repositories `CruxDeps` adds, gated by `organisations`.
  def coursierRepositories: Task[Seq[coursier.Repository]] = Task.Anon:
    pins().map(pin => GitHubRepository(pin, organisations(pin)))

  // The pinned version of a repository, named as `owner/name` or just `name`.
  def version(repository: String): Task[String] = Task.Anon:
    refs().find(pin => pin.repository == repository || pin.name == repository) match
      case Some(pin) => pin.version
      case None      => Task.fail(s"crux: no pin for $repository")

  // A dependency on a module of a pinned repository at its pinned version.
  def dep(repository: String, artifact: String): Task[Dep] = Task.Anon:
    Dep.parse(s"$organisation:$artifact:${version(repository)()}")

object CruxModule:
  given ReadWriter[Pin] = macroRW

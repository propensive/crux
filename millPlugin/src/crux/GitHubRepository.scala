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

import coursier.core.{ArtifactSource, Classifier, Dependency, Module, Project, Publication, Repository}
import coursier.ivy.IvyRepository
import coursier.util.{Artifact, EitherT, Monad}
import coursier.version.Version

// A pinned release as a Coursier repository: the Ivy-pattern repository over its assets, answering
// only for the organisations crux serves.
//
// The gate matters because Coursier tries repositories in order, Mill puts a module's own first,
// and a miss on an Ivy repository is not cached (Coursier records a 404 only for a changing
// artifact or a Maven listing) — so without it, every third-party module would cost two GitHub
// requests per pin on every uncached resolution. With it, a module from another organisation is
// refused before any request is made.
//
// A case class, so that two instances for the same pin are equal: Mill gives a module the
// repositories of every module it depends on, transitively, and deduplicates them by equality —
// with reference equality, Soundness's module graph multiplied these into millions of live
// objects and exhausted the heap.
final case class GitHubRepository(pin: Pin, organisations: Set[String]) extends Repository:
  private lazy val underlying: IvyRepository =
    IvyRepository.parse(pin.artifactPattern, Some(pin.metadataPattern)) match
      case Right(repository) => repository
      case Left(error)       => throw IllegalArgumentException(s"crux: bad repository pattern: $error")

  override def repr: String = s"crux:${pin.repository}:${pin.version}"

  override def find0[F[_]](module: Module, version: Version, fetch: Repository.Fetch[F])
     (using F: Monad[F])
  :     EitherT[F, String, (ArtifactSource, Project)] =
    if organisations(module.organization.value) then underlying.find0(module, version, fetch)
    else EitherT(F.point(Left(s"${module.repr} is not served by $repr")))

  // Abstract in `Repository` still, though Coursier calls `find0`.
  def find[F[_]](module: Module, version: String, fetch: Repository.Fetch[F])(using F: Monad[F])
  :     EitherT[F, String, (ArtifactSource, Project)] =
    find0(module, Version(version), fetch)

  def artifacts(dependency: Dependency, project: Project, overrideClassifiers: Option[Seq[Classifier]])
  :     Seq[(Publication, Artifact)] =
    underlying.artifacts(dependency, project, overrideClassifiers)

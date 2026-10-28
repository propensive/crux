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

// A pinned upstream: a GitHub repository, `owner/name`, and the version of it a build compiles
// against. Everything crux knows about where that version's artifacts live follows from the two:
//
//   - A version `X.Y.Z` is the GitHub Release tagged `X.Y.Z`.
//   - A version `X.Y.Z-<12 hex>` is a snapshot of an unreleased build, published as the
//     pre-release tagged `snapshot-<hex>` (the hex is the start of the filtered tree hash of its
//     commit). The tag is not the version, which is why the tag is baked into the repository URL
//     and `[revision]` left to name the version.
//   - A Proscala version `<scala version>-pro-X.Y.Z` is one of several compiler builds published
//     under the Proscala release tagged `X.Y.Z`.
//
// A release's assets are a flat namespace, `https://github.com/<repository>/releases/download/
// <tag>/<asset>`, so a Maven layout is impossible; but Coursier's Ivy-pattern repositories accept
// any pattern, and a separate one for metadata. The crux layout names each module's jar
// `<artifactId>-<version>.jar` among the release's assets, and keeps its metadata,
// `<artifactId>-<version>.ivy.xml`, in the directory `<version>/` of the repository's orphan `ivy`
// branch, served raw — GitHub limits a release to a thousand assets, which Soundness's jars alone
// approach, and a branch has no such limit. Note that neither pattern names the organisation: a
// release serves its modules under whichever organisation is asked for.
case class Pin(repository: String, version: String):
  require(repository.count(_ == '/') == 1, s"crux: `$repository` is not of the form owner/name")

  def owner: String = repository.takeWhile(_ != '/')
  def name: String = repository.dropWhile(_ != '/').drop(1)

  def snapshot: Boolean = version match
    case Pin.Snapshot(_, _) => true
    case _                  => false

  // A Proscala pin: a Scala compiler, asked for as `org.scala-lang` rather than as its publisher.
  def proscala: Boolean = version match
    case Pin.Proscala(_) => true
    case _               => false

  // For a Proscala pin, the upstream Scala version the compiler derives from: `3.10.0` for
  // `3.10.0-pro-1.0.5`, `3.10.1` for `3.10.1-ad6491c9c142b2c1-pro-1.0.5`.
  def scalaBase: Option[String] = version match
    case Pin.Proscala(_) => Some(version.takeWhile(_ != '-'))
    case _               => None

  def tag: String = version match
    case Pin.Snapshot(_, hex)  => s"snapshot-$hex"
    case Pin.Proscala(release) => release
    case _                     => version

  // The release's download URL, which every jar's URL extends.
  def base: String = s"https://github.com/$repository/releases/download/$tag/"

  // The URL of this version's directory on the `ivy` branch, which every descriptor's URL extends.
  def metadataBase: String = s"https://raw.githubusercontent.com/$repository/${Pin.metadataBranch}/$version/"

  // The two patterns, absolute.
  def artifactPattern: String = base + Pin.artifactPattern
  def metadataPattern: String = metadataBase + Pin.metadataPattern

  // The repository as a Coursier repository string, `ivy:<artifact pattern>|<metadata pattern>`,
  // for Mill's `repositories`, a `//| mill-repositories` header, `cs -r` or scala-cli.
  def repositoryString: String = s"ivy:$artifactPattern|$metadataPattern"

  // The URL of a file in the pinned repository at the pinned tag, such as its own pin file.
  def fileUrl(path: String): String = s"https://raw.githubusercontent.com/$repository/$tag/$path"

object Pin:
  // Relative to `base` and `metadataBase`. The metadata pattern must name the module rather than
  // the artifact, because for metadata Coursier substitutes `ivy` for `[artifact]` and `xml` for
  // `[ext]`. The artifact pattern carries the optional classifier so that a request for a sources
  // jar, should one ever be published, cannot resolve to the main jar.
  val artifactPattern: String = "[artifact]-[revision](-[classifier]).[ext]"
  val metadataPattern: String = "[module]-[revision].ivy.xml"

  // The branch of each repository that holds the descriptors, one directory per version.
  val metadataBranch: String = "ivy"

  private val Snapshot = """(\d+\.\d+\.\d+)-([0-9a-f]{12})""".r
  private val Proscala = """.+-pro-(\d+\.\d+\.\d+)""".r

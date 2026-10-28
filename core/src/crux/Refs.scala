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

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

// The pin-file format, and the walk over pin files that turns a build's direct pins into every
// release its resolution can reach.
//
// A pin file is tab-separated, one upstream per line: `repository`, `version`, and for a snapshot
// the `commit` it was built from; `#` begins a comment. It is the format of `etc/refs` across the
// propensive repositories — and of `deps.py` in propensive/.github, which walks the same files.
// crux reads the file at whatever path a build names; it assumes no location.
//
// The walk exists because a library's published ivy.xml names the versions of its own
// dependencies — the versions it was built against — and a consumer whose pins differ needs a
// repository for each of those too. The pins of a pinned release are read from the pin file in
// its repository at its tag, and so on transitively. Every (repository, version) reached is kept:
// Coursier reconciles the versions, as it would with any repository.
object Refs:
  def parse(text: String): Seq[Pin] =
    text.linesIterator.map(_.trim).filter(line => line.nonEmpty && !line.startsWith("#")).map: line =>
      line.split("\\s+").toList match
        case repository :: version :: _ => Pin(repository, version)
        case _ => throw IllegalArgumentException(s"crux: cannot parse pin `$line`")
    . toSeq

  def render(pins: Seq[Pin]): String = pins.map(pin => s"${pin.repository}\t${pin.version}\n").mkString

  // The transitive closure of `pins`, direct pins first, each (repository, version) once. `fetch`
  // reads a pinned release's own pin file, or `None` where it publishes none.
  def closure(pins: Seq[Pin], fetch: Pin => Option[String]): Seq[Pin] =
    val seen = collection.mutable.LinkedHashSet[Pin]()
    val queue = collection.mutable.Queue.from(pins)

    while queue.nonEmpty do
      val pin = queue.dequeue()
      if seen.add(pin) then fetch(pin).foreach(text => queue.enqueueAll(parse(text)))

    seen.toSeq

  // Pin files fetched from GitHub, cached under `directory` by repository and tag. A tag's content
  // is immutable, so a cached file is never refetched; a release with no pin file is recorded as
  // `absent`, so its 404 is not repeated either. `path` is where, within each upstream repository,
  // its pin file lives.
  class Cache(directory: Path, path: String = "etc/refs"):
    private lazy val client = HttpClient.newBuilder.followRedirects(HttpClient.Redirect.NORMAL).build

    def fetch(pin: Pin): Option[String] =
      val home = directory.resolve(pin.owner).resolve(pin.name).resolve(pin.tag)
      val present = home.resolve("refs")
      val absent = home.resolve("absent")

      if Files.exists(present) then Some(Files.readString(present, UTF_8))
      else if Files.exists(absent) then None
      else
        val url = pin.fileUrl(path)
        val request = HttpRequest.newBuilder(URI.create(url)).GET.build
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(UTF_8))
        Files.createDirectories(home)

        response.statusCode match
          case 200 =>
            Files.writeString(present, response.body, UTF_8)
            Some(response.body)
          case 404 =>
            Files.writeString(absent, "", UTF_8)
            None
          case status =>
            throw RuntimeException(s"crux: fetching $url for pin ${pin.repository} ${pin.version} returned HTTP $status")

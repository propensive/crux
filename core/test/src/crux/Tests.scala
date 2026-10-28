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

import utest.*

object PinTests extends TestSuite:
  val tests = Tests:
    test("a release's tag is its version"):
      val pin = Pin("propensive/soundness", "0.74.0")
      assert(pin.tag == "0.74.0")
      assert(!pin.snapshot)
      assert(pin.owner == "propensive")
      assert(pin.name == "soundness")

    test("a snapshot's tag names the tree hash"):
      val pin = Pin("propensive/soundness", "0.74.0-a37f5dccbcf8")
      assert(pin.tag == "snapshot-a37f5dccbcf8")
      assert(pin.snapshot)

    test("a Proscala version's tag is its release, and its base the upstream Scala"):
      assert(Pin("propensive/proscala", "3.10.0-pro-1.0.5").tag == "1.0.5")
      assert(Pin("propensive/proscala", "3.10.0-pro-1.0.5").scalaBase == Some("3.10.0"))
      assert(Pin("propensive/proscala", "3.10.1-ad6491c9c142b2c1-pro-1.0.5").tag == "1.0.5")
      assert(Pin("propensive/proscala", "3.10.1-ad6491c9c142b2c1-pro-1.0.5").scalaBase == Some("3.10.1"))
      assert(Pin("propensive/soundness", "0.74.0").scalaBase == None)

    test("the repository string bakes the tag in and leaves the revision to Coursier"):
      val pin = Pin("propensive/pyrocosm", "0.8.2-08cb714c0ff5")
      val base = "https://github.com/propensive/pyrocosm/releases/download/snapshot-08cb714c0ff5/"
      val metadata = "https://raw.githubusercontent.com/propensive/pyrocosm/ivy/0.8.2-08cb714c0ff5/"
      val expected = s"ivy:$base[artifact]-[revision](-[classifier]).[ext]|$metadata[module]-[revision].ivy.xml"
      assert(pin.repositoryString == expected)

    test("a repository must be owner/name"):
      intercept[IllegalArgumentException](Pin("soundness", "0.74.0"))

object RefsTests extends TestSuite:
  val tests = Tests:
    test("parse skips comments and blanks and ignores the commit column"):
      val text =
        """|# pins
           |propensive/soundness	0.74.0
           |
           |propensive/pyrocosm	0.8.2-08cb714c0ff5	08cb714c0ff5deadbeef
           |""".stripMargin

      val pins = Refs.parse(text)
      assert(pins == Seq(Pin("propensive/soundness", "0.74.0"), Pin("propensive/pyrocosm", "0.8.2-08cb714c0ff5")))

    test("render round-trips"):
      val pins = Seq(Pin("propensive/soundness", "0.74.0"), Pin("propensive/lira", "0.3.1"))
      assert(Refs.parse(Refs.render(pins)) == pins)

    test("closure walks transitively, keeps every version, and lists direct pins first"):
      val upstream = Map(
        Pin("propensive/fever", "0.1.0")    -> "propensive/soundness\t0.73.0\npropensive/pyrocosm\t0.8.0\n",
        Pin("propensive/pyrocosm", "0.8.0") -> "propensive/soundness\t0.74.0\n")

      val closure = Refs.closure(Seq(Pin("propensive/fever", "0.1.0")), upstream.get)

      assert(closure == Seq(
        Pin("propensive/fever", "0.1.0"),
        Pin("propensive/soundness", "0.73.0"),
        Pin("propensive/pyrocosm", "0.8.0"),
        Pin("propensive/soundness", "0.74.0")))

    test("closure terminates on cycles"):
      val a = Pin("propensive/a", "1.0.0")
      val b = Pin("propensive/b", "1.0.0")
      val upstream = Map(a -> Refs.render(Seq(b)), b -> Refs.render(Seq(a)))
      assert(Refs.closure(Seq(a), upstream.get) == Seq(a, b))

    test("the cache fetches once and records absence"):
      val directory = java.nio.file.Files.createTempDirectory("crux-refs")
      val cache = Refs.Cache(directory, "etc/refs")
      // A tag that exists with no pin file in it; recorded as absent, so the second call makes no
      // request. (Needs the network once.)
      val pin = Pin("propensive/soundness", "0.60.0")
      val first = cache.fetch(pin)
      val second = cache.fetch(pin)
      assert(first == second)
      val home = directory.resolve("propensive/soundness/0.60.0")
      assert(java.nio.file.Files.exists(home.resolve("refs")) || java.nio.file.Files.exists(home.resolve("absent")))

object DescriptorTests extends TestSuite:
  val tests = Tests:
    test("strip removes the sources and javadoc publications and nothing else"):
      val ivy =
        """|<?xml version="1.0" encoding="UTF-8"?>
           |<ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
           |  <info organisation="dev.propensive" module="crux-core" revision="0.1.0" status="release"/>
           |  <configurations>
           |    <conf name="compile" visibility="public" description=""/>
           |  </configurations>
           |  <publications>
           |    <artifact name="crux-core" type="pom" ext="pom" conf="pom"/>
           |    <artifact name="crux-core" type="jar" ext="jar" conf="compile"/>
           |    <artifact name="crux-core" type="src" ext="jar" conf="compile" e:classifier="sources"/>
           |    <artifact name="crux-core" type="doc" ext="jar" conf="compile" e:classifier="javadoc"/>
           |  </publications>
           |  <dependencies>
           |    <dependency org="org.scala-lang" name="scala3-library_3" rev="3.7.4" conf="compile->default(compile)"/>
           |  </dependencies>
           |</ivy-module>
           |""".stripMargin

      val stripped = Descriptor.strip(ivy)
      assert(!stripped.contains("sources"))
      assert(!stripped.contains("javadoc"))
      assert(stripped.contains("""<artifact name="crux-core" type="jar" ext="jar" conf="compile"/>"""))
      assert(stripped.contains("""<artifact name="crux-core" type="pom" ext="pom" conf="pom"/>"""))
      assert(stripped.contains("scala3-library_3"))
      assert(stripped.linesIterator.size == ivy.linesIterator.size - 2)

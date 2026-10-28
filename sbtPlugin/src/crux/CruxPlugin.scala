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

import sbt.*
import sbt.Keys.*
import sbt.librarymanagement.{Patterns, URLRepository}

// The sbt 2 plugin. Enabled everywhere, it contributes nothing until a build declares its pins;
// crux assumes no default pin file:
//
//     cruxRefs := Crux.refsFile(baseDirectory.value / "etc" / "refs")
//     libraryDependencies += Crux.dep("soundness", "rudiments-core").value
//
// or, without a pin file, `cruxRefs := Seq(Pin("propensive/soundness", "0.74.0"))`. Resolvers are
// settings in sbt, so the transitive walk over upstream pin files runs when the build loads; its
// results are cached on disk, per tag, so it reaches the network once per new pin.
object CruxPlugin extends AutoPlugin:
  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  object autoImport:
    val cruxRefs = settingKey[Seq[Pin]]("The build's direct pins")
    val cruxPins = settingKey[Seq[Pin]]("Every pin resolution can reach, transitively")
    val cruxOrganisations = settingKey[Seq[String]]("The organisations the pinned releases serve")
    val cruxUpstreamRefs = settingKey[String]("Where, within each pinned repository, its own pin file lives")
    val cruxCacheDirectory = settingKey[File]("Where fetched upstream pin files are cached")

    object Crux:
      // The pins in a pin file, in the format `Refs.parse` reads.
      def refsFile(file: File): Seq[Pin] = Refs.parse(IO.read(file))

      // The pinned version of a repository, named as `owner/name` or just `name`.
      def version(repository: String): Def.Initialize[String] = Def.setting:
        cruxRefs.value.find(pin => pin.repository == repository || pin.name == repository) match
          case Some(pin) => pin.version
          case None      => sys.error(s"crux: no pin for $repository")

      // A dependency on a module of a pinned repository at its pinned version.
      def dep(repository: String, artifact: String): Def.Initialize[ModuleID] = Def.setting:
        cruxOrganisations.value.head % artifact % version(repository).value

  import autoImport.*

  // A pinned release as an sbt resolver: the Ivy-pattern repository over its jars and descriptors,
  // with absolute patterns since the two live at different bases. sbt puts `resolvers` after Maven
  // Central, so a module from another organisation costs one Central miss and never reaches
  // GitHub; a module of ours costs one Central miss before it is found.
  def resolver(pin: Pin): Resolver =
    val patterns = Patterns(Vector(pin.metadataPattern), Vector(pin.artifactPattern), false, false, false)
    URLRepository(s"crux:${pin.repository}:${pin.version}", patterns)

  override def globalSettings = Seq(
    cruxRefs := Nil,
    cruxOrganisations := Seq("dev.propensive"),
    cruxUpstreamRefs := "etc/refs",
    cruxCacheDirectory := file(sys.props("user.home")) / ".cache" / "crux" / "refs")

  // The Scala toolchain's modules, as a Proscala release publishes them under `org.scala-lang`.
  // A library built against another Proscala release names that release's version of these;
  // Mill forces every `org.scala-lang` artifact to the Scala version, and sbt does not, so where
  // a Proscala pin is the Scala version, these are overridden to it.
  private val toolchain = Seq(
    "scala-library", "scala3-library_3", "scala3-compiler_3", "scala3-interfaces", "tasty-core_3",
    "scala3-staging_3", "scala3-tasty-inspector_3", "scala3-sbt-bridge", "proscala-library_3")

  override def projectSettings = Seq(
    cruxPins := Refs.closure(
      cruxRefs.value,
      Refs.Cache(cruxCacheDirectory.value.toPath, cruxUpstreamRefs.value).fetch),
    resolvers ++= cruxPins.value.map(resolver),
    // The fork's supplementary runtime, which its standard library needs and whose types the
    // pinned libraries' TASTy refers to; Proscala's own POM declares the dependency the other way
    // round, so nothing published pulls it in.
    libraryDependencies ++= cruxRefs.value.find(pin => pin.proscala && pin.version == scalaVersion.value)
      . toSeq.map(pin => "org.scala-lang" % "proscala-library_3" % pin.version),
    dependencyOverrides ++= cruxRefs.value.find(pin => pin.proscala && pin.version == scalaVersion.value)
      . toSeq.flatMap: pin =>
          // sbt's `doc` task wants `scaladoc_3` at the Scala version, which Proscala does not
          // publish; the upstream release's suffices, as it runs in its own configuration.
          toolchain.map(module => "org.scala-lang" % module % pin.version)
            ++ pin.scalaBase.map(base => "org.scala-lang" % "scaladoc_3" % base))

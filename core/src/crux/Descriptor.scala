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

import java.util.regex.Matcher

// The ivy.xml a release publishes beside each jar, derived from the one Mill generates (and
// embeds in the jar under `META-INF/maven/<group>/<artifactId>/ivy.xml`).
object Descriptor:
  private val artifact = """<artifact\b[^>]*?(?:/>|>\s*</artifact>)""".r
  private val secondary = """(?s).*\btype="(src|doc)".*""".r

  // Mill declares the sources and javadoc publications in the `compile` configuration, alongside
  // the jar. The crux artifact pattern, `[artifact]-[revision](-[classifier]).[ext]`, names the
  // main jar for any publication whose classifier is absent — and neither is published — so left
  // in place, a request for sources would resolve to the binary jar. They are removed.
  def strip(ivy: String): String =
    val filtered = artifact.replaceAllIn(ivy, found =>
      found.matched match
        case secondary(_)                      => ""
        case text if text.contains("classifier=") => ""
        case text                              => Matcher.quoteReplacement(text))

    filtered.linesIterator.filter(_.trim.nonEmpty).mkString("", "\n", "\n")

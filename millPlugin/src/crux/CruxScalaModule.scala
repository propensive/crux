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
// with `propensive/proscala  3.10.0-pro-1.0.5` among the pins. The Scala version is the pin's,
// and nothing else is needed: Mill resolves the compiler, the library and the compiler bridge as
// `org.scala-lang` through the module's repositories, which `CruxDeps` has extended with the
// Proscala release, and forces every `org.scala-lang` artifact to the Scala version — so a library
// built against another Proscala release is compiled against this one.
trait CruxScalaModule extends ScalaModule, CruxDeps:
  // The pinned repository that supplies the compiler.
  def scalaRepository: String = "proscala"

  def scalaVersion: T[String] = Task(crux.version(scalaRepository)())

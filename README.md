# Crux

Build-tool plugins — for [Mill](https://mill-build.org) and [sbt](https://www.scala-sbt.org) —
that let a Scala build depend on libraries published only on GitHub Releases, with nothing
installed into a local repository and no step outside the build.

Crux exists for the Propensive libraries: [Soundness](https://github.com/propensive/soundness),
[Pyrocosm](https://github.com/propensive/pyrocosm), [Proscala](https://github.com/propensive/proscala)
and the tools built on them are released on GitHub, not Maven Central.

## How it works

A GitHub release is a flat namespace: `https://github.com/<owner>/<repo>/releases/download/<tag>/<asset>`.
That rules out a Maven layout, but Coursier — the resolver under both Mill and sbt — also resolves
from Ivy-pattern repositories, with any pattern, and a separate pattern for metadata. A release in
the **crux layout** has, for each module:

| file | where | content |
|---|---|---|
| `<artifactId>-<version>.jar` | a release asset | the jar, with its POM and ivy.xml embedded under `META-INF/maven/<group>/<artifactId>/` |
| `<artifactId>-<version>.ivy.xml` | `<version>/` on the repository's orphan `ivy` branch | the same ivy.xml, as Coursier reads it (without sources/javadoc publications) |

The descriptors live on a branch, served raw, because GitHub allows a release a thousand assets
and Soundness's jars alone are most of that. Crux reads a release as the repository

```
ivy:https://github.com/<owner>/<repo>/releases/download/<tag>/[artifact]-[revision](-[classifier]).[ext]
   |https://raw.githubusercontent.com/<owner>/<repo>/ivy/<version>/[module]-[revision].ivy.xml
```

That string also works as-is with `cs fetch -r …` and scala-cli's `//> using repository …`.

Neither pattern names the organisation: a release serves its modules under whichever organisation
is asked for. The libraries are `dev.propensive`; a Proscala release is the Scala toolchain, which
every build asks for as `org.scala-lang`.

### Pins

A build pins each upstream it uses as a repository and a version:

- `X.Y.Z` is the release tagged `X.Y.Z`;
- `X.Y.Z-<12 hex>` is a snapshot of an unreleased build, the pre-release tagged `snapshot-<hex>`;
- a Proscala version `<scala version>-pro-X.Y.Z` is one of the compilers in the release tagged `X.Y.Z`.

A library's published metadata names the versions of *its* dependencies — the ones it was built
against — so Crux also walks the pin file (`etc/refs`) of each pinned release at its tag,
transitively, and adds a repository for every release reached. Coursier reconciles versions as it
would with any repository. Fetched pin files are cached under `~/.cache/crux/refs`; a tag's content
never changes, so a build is offline after its first resolution.

Pins can be declared inline or read from a pin file — tab-separated `owner/repo  version
[commit]` lines, `#` comments — at whatever path the build names. Crux assumes no default file.

## Mill

```scala
//| mill-version: 1.1.5
//| mvnDeps: ["dev.propensive:crux-mill:<crux version>"]
package build

import mill.*, scalalib.*
import mill.api.BuildCtx
import crux.*

// Named `deps`, not `crux`: inside `package build` a module named `crux` would shadow the package.
object deps extends CruxModule:
  def refsFiles = Task.Sources(BuildCtx.workspaceRoot / "etc" / "refs")
  // or: def declared = Seq(Pin("propensive/soundness", "0.74.0"))

// Compiled with the pinned Proscala, against the pinned libraries.
trait Project extends CruxScalaModule:
  def crux = deps
  def mvnDeps = Seq(crux.dep("soundness", "rudiments-core")())
```

`CruxDeps` alone (for a module on stock Scala) adds the pinned repositories to a `CoursierModule`;
`CruxScalaModule` also takes its `scalaVersion` from the `propensive/proscala` pin. The
repositories are consulted only for the organisations each release serves, so third-party
dependencies never cost a request to GitHub.

## sbt 2

```scala
// project/plugins.sbt
addSbtPlugin("dev.propensive" % "sbt-crux" % "<crux version>")

// build.sbt
cruxRefs := Crux.refsFile(baseDirectory.value / "etc" / "refs")
scalaVersion := Crux.version("proscala").value
libraryDependencies += Crux.dep("soundness", "rudiments-core").value
```

## Moving from `sync-deps`

A machine that installed releases into `~/.ivy2/local` with the earlier `sync_releases.py` has
`dev.propensive` entries there whose descriptors still list sources and javadoc jars that were
never published. Mill ignores them; sbt tries to fetch them and fails. Remove
`~/.ivy2/local/dev.propensive` once a build uses crux, so that both tools resolve from GitHub.

## Limits

- Version ranges and `latest.*` do not resolve: a release has no version listing. Pin exact versions.
- Only releases in the crux layout resolve. Releases published before it was adopted are given
  their descriptors by `etc/backfill-ivy.sh` (`etc/backfill-proscala.sh` for Proscala).

## Where Crux itself comes from

Crux is the one Propensive artifact on Maven Central — `dev.propensive:crux-mill`,
`dev.propensive:sbt-crux_sbt2_3` and `dev.propensive:crux-core` — because it is what a build needs
before any pins are known, and both tools look there by default; so no resolver has to be declared
to load it. It is also released on GitHub, in the crux layout, like everything else.

## Repository

- `core` — `dev.propensive:crux-core`: pins, the pin-file format, the walk, the patterns, the ivy.xml filter.
- `millPlugin` — `dev.propensive:crux-mill`.
- `sbtPlugin` — `dev.propensive:sbt-crux_sbt2_3`, for sbt 2 (`addSbtPlugin("dev.propensive" % "sbt-crux" % …)`; sbt adds the suffix).
- `example/mill`, `example/sbt` — consumers, which resolve Pyrocosm from GitHub with the locally published plugins.

`make build`, `make test`, `make publishLocal`. Releases are cut by tagging — see `etc/release` and
[propensive/.github](https://github.com/propensive/.github).

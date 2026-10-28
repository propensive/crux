// A consumer of crux under sbt 2: compiled against the Pyrocosm pinned in etc/refs, with the
// Proscala pinned there as the Scala version.
cruxRefs := Crux.refsFile(baseDirectory.value / "etc" / "refs")
scalaVersion := Crux.version("proscala").value
libraryDependencies += Crux.dep("pyrocosm", "pyrocosm-script").value

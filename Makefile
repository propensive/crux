# Build every module.
build:
	./mill __.compile

# Run the unit tests.
test:
	./mill core.test

# Stage a release's assets — `<artifactId>-<version>.jar` and `<artifactId>-<version>.ivy.xml` per
# module — under out/release/stage.dest, as release.sh does.
stage:
	./mill release.stage

# Publish every module to ~/.ivy2/local, for a build that loads the plugin from there.
publishLocal:
	./mill __.publishLocal

# Releases are cut by tagging, not by make. Tag a commit CI has passed, with `git tag -s
# X.Y.Z && git push --tags`: the tag fires .github/workflows/release.yml, which runs the shared
# release.sh in propensive/.github. What this repository needs beyond the common path is declared
# in etc/release. This target survives only to say so.
release:
	@echo "Releases are triggered by tags, not by make. Once CI has passed on the commit:" >&2
	@echo "" >&2
	@echo "    git tag -s X.Y.Z && git push --tags" >&2
	@echo "" >&2
	@echo "See propensive/.github." >&2
	@exit 1

.PHONY: build test stage publishLocal release

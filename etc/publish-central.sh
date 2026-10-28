#!/usr/bin/env bash
#
# Publish the release's artifacts to Maven Central, once the GitHub release is public. Crux is the
# one Propensive artifact on Central: a build needs it before any pins are known, and both Mill
# and sbt look there by default, so a consumer declares no resolver to load it. Everything else
# stays on GitHub Releases, in the layout crux reads.
#
# Run by release.sh as the `after` command in etc/release, with CRUX_RELEASE_VERSION exported, so
# `publishVersion` is the tag. Mill's SonatypeCentralPublishModule signs and uploads every
# PublishModule (`__:PublishModule.publishArtifacts`: crux-core, crux-mill, sbt-crux_sbt2_3) as
# one bundle and releases it. It reads the credentials from the environment:
#
#   MILL_SONATYPE_USERNAME, MILL_SONATYPE_PASSWORD   a Central Portal user token
#   MILL_PGP_SECRET_BASE64, MILL_PGP_PASSPHRASE      the signing key, exported and base64-encoded
#
# — repository secrets of propensive/crux, passed through scala-release.yml in propensive/.github.
# An `after` command cannot fail a release that is already public; if this fails, run it by hand
# from a checkout of the tag with the same four variables set.

set -euo pipefail

for variable in MILL_SONATYPE_USERNAME MILL_SONATYPE_PASSWORD MILL_PGP_SECRET_BASE64 MILL_PGP_PASSPHRASE; do
  [[ -n "${!variable:-}" ]] || { echo "publish-central: $variable is not set; not publishing" >&2; exit 1; }
done
[[ -n "${CRUX_RELEASE_VERSION:-}" ]] || { echo "publish-central: CRUX_RELEASE_VERSION is not set" >&2; exit 1; }

./mill mill.javalib.SonatypeCentralPublishModule/publishAll
echo "publish-central: published $CRUX_RELEASE_VERSION to Maven Central"

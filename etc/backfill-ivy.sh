#!/usr/bin/env bash
#
# Give a release published before the crux layout its descriptors: for every
# `<artifactId>-<version>.jar` asset of the release, write `<version>/<artifactId>-<version>.ivy.xml`
# — the ivy.xml the jar embeds under META-INF/maven/<group>/<artifactId>/, with its sources and
# javadoc publications removed (as crux.Descriptor.strip does) — to the repository's orphan `ivy`
# branch, and push. Proscala, whose jars embed no descriptor, has backfill-proscala.sh.
#
# Usage: etc/backfill-ivy.sh <owner/repo> <tag> [--dry-run]
#
# Idempotent: a version directory already on the branch is left alone. Needs `gh` authenticated
# with push access to the repository.

set -euo pipefail

REPO=${1:?usage: backfill-ivy.sh <owner/repo> <tag> [--dry-run]}
TAG=${2:?usage: backfill-ivy.sh <owner/repo> <tag> [--dry-run]}
DRY=${3:-}
HERE=$(cd "$(dirname "$0")" && pwd)

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

mapfile -t assets < <(gh api --paginate "repos/$REPO/releases/tags/$TAG" --jq '.assets[].name')

# The jars, and the one version they all carry.
jars=()
VERSION=""
for asset in "${assets[@]}"; do
  [[ "$asset" =~ ^(.+)-([0-9]+\.[0-9]+\.[0-9]+(-[0-9a-f]{12})?)\.jar$ ]] || continue
  jars+=("$asset")
  VERSION=${BASH_REMATCH[2]}
done
(( ${#jars[@]} > 0 )) || { echo "no jars in $REPO $TAG" >&2; exit 1; }

# The branch, checked out in a temporary clone (created, orphan, if the repository has none yet).
"$HERE/ivy-branch.sh" checkout "$REPO" "$WORK/branch"
if [[ -d "$WORK/branch/$VERSION" ]]; then echo "have   $VERSION on the ivy branch of $REPO"; exit 0; fi

# Downloaded in parallel by their direct URLs — a release is public, and the URL of an asset is
# its name under the release.
mkdir -p "$WORK/jars"
printf '%s\n' "${jars[@]}" | xargs -P 8 -I{} \
  curl -fsSL --retry 3 -o "$WORK/jars/{}" "https://github.com/$REPO/releases/download/$TAG/{}"

strip_descriptor() {
  perl -0pe 's{<artifact\b[^>]*?(?:classifier=|\btype="(?:src|doc)")[^>]*?(?:/>|>\s*</artifact>)\s*}{}g' |
    sed '/^[[:space:]]*$/d'
}

mkdir -p "$WORK/branch/$VERSION"
count=0
for asset in "${jars[@]}"; do
  jar="$WORK/jars/$asset"
  [[ -s "$jar" ]] || { echo "skip   $asset (download failed)" >&2; continue; }
  entry=$(unzip -Z1 "$jar" | grep -E '^META-INF/maven/[^/]+/[^/]+/ivy\.xml$' | head -1)
  [[ -n "$entry" ]] || { echo "skip   $asset (no embedded ivy.xml)" >&2; continue; }
  unzip -p "$jar" "$entry" | strip_descriptor > "$WORK/branch/$VERSION/${asset%.jar}.ivy.xml"
  count=$((count + 1))
done
echo "wrote  $count descriptors for $VERSION"

if [[ "$DRY" == "--dry-run" ]]; then echo "dry run: not pushed"; ls "$WORK/branch/$VERSION" | head -3; exit 0; fi
"$HERE/ivy-branch.sh" publish "$WORK/branch" "$VERSION"

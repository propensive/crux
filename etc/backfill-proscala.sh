#!/usr/bin/env bash
#
# Give a Proscala release its descriptors on the `ivy` branch (see ivy-branch.sh). Proscala's loose
# jars embed no descriptor; the POMs are in its `proscala-<version>-maven.zip`, from which an
# ivy.xml per module is written by pom-to-ivy.py (which also names the fork's modules
# `org.scala-lang`, the organisation every build asks for).
#
# Usage: etc/backfill-proscala.sh <tag> <scala version> [--dry-run]    e.g. 1.0.2 3.10.0-pro-1.0.2
#
# Idempotent: a version already on the branch is left alone, unless FORCE=1 regenerates it — which
# only helps a machine that has not yet resolved that version: Coursier caches a descriptor as
# immutable, so a changed one is seen only after its cache entry is removed
# (~/Library/Caches/Coursier/v1/https/raw.githubusercontent.com/<owner>/<repo>/ or ~/.cache/coursier).

set -euo pipefail

TAG=${1:?usage: backfill-proscala.sh <tag> <scala version> [--dry-run]}
VERSION=${2:?usage: backfill-proscala.sh <tag> <scala version> [--dry-run]}
DRY=${3:-}
REPO=propensive/proscala
HERE=$(cd "$(dirname "$0")" && pwd)

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

mapfile -t assets < <(gh api --paginate "repos/$REPO/releases/tags/$TAG" --jq '.assets[].name')
declare -A present
for asset in "${assets[@]}"; do present["$asset"]=1; done

"$HERE/ivy-branch.sh" checkout "$REPO" "$WORK/branch"
if [[ -d "$WORK/branch/$VERSION" && "${FORCE:-0}" != 1 ]]; then echo "have   $VERSION on the ivy branch of $REPO"; exit 0; fi
rm -rf "$WORK/branch/$VERSION"   # FORCE=1 regenerates

zip="proscala-$VERSION-maven.zip"
gh release download "$TAG" --repo "$REPO" --pattern "$zip" --dir "$WORK" >/dev/null
unzip -q "$WORK/$zip" -d "$WORK/maven"

mkdir -p "$WORK/branch/$VERSION"
count=0
while IFS= read -r pom; do
  base=$(basename "$pom" .pom)                     # <artifactId>-<version>
  [[ -n "${present[$base.jar]:-}" ]] || { echo "skip   $base (no loose jar in release)" >&2; continue; }
  python3 -I "$HERE/pom-to-ivy.py" "$pom" "$WORK/branch/$VERSION/$base.ivy.xml"
  count=$((count + 1))
done < <(find "$WORK/maven" -name '*.pom' | sort)
echo "wrote  $count descriptors for $VERSION"

if [[ "$DRY" == "--dry-run" ]]; then echo "dry run: not pushed"; cat "$WORK/branch/$VERSION/scala3-library_3-$VERSION.ivy.xml"; exit 0; fi
"$HERE/ivy-branch.sh" publish "$WORK/branch" "$VERSION"

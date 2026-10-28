#!/usr/bin/env bash
#
# The orphan `ivy` branch of a repository, where the crux layout keeps each released version's
# descriptors (`<version>/<artifactId>-<version>.ivy.xml`), served at
# https://raw.githubusercontent.com/<owner>/<repo>/ivy/<version>/. Two commands:
#
#   ivy-branch.sh checkout <owner/repo> <dir>    clone the branch into <dir>, creating it (orphan,
#                                                empty) if the repository has none
#   ivy-branch.sh publish <dir> <version>        commit <dir>/<version> and push the branch
#
# The release and snapshot scripts in propensive/.github do the same for a release being cut;
# this copy serves the one-off backfill of releases that predate the layout.

set -euo pipefail

BRANCH=ivy

checkout() {
  local repo=$1 dir=$2
  if git ls-remote --exit-code --heads "https://github.com/$repo.git" "$BRANCH" >/dev/null 2>&1; then
    git clone --quiet --depth 1 --branch "$BRANCH" "https://github.com/$repo.git" "$dir"
  else
    git clone --quiet --depth 1 --no-checkout "https://github.com/$repo.git" "$dir"
    git -C "$dir" checkout --quiet --orphan "$BRANCH"
    git -C "$dir" rm -rfq --cached . 2>/dev/null || true
    find "$dir" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
    cat > "$dir/README.md" <<'README'
# Descriptors

One directory per released version, holding the `<artifactId>-<version>.ivy.xml` of every module
in that release: the metadata Coursier reads when [crux](https://github.com/propensive/crux)
uses the release as an Ivy-pattern repository. The jars are the release's assets. Written by the
release pipeline; not for editing.
README
    git -C "$dir" add README.md
    git -C "$dir" -c user.name="propensive" -c user.email="propensive@gmail.com" \
      commit --quiet -m "Begin the ivy branch"
  fi
}

publish() {
  local dir=$1 version=$2
  git -C "$dir" add -A "$version"
  git -C "$dir" -c user.name="propensive" -c user.email="propensive@gmail.com" \
    commit --quiet -m "Descriptors for $version"
  # A token-authenticated push, as `gh` is: a clone over https has no credentials of its own.
  git -C "$dir" -c credential.helper='!gh auth git-credential' push --quiet origin "HEAD:$BRANCH"
  echo "pushed $version to the $BRANCH branch"
}

case ${1:-} in
  checkout) checkout "$2" "$3" ;;
  publish)  publish "$2" "$3" ;;
  *) echo "usage: ivy-branch.sh checkout <owner/repo> <dir> | publish <dir> <version>" >&2; exit 2 ;;
esac

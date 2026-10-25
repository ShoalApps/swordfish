#!/usr/bin/env bash
# Replaces tv/vacuumtube with a VacuumTube release (default: the latest), then rebuilds the bundle.
#   tv/update-vacuumtube.sh [tag]
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
tag="${1:-$(curl -sf https://api.github.com/repos/shy1132/VacuumTube/releases/latest | sed -n 's/.*"tag_name": *"\([^"]*\)".*/\1/p')}"
[ -n "$tag" ] || { echo "couldn't find the latest VacuumTube release" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT
git clone -q --depth 1 --branch "$tag" https://github.com/shy1132/VacuumTube.git "$work/vt"

dest="$here/vacuumtube"
rm -rf -- "$dest/preload" "$dest/locale" "$dest/assets"
mkdir -p "$dest/assets"
cp -r "$work/vt/src/preload" "$dest/preload"
cp -r "$work/vt/locale" "$dest/locale"
cp "$work/vt/assets/icon.png" "$dest/assets/icon.png"
cp "$work/vt/src/config.js" "$dest/host-config.js"
cp "$work/vt/LICENSE" "$dest/LICENSE"
git -C "$work/vt" rev-parse HEAD > "$dest/UPSTREAM_COMMIT"
version="$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$work/vt/package.json" | head -1)"
printf '{ "version": "%s", "tag": "%s" }\n' "$version" "$tag" > "$here/vacuumtube-version.json"

node "$here/build.mjs"
echo "VacuumTube $tag ($version)"

#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if ! command -v java >/dev/null 2>&1; then
  echo 'Java 21 or newer is required. Install a JDK, then try again.'
  exit 1
fi
java -jar movie-theater-manager.jar "$@"

#!/bin/sh

# This script sets the version of the project and commits the changes to git.

VERSION="$1"
if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version>"
    exit 1
fi

mvn versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
git commit -am "Updated version to $VERSION."

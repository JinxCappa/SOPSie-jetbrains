default:
    @just --list

gradle := if path_exists("gradlew") == "true" { "./gradlew" } else { "gradle" }

# Dev build (runs plugin in sandbox IDE, version tagged -dev)
dev:
    {{gradle}} runIde -Pversion.suffix=-dev

# Release build (packaged plugin zip)
release:
    {{gradle}} buildPlugin

# Clean build artifacts
clean:
    {{gradle}} clean

# Verify plugin compatibility
verify:
    {{gradle}} verifyPlugin

# Run tests
test:
    {{gradle}} test

# Create a release: bump version, update changelog, commit, tag, and push
publish version:
    #!/usr/bin/env bash
    set -euo pipefail
    VERSION="{{version}}"
    VERSION="${VERSION#v}"
    echo "Preparing release v${VERSION}..."
    sed -i.bak 's/^version = ".*" + /version = "'"${VERSION}"'" + /' build.gradle.kts
    rm -f build.gradle.kts.bak
    git-cliff --config keepachangelog --tag "v${VERSION}" --output CHANGELOG.md --ignore-tags ".*-.*"
    git add build.gradle.kts CHANGELOG.md
    git commit -m "chore: prepare for v${VERSION}"
    git tag -s "v${VERSION}" -m "v${VERSION}"
    git push origin main
    git push origin "v${VERSION}"
    echo "Released v${VERSION} — workflow will build and publish."

# Dev build via Docker
docker-dev:
    docker build --target artifact --build-arg VERSION_SUFFIX=-dev --output type=local,dest=. .

# Release build via Docker
docker-release:
    docker build --target artifact --output type=local,dest=. .

# Generate Gradle wrapper (run once after cloning)
wrapper:
    gradle wrapper

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

# Dev build via Docker
docker-dev:
    docker build --target artifact --build-arg VERSION_SUFFIX=-dev --output type=local,dest=. .

# Release build via Docker
docker-release:
    docker build --target artifact --output type=local,dest=. .

# Generate Gradle wrapper (run once after cloning)
wrapper:
    gradle wrapper

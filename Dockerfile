FROM gradle:8-jdk21 AS build

ARG VERSION_SUFFIX=

WORKDIR /plugin

COPY build.gradle.kts settings.gradle.kts ./
COPY src/ src/
COPY CHANGELOG.md ./

RUN gradle buildPlugin --no-daemon -Pversion.suffix=${VERSION_SUFFIX}

FROM scratch AS artifact
COPY --from=build /plugin/build/distributions/*.zip /sopsie-jetbrains.zip

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "com.sopsie"
version = "0.1.0" + (findProperty("version.suffix")?.toString() ?: "")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2")
    }
    implementation("org.yaml:snakeyaml:2.2")
}

intellijPlatform {
    pluginConfiguration {
        name = "SOPSie"
        description = file("src/main/resources/description.html").readText()
        changeNotes = file("CHANGELOG.md").readText()
        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

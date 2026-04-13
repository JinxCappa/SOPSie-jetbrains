plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.14.0"
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
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
}

tasks.test {
    useJUnit()
    // Make the test age key available to integration tests under
    // com.sopsie.integration.*. The tests self-skip when `sops` is not
    // on PATH so devs without sops installed are not blocked.
    environment(
        "SOPS_AGE_KEY_FILE",
        "${projectDir}/src/test/resources/age-test-key.txt"
    )
}

// `integrationTest` is a thin alias for `test` that documents intent
// and lets CI invoke the real-SOPS suite explicitly. The integration
// tests live in com.sopsie.integration.* and self-skip when sops is
// missing, so this task simply forwards to `test`.
tasks.register("integrationTest") {
    description = "Runs the real-SOPS round-trip suite (alias for `test`)."
    group = "verification"
    dependsOn(tasks.test)
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
    pluginVerification {
        ides {
            recommended()
        }
    }
}

kotlin {
    jvmToolchain(21)
}

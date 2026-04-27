plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "local.opencoderefresh"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2.5")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "local.opencoderefresh"
        name = "OpenCode Auto Refresh"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "232"
        }

        description = """
            Refreshes IntelliJ IDEA project files when OpenCode edits files outside the IDE.
        """.trimIndent()

        changeNotes = """
            Initial local version: exposes a localhost refresh endpoint for OpenCode.
        """.trimIndent()
    }
}

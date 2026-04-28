plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "local.filehider"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "local.filehider"
        name = "File Hider"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "242"
        }

        vendor {
            name = "Local"
        }

        description = """
            Hides configured file and directory names from IntelliJ IDEA Project Tree without changing files,
            indexing, VCS, search, or build behavior.
        """.trimIndent()

        changeNotes = """
            Initial version with global rules, import/export, Project View filtering, and a per-project show-hidden toggle.
        """.trimIndent()
    }
}

tasks.named("buildSearchableOptions") {
    enabled = false
}

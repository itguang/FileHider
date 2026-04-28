plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "local.filehider"
version = "0.2"

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

            中文说明：根据全局规则在 IntelliJ IDEA 的 Project 视图中隐藏指定文件或目录，仅影响项目树展示；
            不会删除文件，不会修改 .gitignore，也不会影响索引、编译、VCS、搜索或磁盘文件。
        """.trimIndent()

        changeNotes = """
            Initial version with global rules, import/export, Project View filtering, and a per-project show-hidden toggle.
        """.trimIndent()
    }
}

tasks.named("buildSearchableOptions") {
    enabled = false
}

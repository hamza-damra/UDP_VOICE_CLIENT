import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)

    // Audio handling
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")

    // Networking
    implementation("io.ktor:ktor-network:2.3.7")
    implementation("io.ktor:ktor-network-jvm:2.3.7")

    // Additional Compose dependencies
    // Note: material-icons-extended is Android-specific, desktop uses built-in icons
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "VoiceCallApp"
            packageVersion = "1.0.0"
            description = "Voice Call Application with DNS resolver and connection monitoring"
            copyright = "© 2024 Voice Call App. All rights reserved."
            vendor = "Voice Call App Developer"

            windows {
                // Windows-specific settings
                menuGroup = "Voice Call App"
                // Add to Windows Start Menu
                perUserInstall = false
                // Create desktop shortcut
                shortcut = true
                // Allow user to choose installation directory
                dirChooser = true

                // Optional: Set icon if available
                val iconPath = project.file("src/main/resources/icon.ico")
                if (iconPath.exists()) {
                    iconFile.set(iconPath)
                }
            }

            // Include additional files
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
        }
    }
}

// Task to run the test server
tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Run the test voice server"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("TestServerKt")
}

// Task to build MSI installer
tasks.register("buildMsi") {
    group = "distribution"
    description = "Build MSI installer for Voice Call Application"

    dependsOn("packageMsi")

    doLast {
        println("✅ MSI installer built successfully!")
        println("📁 Check build/compose/binaries/main/msi/ for the MSI file")
    }
}

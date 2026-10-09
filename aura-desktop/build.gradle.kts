plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":aura-core"))
    // Cross-platform Java wrapper with a built-in native webcam driver.
    // The Windows provider is selected only on Windows; no camera is opened at startup.
    implementation("com.github.sarxos:webcam-capture:0.3.12")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.aura.desktop.AuraDesktopKt")
}

tasks.test { useJUnitPlatform() }

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

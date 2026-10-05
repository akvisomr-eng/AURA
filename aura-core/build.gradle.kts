plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }

dependencies {
    // The reconstructed source currently contains no JVM test sources.
    // Keep test compilation available for future tests without requiring a test engine now.
    testImplementation(kotlin("test"))
}

tasks.test {
    // Prevent an empty reconstructed test suite from invoking a missing JUnit engine.
    // This becomes active automatically once actual test sources are restored.
    onlyIf {
        project.file("src/test").exists() &&
            project.fileTree("src/test").matching { include("**/*.kt", "**/*.java") }.files.isNotEmpty()
    }
}

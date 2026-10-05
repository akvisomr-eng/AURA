plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }

// No JVM test sources are present in the reconstructed deployment source.
// Keep the standard Gradle test task available without forcing a test engine dependency.

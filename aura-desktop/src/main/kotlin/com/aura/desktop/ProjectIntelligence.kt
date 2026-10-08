package com.aura.desktop

import java.nio.file.Files
import java.nio.file.Path

data class ProjectProfile(
    val root: String,
    val kind: String,
    val sourceFiles: Int,
    val testFiles: Int,
    val buildFiles: List<String>
)

class ProjectIntelligence {
    fun profile(root: Path): ProjectProfile {
        require(Files.isDirectory(root)) { "Folder project tidak ditemukan." }
        val buildFiles = mutableListOf<String>()
        var source = 0
        var tests = 0
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }.limit(5000).forEach {
                val name = it.fileName.toString()
                val lower = name.lowercase()
                if (lower in setOf("build.gradle", "build.gradle.kts", "pom.xml", "package.json", "cargo.toml", "pyproject.toml", "requirements.txt")) buildFiles += name
                if (lower.endsWith(".kt") || lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".tsx")) {
                    source++
                    if (lower.contains("test")) tests++
                }
            }
        }
        val kind = when {
            buildFiles.any { it.startsWith("build.gradle") } -> "Gradle/JVM/Android"
            buildFiles.any { it == "package.json" } -> "Node.js/JavaScript"
            buildFiles.any { it == "pyproject.toml" || it == "requirements.txt" } -> "Python"
            buildFiles.any { it == "Cargo.toml" } -> "Rust"
            else -> "Project umum"
        }
        return ProjectProfile(root.toAbsolutePath().toString(), kind, source, tests, buildFiles.distinct())
    }
}

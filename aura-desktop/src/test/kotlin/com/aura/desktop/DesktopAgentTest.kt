package com.aura.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files

class DesktopAgentTest {
    @Test
    fun approvalProtectsSensitiveActions() {
        val request = ApprovalEngine().classify("jalankan deploy production")
        assertEquals(ApprovalRisk.DESTRUCTIVE, request.risk)
    }

    @Test
    fun projectIntelligenceDetectsGradleProject() {
        val root = Files.createTempDirectory("aura-project")
        Files.writeString(root.resolve("build.gradle.kts"), "plugins { kotlin(\"jvm\") }")
        Files.writeString(root.resolve("Main.kt"), "fun main() {}")
        val profile = ProjectIntelligence().profile(root)
        assertEquals("Gradle/JVM/Android", profile.kind)
        assertEquals(1, profile.sourceFiles)
        assertTrue(profile.buildFiles.contains("build.gradle.kts"))
    }

    @Test
    fun workspaceSearchFindsMatchingLines() {
        val root = Files.createTempDirectory("aura-search")
        Files.writeString(root.resolve("notes.md"), "AURA workspace\nagent runtime\n")
        val hits = DesktopCapabilities().searchWorkspace(root, "agent")
        assertEquals(1, hits.size)
        assertEquals("notes.md", hits.first().path)
        assertEquals(2, hits.first().line)
    }

    @Test
    fun approvedCommandCapturesExitCodeAndOutput() {
        val root = Files.createTempDirectory("aura-command")
        val result = DesktopCapabilities().runApprovedCommand(root, "java", "-version")
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("version") || result.output.contains("Version"))
    }

    @Test
    fun projectBuildIntelligenceDetectsGradleWrapper() {
        val root = Files.createTempDirectory("aura-build")
        Files.writeString(root.resolve("gradlew"), "wrapper")
        val build = ProjectIntelligence().recommendedBuild(root)
        assertTrue(build.command.isNotEmpty())
        assertTrue(build.rationale.contains("Gradle"))
    }

    @Test
    fun fileIntelligenceFindsFilesByName() {
        val root = Files.createTempDirectory("aura-files")
        Files.writeString(root.resolve("architecture.md"), "AURA")
        val matches = DesktopCapabilities().findFiles(root, "arch")
        assertEquals(1, matches.size)
        assertEquals("architecture.md", matches.first().path)
    }
    @Test
    fun workspaceStatePersistsAndRestores() {
        val state = WorkspaceState()
        val path = Files.createTempDirectory("aura-workspace").toAbsolutePath().toString()
        state.save(path)
        assertEquals(path, state.load())
        state.clear()
    }

}

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
}

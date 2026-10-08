package com.aura.desktop

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

data class WorkspaceEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val sizeBytes: Long
)

data class GitWorkspaceStatus(
    val root: String,
    val branch: String,
    val status: String,
    val recentCommits: List<String>
)

class DesktopCapabilities {
    fun inspectFolder(folder: Path, limit: Int = 80): List<WorkspaceEntry> {
        require(Files.isDirectory(folder)) { "Folder tidak ditemukan: $folder" }
        return Files.list(folder).use { stream ->
            stream.sorted(compareBy<Path> { !Files.isDirectory(it) }.thenBy { it.fileName.toString().lowercase() })
                .limit(limit.toLong())
                .map {
                    WorkspaceEntry(
                        name = it.fileName.toString(),
                        path = it.toAbsolutePath().toString(),
                        directory = Files.isDirectory(it),
                        sizeBytes = if (Files.isRegularFile(it)) runCatching { Files.size(it) }.getOrDefault(0L) else 0L
                    )
                }.toList()
        }
    }

    fun readTextFile(file: Path, maxChars: Int = 20000): String {
        require(Files.isRegularFile(file)) { "File tidak ditemukan: $file" }
        val allowed = setOf("txt", "md", "json", "xml", "yaml", "yml", "toml", "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "html", "css", "sql", "csv", "gradle", "properties")
        val ext = file.fileName.toString().substringAfterLast('.', "").lowercase()
        require(ext in allowed) { "Format .$ext belum didukung untuk pembacaan teks aman." }
        return Files.readString(file).take(maxChars)
    }

    fun openPath(path: Path): Boolean =
        runCatching {
            if (!Desktop.isDesktopSupported()) return false
            Desktop.getDesktop().open(path.toFile())
            true
        }.getOrDefault(false)

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(
            java.awt.datatransfer.StringSelection(text), null
        )
    }

    fun readClipboard(): String? =
        runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) null
            else clipboard.getData(DataFlavor.stringFlavor) as String
        }.getOrNull()

    fun openApplication(executableOrCommand: String): Boolean =
        runCatching {
            ProcessBuilder(executableOrCommand.split(" ").filter { it.isNotBlank() }).start()
            true
        }.getOrDefault(false)

    fun gitStatus(repo: Path): GitWorkspaceStatus {
        require(Files.isDirectory(repo.resolve(".git"))) { "Folder ini bukan Git repository." }
        val root = command(repo.toFile(), "rev-parse", "--show-toplevel").trim()
        val branch = command(repo.toFile(), "branch", "--show-current").trim().ifBlank { "detached" }
        val status = command(repo.toFile(), "status", "--short").trim().ifBlank { "clean" }
        val commits = command(repo.toFile(), "log", "-5", "--pretty=format:%h %s").lines().filter { it.isNotBlank() }
        return GitWorkspaceStatus(root, branch, status, commits)
    }

    private fun command(directory: File, vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(directory)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output.trim().ifBlank { "Perintah git gagal." } }
        return output
    }
}

package com.aura.desktop

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

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

data class SearchResult(
    val path: String,
    val line: Int,
    val preview: String
)

data class CommandResult(
    val exitCode: Int,
    val output: String
)

data class FileMatch(
    val path: String,
    val sizeBytes: Long,
    val extension: String
)

data class TextDocument(
    val name: String,
    val extension: String,
    val content: String
)

class DesktopCapabilities {
    // CI trigger: validated desktop capability surface.
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

    fun findFiles(root: Path, query: String, limit: Int = 80): List<FileMatch> {
        require(Files.isDirectory(root)) { "Workspace tidak ditemukan: $root" }
        require(query.isNotBlank()) { "Query pencarian kosong." }
        val needle = query.lowercase()
        val matches = mutableListOf<FileMatch>()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { !it.toString().contains(java.io.File.separator + ".git" + java.io.File.separator) }
                .forEach { file ->
                    if (matches.size < limit && file.fileName.toString().lowercase().contains(needle)) {
                        matches += FileMatch(
                            root.relativize(file).toString(),
                            runCatching { Files.size(file) }.getOrDefault(0L),
                            file.fileName.toString().substringAfterLast('.', "")
                        )
                    }
                }
        }
        return matches
    }

    fun readTextFile(file: Path, maxChars: Int = 20000): String {
        require(Files.isRegularFile(file)) { "File tidak ditemukan: $file" }
        val allowed = setOf("txt", "md", "json", "xml", "yaml", "yml", "toml", "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "html", "css", "sql", "csv", "gradle", "properties")
        val ext = file.fileName.toString().substringAfterLast('.', "").lowercase()
        require(ext in allowed) { "Format .$ext belum didukung untuk pembacaan teks aman." }
        return Files.readString(file).take(maxChars)
    }

    fun readDocument(file: Path, maxChars: Int = 30000): TextDocument {
        val content = readTextFile(file, maxChars)
        return TextDocument(file.fileName.toString(), file.fileName.toString().substringAfterLast('.', ""), content)
    }

    fun searchWorkspace(root: Path, query: String, limit: Int = 40): List<SearchResult> {
        require(Files.isDirectory(root)) { "Workspace tidak ditemukan: $root" }
        require(query.isNotBlank()) { "Query pencarian kosong." }
        val needle = query.lowercase()
        val results = mutableListOf<SearchResult>()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { !it.toString().contains(File.separator + ".git" + File.separator) }
                .filter { it.fileName.toString().substringAfterLast('.', "").lowercase() in
                    setOf("txt","md","json","xml","yaml","yml","toml","kt","kts","java","py","js","ts","tsx","jsx","html","css","sql","csv","gradle","properties") }
                .forEach { file ->
                    if (results.size < limit) runCatching {
                        Files.readAllLines(file).forEachIndexed { index, line ->
                            if (results.size < limit && line.lowercase().contains(needle)) {
                                results += SearchResult(root.relativize(file).toString(), index + 1, line.trim().take(240))
                            }
                        }
                    }
                }
        }
        return results
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
            val parts = parseCommandLine(executableOrCommand)
            require(parts.isNotEmpty()) { "Perintah kosong." }
            ProcessBuilder(parts).start()
            true
        }.getOrDefault(false)

    fun gitDiff(repo: Path): String {
        require(Files.isDirectory(repo.resolve(".git"))) { "Folder ini bukan Git repository." }
        return command(repo.toFile(), "diff", "--stat", "--", ".").trim().ifBlank { "Tidak ada perubahan tracked." }
    }

    fun runApprovedCommand(directory: Path, vararg args: String): CommandResult {
        require(args.isNotEmpty()) { "Perintah kosong." }
        require(Files.isDirectory(directory)) { "Working directory tidak ditemukan." }
        val process = ProcessBuilder(args.toList())
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().take(30000)
        return CommandResult(process.waitFor(), output)
    }

    fun gitStatus(repo: Path): GitWorkspaceStatus {
        require(Files.isDirectory(repo.resolve(".git"))) { "Folder ini bukan Git repository." }
        val root = command(repo.toFile(), "rev-parse", "--show-toplevel").trim()
        val branch = command(repo.toFile(), "branch", "--show-current").trim().ifBlank { "detached" }
        val status = command(repo.toFile(), "status", "--short").trim().ifBlank { "clean" }
        val commits = command(repo.toFile(), "log", "-5", "--pretty=format:%h %s").lines().filter { it.isNotBlank() }
        return GitWorkspaceStatus(root, branch, status, commits)
    }

    private fun parseCommandLine(command: String): List<String> =
        Regex("""[^\s"']+|"[^"]*"|'[^']*'""").findAll(command).map { it.value.trim('"', '\'') }.toList()

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

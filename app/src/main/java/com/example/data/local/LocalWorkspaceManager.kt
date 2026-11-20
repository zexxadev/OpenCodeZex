package com.example.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.FileContentResponse
import com.example.data.model.FileNode
import com.example.data.model.GrepMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class LocalWorkspaceManager(
    private val context: Context
) {
    val workspaceDir: File
        get() = File(context.filesDir, "opencode_workspace")

    val stateDir: File
        get() = File(context.filesDir, "opencode_local_state")

    fun ensureEnvironmentInitialized(): String {
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
        }
        if (!stateDir.exists()) {
            stateDir.mkdirs()
        }
        val agentsFile = File(workspaceDir, "AGENTS.md")
        if (!agentsFile.exists() && (workspaceDir.listFiles()?.isEmpty() != false)) {
            agentsFile.writeText(
                """
                # OpenCode Local Android Workspace

                Workspace ini berjalan langsung di penyimpanan lokal aplikasi Android tanpa Termux atau PC.
                Agen dapat membaca, menulis, mengedit, mencari file, dan menjalankan perintah shell aman di dalam folder ini.
                """.trimIndent() + "\n"
            )
            val srcDir = File(workspaceDir, "src")
            srcDir.mkdirs()
            File(srcDir, "hello.kt").writeText(
                """
                package workspace

                fun greet(name: String): String {
                    return "Halo, ${'$'}name dari OpenCode Android!"
                }
                """.trimIndent() + "\n"
            )
        }
        return workspaceDir.absolutePath
    }

    suspend fun listFiles(relPath: String): List<FileNode> = withContext(Dispatchers.IO) {
        ensureEnvironmentInitialized()
        val targetDir = resolveSafeFile(relPath)
        if (!targetDir.exists() || !targetDir.isDirectory) return@withContext emptyList()
        val rootCanonical = workspaceDir.canonicalPath
        targetDir.listFiles()
            ?.filter { !it.name.startsWith(".opencode_internal") }
            ?.map { file ->
                val rel = file.canonicalPath.removePrefix(rootCanonical).trimStart(File.separatorChar)
                FileNode(
                    name = file.name,
                    path = rel,
                    absolute = file.absolutePath,
                    type = if (file.isDirectory) "directory" else "file",
                    ignored = file.name.startsWith(".")
                )
            }
            ?.sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    suspend fun readFile(relPath: String): FileContentResponse = withContext(Dispatchers.IO) {
        ensureEnvironmentInitialized()
        val target = resolveSafeFile(relPath)
        if (!target.exists() || !target.isFile) {
            throw IllegalArgumentException("File tidak ditemukan: $relPath")
        }
        if (target.length() > 512_000) {
            return@withContext FileContentResponse(
                type = "text",
                content = target.readText().take(200_000) + "\n... [file dipotong karena > 500KB]"
            )
        }
        FileContentResponse(type = "text", content = target.readText())
    }

    suspend fun writeFile(relPath: String, content: String): Pair<String, String> = withContext(Dispatchers.IO) {
        ensureEnvironmentInitialized()
        val target = resolveSafeFile(relPath)
        val before = if (target.exists() && target.isFile) target.readText() else ""
        target.parentFile?.mkdirs()
        target.writeText(content)
        before to content
    }

    suspend fun editFile(relPath: String, oldString: String, newString: String): Pair<String, String> = withContext(Dispatchers.IO) {
        ensureEnvironmentInitialized()
        val target = resolveSafeFile(relPath)
        if (!target.exists() || !target.isFile) {
            throw IllegalArgumentException("File tidak ditemukan: $relPath")
        }
        val before = target.readText()
        if (!before.contains(oldString)) {
            throw IllegalArgumentException("Teks target tidak ditemukan di $relPath")
        }
        val after = before.replaceFirst(oldString, newString)
        target.writeText(after)
        before to after
    }

    suspend fun findFiles(query: String): List<String> = withContext(Dispatchers.IO) {
        ensureEnvironmentInitialized()
        val rootCanonical = workspaceDir.canonicalPath
        val q = query.trim().lowercase()
        workspaceDir.walkTopDown()
            .filter { it.isFile }
            .map { it.canonicalPath.removePrefix(rootCanonical).trimStart(File.separatorChar) }
            .filter { q.isEmpty() || it.lowercase().contains(q) }
            .take(50)
            .toList()
    }

    suspend fun grepText(pattern: String): List<GrepMatch> = withContext(Dispatchers.IO) {
        ensureEnvironmentInitialized()
        val clean = pattern.trim()
        if (clean.isEmpty()) return@withContext emptyList()
        val regex = runCatching { Regex(clean, RegexOption.IGNORE_CASE) }.getOrNull()
        val rootCanonical = workspaceDir.canonicalPath
        val matches = mutableListOf<GrepMatch>()
        workspaceDir.walkTopDown()
            .filter { it.isFile && it.length() < 256_000 }
            .forEach { file ->
                val rel = file.canonicalPath.removePrefix(rootCanonical).trimStart(File.separatorChar)
                file.useLines { seq ->
                    seq.forEachIndexed { idx, line ->
                        val hit = regex?.containsMatchIn(line) ?: line.contains(clean, ignoreCase = true)
                        if (hit) {
                            matches.add(GrepMatch(path = rel, lineNumber = idx + 1, lineText = line))
                            if (matches.size >= 80) return@withContext matches
                        }
                    }
                }
            }
        matches
    }

    suspend fun importFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        ensureEnvironmentInitialized()
        val name = resolveDisplayName(uri) ?: "imported_${System.currentTimeMillis()}.txt"
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dest = resolveSafeFile(safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Gagal membaca file terpilih")
        safeName
    }

    suspend fun runLocalShell(command: String): ShellRunResult = withContext(Dispatchers.IO) {
        ensureEnvironmentInitialized()
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(workspaceDir)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        if (!finished) {
            process.destroyForcibly()
            ShellRunResult(-1, "$output\n[Perintah dihentikan karena melebihi batas waktu 10 detik]")
        } else {
            ShellRunResult(process.exitValue(), output.ifBlank { "(exit ${process.exitValue()})" })
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun resolveSafeFile(relPath: String): File {
        val clean = relPath.trim().trimStart('/')
        val candidate = if (clean.isEmpty()) workspaceDir else File(workspaceDir, clean)
        val rootPath = workspaceDir.canonicalPath
        val targetPath = candidate.canonicalPath
        if (targetPath != rootPath && !targetPath.startsWith("$rootPath${File.separator}")) {
            throw SecurityException("Akses path di luar workspace ditolak: $relPath")
        }
        return candidate
    }
}

data class ShellRunResult(
    val exitCode: Int,
    val output: String
)

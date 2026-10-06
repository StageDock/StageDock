package com.stagedock.app.install

import android.os.Environment
import android.util.Log
import com.stagedock.app.data.Stage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

class StageInstaller(private val client: OkHttpClient, private val cacheDir: File) {

    val stagesDir: File
        get() = File(Environment.getExternalStorageDirectory(), "SynthRidersUC/CustomStages")

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    suspend fun installedFileNames(): Set<String> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptySet()
        stagesDir.listFiles { f -> f.isFile && f.extension.equals(EXT, true) }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
    }

    suspend fun remove(fileName: String): Boolean = withContext(Dispatchers.IO) {
        File(stagesDir, sanitize(fileName)).takeIf { it.isFile }?.delete() ?: false
    }

    suspend fun install(stage: Stage, onProgress: (Float) -> Unit): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!hasAccess()) throw IOException("All files access not granted")
                if (!stagesDir.exists() && !stagesDir.mkdirs()) throw IOException("Cannot create ${stagesDir.path}")

                val temp = File.createTempFile("stage_", ".tmp", cacheDir)
                try {
                    val resolvedName = download(stage, temp, onProgress)
                    place(temp, resolvedName)
                } finally {
                    temp.delete()
                }
            }
        }

    private suspend fun download(stage: Stage, target: File, onProgress: (Float) -> Unit): String {
        val failures = mutableListOf<String>()
        for (url in stage.downloadUrls) {
            try {
                return downloadFrom(url, stage, target, onProgress)
            } catch (e: HttpFailure) {
                Log.w(TAG, "Download failed for stage ${stage.id}: ${e.message}")
                failures += e.message.orEmpty()
            }
        }
        throw IOException(failures.joinToString("\n").ifBlank { "No Quest (.$EXT) file for this stage" })
    }

    private class HttpFailure(message: String) : IOException(message)

    private suspend fun downloadFrom(url: String, stage: Stage, target: File, onProgress: (Float) -> Unit): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream, application/zip, */*")
            .header("Referer", "https://synthriderz.com/stages")
            .build()
        Log.d(TAG, "GET $url")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val snippet = runCatching { response.peekBody(300).string() }.getOrDefault("")
                    .replace(Regex("\\s+"), " ").trim()
                throw HttpFailure("HTTP ${response.code} from $url${if (snippet.isNotEmpty()) " — $snippet" else ""}")
            }
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.contains("text/html", true) || contentType.contains("application/json", true)) {
                val snippet = runCatching { response.peekBody(300).string() }.getOrDefault("").replace(Regex("\\s+"), " ").trim()
                throw HttpFailure("Got $contentType instead of a file from $url — $snippet")
            }
            val body = response.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: stage.sizeBytes ?: -1L
            var read = 0L
            var lastReported = -1
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress((pct / 100f).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (read == 0L) throw IOException("Downloaded file is empty")
            return nameFor(stage, response.header("Content-Disposition"), response.request.url.encodedPath)
        }
    }

    private fun nameFor(stage: Stage, disposition: String?, path: String): String {
        val raw = stage.fileName
            ?: dispositionName(disposition)
            ?: path.substringAfterLast('/').takeIf { it.contains('.') }?.let { URLDecoder.decode(it, "UTF-8") }
            ?: stage.name
        return sanitize(raw)
    }

    private fun dispositionName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val parts = header.split(';').map { it.trim() }
        parts.firstOrNull { it.startsWith("filename*=", true) }?.let { part ->
            val value = part.substringAfter('=').substringAfter("''")
            return runCatching { URLDecoder.decode(value.trim('"'), "UTF-8") }.getOrNull()
        }
        return parts.firstOrNull { it.startsWith("filename=", true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
    }

    private fun sanitize(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .trimStart('.')
            .ifBlank { "stage" }

    private fun place(temp: File, resolvedName: String): List<String> {
        val isStageName = resolvedName.endsWith(".$EXT", true)
        if (!isStageName && isZip(temp)) return extractStages(temp)
        if (resolvedName.endsWith(".stage", true)) throw IOException("Server sent the PC .stage file, not the Quest .$EXT")
        val finalName = if (isStageName) resolvedName else "${resolvedName.substringBeforeLast('.')}.$EXT"
        temp.inputStream().use { writeAtomically(finalName) { out -> it.copyTo(out) } }
        return listOf(finalName)
    }

    private fun writeAtomically(fileName: String, write: (java.io.OutputStream) -> Unit) {
        val destination = File(stagesDir, fileName)
        val partial = File(stagesDir, "$fileName.part")
        try {
            partial.outputStream().use(write)
            if (destination.exists() && !destination.delete()) throw IOException("Cannot replace $fileName")
            if (!partial.renameTo(destination)) throw IOException("Cannot finalise $fileName")
        } finally {
            partial.delete()
        }
    }

    private fun isZip(file: File): Boolean =
        file.inputStream().use { it.read() == 'P'.code && it.read() == 'K'.code }

    private fun extractStages(zip: File): List<String> {
        val written = mutableListOf<String>()
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = sanitize(entry.name)
                if (entry.isDirectory || !name.endsWith(".$EXT", true)) continue
                writeAtomically(name) { out -> zis.copyTo(out) }
                written += name
            }
        }
        if (written.isEmpty()) throw IOException("Archive contained no .$EXT files")
        return written
    }

    private companion object {
        const val EXT = "stagedroid"
        const val TAG = "StageDock"
    }
}

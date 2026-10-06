package com.stagedock.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

enum class StageSort(val label: String, val param: String) {
    Top("Top", "score,DESC"),
    Newest("Newest", "published_at,DESC"),
    Downloads("Most downloaded", "download_count,DESC"),
}

object ApiConfig {
    const val BASE = "https://synthriderz.com"
    const val STAGES_PATH = "/api/models/stages"
    const val PAGE_SIZE = 24

    val SELECT = listOf(
        "id", "name", "description",
        "user.id", "user.username",
        "download_url", "cover_url", "cover_version",
        "published_at", "download_count",
        "upvote_count", "downvote_count", "vote_diff", "score", "rating",
    ).joinToString(",")

    val JOINS = listOf("files", "files.file")

    fun filter(query: String): String = buildJsonObject {
        put("\$and", buildJsonArray {
            if (query.isNotBlank()) {
                add(buildJsonObject {
                    put("\$or", buildJsonArray {
                        add(buildJsonObject { put("name", buildJsonObject { put("\$contL", query.trim()) }) })
                        add(buildJsonObject { put("user.username", buildJsonObject { put("\$contL", query.trim()) }) })
                    })
                })
            }
        })
    }.toString()
}

data class Stage(
    val id: String,
    val name: String,
    val author: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val downloadUrls: List<String>,
    val fileName: String?,
    val sizeBytes: Long?,
    val downloadCount: Long?,
    val upvotes: Long?,
    val questAvailable: Boolean,
)

data class StagePage(val stages: List<Stage>, val page: Int, val pageCount: Int?)

class StageApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var loggedShape = false

    suspend fun fetchPage(page: Int, query: String, sort: StageSort): StagePage = withContext(Dispatchers.IO) {
        val url = (ApiConfig.BASE + ApiConfig.STAGES_PATH).toHttpUrl().newBuilder()
            .addQueryParameter("select", ApiConfig.SELECT)
            .apply { ApiConfig.JOINS.forEach { addQueryParameter("join[]", it) } }
            .addQueryParameter("limit", ApiConfig.PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", sort.param)
            .addQueryParameter("s", ApiConfig.filter(query))
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Stage list failed: HTTP ${response.code}")
            val body = response.body?.string() ?: error("Empty response")
            parse(json.parseToJsonElement(body), page)
        }
    }

    private fun parse(root: JsonElement, requestedPage: Int): StagePage {
        val obj = root as? JsonObject
        val items = (obj?.get("data") as? JsonArray) ?: (root as? JsonArray) ?: JsonArray(emptyList())
        logShapeOnce(items)
        return StagePage(
            stages = items.mapNotNull { (it as? JsonObject)?.let(::toStage) },
            page = obj?.int("page") ?: requestedPage,
            pageCount = obj?.int("pageCount"),
        )
    }

    private fun toStage(o: JsonObject): Stage? {
        val id = o.str("id") ?: return null
        val questFile = findQuestFile(o["files"])
        val cover = o.str("cover_url")?.let(::absolute)?.let { url ->
            val v = o.str("cover_version")
            if (v != null) "$url${if ('?' in url) '&' else '?'}v=$v" else url
        }
        val base = (o.str("download_url")?.let(::absolute) ?: "${ApiConfig.BASE}${ApiConfig.STAGES_PATH}/$id/download")
            .substringBefore('?')
        return Stage(
            id = id,
            name = o.str("name") ?: "Stage $id",
            author = (o["user"] as? JsonObject)?.str("username"),
            description = o.str("description"),
            thumbnailUrl = cover,
            downloadUrls = questFile?.ids.orEmpty().map { fileId -> "$base?file_id=$fileId" },
            fileName = questFile?.name,
            sizeBytes = questFile?.size,
            downloadCount = o.long("download_count"),
            upvotes = o.long("upvote_count"),
            questAvailable = questFile != null,
        )
    }

    private data class QuestFile(val name: String, val size: Long?, val ids: List<String>)

    private fun findQuestFile(files: JsonElement?): QuestFile? {
        val entries = (files as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
        for (entry in entries) {
            val file = entry["file"] as? JsonObject
            val name = listOfNotNull(file, entry)
                .flatMap { obj -> NAME_KEYS.mapNotNull { obj.str(it) } }
                .map { it.substringBefore('?').substringAfterLast('/') }
                .firstOrNull { it.endsWith(".$QUEST_EXT", true) }
                ?: continue
            val ids = listOfNotNull(entry.str("file_id"), file?.str("id"), entry.str("id")).distinct()
            if (ids.isEmpty()) continue
            val size = listOfNotNull(file, entry).firstNotNullOfOrNull { obj -> SIZE_KEYS.firstNotNullOfOrNull { obj.long(it) } }
            return QuestFile(name, size, ids)
        }
        return null
    }

    private fun logShapeOnce(items: JsonArray) {
        if (loggedShape) return
        val first = items.firstOrNull() as? JsonObject ?: return
        loggedShape = true
        Log.d(TAG, "Stage item keys: ${first.keys}")
        Log.d(TAG, "First stage raw: ${first.toString().take(2000)}")
    }

    private fun absolute(value: String) = when {
        value.startsWith("http") -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> ApiConfig.BASE + value
        else -> "${ApiConfig.BASE}/$value"
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private companion object {
        const val TAG = "StageDock"
        const val QUEST_EXT = "stagedroid"
        val NAME_KEYS = listOf("filename", "original_filename", "name", "path", "key")
        val SIZE_KEYS = listOf("size", "filesize", "file_size")
    }
}

package com.skmtkytr.myhealthbuddy.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class ChatTurn(val role: String, val content: String)

class ClaudeClient(
    private val baseUrl: String,
    private val apiKey: String?,
    private val model: String,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val compactJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prettyJson = Json {
        ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true
    }

    suspend fun send(
        system: String,
        history: List<ChatTurn>,
        maxTokens: Int = 1024,
        onRequestBuilt: (String) -> Unit = {},
    ): String =
        withContext(Dispatchers.IO) {
            val body = MessagesRequest(
                model = model,
                maxTokens = maxTokens,
                system = system,
                messages = history.map { ApiMessage(it.role, it.content) },
            )
            val wireBody = compactJson.encodeToString(body)
            val prettyBody = prettyJson.encodeToString(body)
            onRequestBuilt(prettyBody)
            logChunked("MhbClaudeReq", prettyBody)
            val builder = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/messages")
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(wireBody.toRequestBody(JSON_MEDIA))
            if (!apiKey.isNullOrBlank()) builder.addHeader("x-api-key", apiKey)
            client.newCall(builder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                logChunked("MhbClaudeResp", "HTTP ${resp.code}\n$raw")
                if (!resp.isSuccessful) throw IOException("API ${resp.code}: $raw")
                val parsed = compactJson.decodeFromString(MessagesResponse.serializer(), raw)
                parsed.content.firstOrNull { it.type == "text" }?.text.orEmpty()
            }
        }

    @Serializable
    private data class MessagesRequest(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int,
        val system: String,
        val messages: List<ApiMessage>,
    )

    @Serializable
    private data class ApiMessage(val role: String, val content: String)

    @Serializable
    private data class MessagesResponse(
        val id: String? = null,
        val type: String? = null,
        val role: String? = null,
        val model: String? = null,
        val content: List<ContentBlock> = emptyList(),
        @SerialName("stop_reason") val stopReason: String? = null,
    )

    @Serializable
    private data class ContentBlock(val type: String, val text: String? = null)

    private fun logChunked(tag: String, message: String) {
        val chunkSize = 3500
        var i = 0
        var part = 1
        val total = (message.length + chunkSize - 1) / chunkSize
        while (i < message.length) {
            val end = minOf(i + chunkSize, message.length)
            Log.d(tag, "[$part/$total] ${message.substring(i, end)}")
            i = end
            part++
        }
        if (message.isEmpty()) Log.d(tag, "(empty)")
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

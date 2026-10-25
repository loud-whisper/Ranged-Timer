package com.loudwhisper.rangedtimer.core

import kotlinx.serialization.json.Json

object Codec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(data: AppData): String = json.encodeToString(AppData.serializer(), data)

    fun decode(text: String): Result<AppData> = runCatching {
        val data = json.decodeFromString(AppData.serializer(), text)
        require(data.schemaVersion == SCHEMA_VERSION) { "Unsupported file version ${data.schemaVersion}." }
        require(data.sessions.all { it.pickedSeconds >= 0 && it.actualSeconds >= 0 }) { "File contains negative session lengths." }
        data
    }
}

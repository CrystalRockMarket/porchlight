package it.kituwa.stackmate.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.intOrNullCompat(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

internal fun JsonObject.longOrNullCompat(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

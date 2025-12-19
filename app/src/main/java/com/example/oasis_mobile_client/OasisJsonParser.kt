package com.example.oasis_mobile_client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object OasisJsonParser {
    fun formatUciProposal(el: JsonElement?): String? {
        if (el == null) return null
        val obj = el.jsonObject
        val notify = obj["uci_notify"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!notify) return null
        val list = obj["uci_list"]?.jsonObject ?: return "UCI提案があります。"
        fun linesOf(name: String): List<String> {
            val arr = list[name]?.jsonArray ?: return emptyList()
            return arr.mapNotNull { item ->
                val o = item.jsonObject
                o["param"]?.jsonPrimitive?.content
            }
        }
        val parts = mutableListOf<String>()
        val sections = listOf("set","add","delete","add_list","del_list","reorder")
        for (s in sections) {
            val lines = linesOf(s)
            if (lines.isNotEmpty()) {
                parts.add("$s:\n" + lines.joinToString("\n") { "  $it" })
            }
        }
        if (parts.isEmpty()) return "UCI提案があります。"
        return "UCI提案:\n" + parts.joinToString("\n")
    }

    fun parseToolLabel(el: JsonElement?): String? {
        if (el == null) return null
        return runCatching {
            val obj = when {
                (el is kotlinx.serialization.json.JsonPrimitive) && el.isString ->
                    runCatching { Json.parseToJsonElement(el.content).jsonObject }.getOrNull()
                else -> el.jsonObject
            } ?: return@runCatching null

            if (obj.isEmpty()) return@runCatching null

            obj["name"]?.jsonPrimitive?.content
                ?: obj["tool"]?.jsonPrimitive?.content
                ?: obj["tools"]?.jsonArray?.mapNotNull {
                    runCatching { it.jsonObject["name"]?.jsonPrimitive?.content ?: it.jsonPrimitive.content }.getOrNull()
                }?.filter { it.isNotBlank() }?.joinToString(", ")?.ifBlank { null }
                ?: obj["tool_outputs"]?.jsonArray?.mapNotNull {
                    it.jsonObject["name"]?.jsonPrimitive?.content
                }?.filter { it.isNotBlank() }?.joinToString(", ")?.ifBlank { null }
        }.getOrNull()
    }

    fun extractToolNamesFromContentIfJson(text: String): String? {
        return runCatching {
            val trimmed = text.trimStart()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
            val root = Json.parseToJsonElement(text)
            val o = root.jsonObject
            o["tool_outputs"]?.jsonArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content
            }?.filter { it.isNotBlank() }?.joinToString(", ")?.takeIf { it.isNotBlank() }
                ?: o["tools"]?.jsonArray?.mapNotNull {
                    runCatching { it.jsonObject["name"]?.jsonPrimitive?.content ?: it.jsonPrimitive.content }.getOrNull()
                }?.filter { it.isNotBlank() }?.joinToString(", ")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

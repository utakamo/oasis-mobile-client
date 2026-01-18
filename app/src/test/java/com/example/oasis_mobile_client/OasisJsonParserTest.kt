package com.example.oasis_mobile_client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OasisJsonParserTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun parseToolLabel_extractsNameFromSimpleObject() {
        val input = "{\"name\": \"wifi_scan\", \"arguments\": {}}"
        val element = json.parseToJsonElement(input)
        
        val result = OasisJsonParser.parseToolLabel(element)
        
        assertEquals("wifi_scan", result)
    }

    @Test
    fun parseToolLabel_extractsToolField() {
        val input = "{\"tool\": \"reboot_system\"}"
        val element = json.parseToJsonElement(input)
        
        val result = OasisJsonParser.parseToolLabel(element)
        
        assertEquals("reboot_system", result)
    }

    @Test
    fun parseToolLabel_extractsFromToolsArray() {
        val input = "{\"tools\": [{\"name\": \"tool1\"}, {\"name\": \"tool2\"}]}"
        val element = json.parseToJsonElement(input)
        
        val result = OasisJsonParser.parseToolLabel(element)
        
        assertEquals("tool1, tool2", result)
    }

    @Test
    fun parseToolLabel_extractsFromToolOutputs() {
        val input = "{\"tool_outputs\": [{\"name\": \"output1\"}, {\"name\": \"output2\"}]}"
        val element = json.parseToJsonElement(input)
        
        val result = OasisJsonParser.parseToolLabel(element)
        
        assertEquals("output1, output2", result)
    }

    @Test
    fun parseToolLabel_returnsNullForInvalidInput() {
        val input = "{\"other\": \"value\"}"
        val element = json.parseToJsonElement(input)
        
        val result = OasisJsonParser.parseToolLabel(element)
        
        assertNull(result)
    }

    @Test
    fun parseToolLabel_handlesStringInput() {
        // Sometimes the tool info comes as a JSON string inside a JsonPrimitive
        val innerJson = "{\"name\": \"string_tool\"}"
        val element = json.parseToJsonElement("\"${innerJson.replace("\"", "\\\"")}\"") // Escaped JSON string
        
        val result = OasisJsonParser.parseToolLabel(element)
        
        assertEquals("string_tool", result)
    }

    @Test
    fun formatUciProposal_returnsNullIfNotifyFalse() {
        val input = "{\"uci_notify\": false, \"uci_list\": {}}"
        val element = json.parseToJsonElement(input)
        
        val result = OasisJsonParser.formatUciProposal(element)
        
        assertNull(result)
    }

    @Test
    fun formatUciProposal_formatsCorrectly() {
        val input = "{\"uci_notify\": true, \"uci_list\": {\"set\": [{\"param\": \"wireless.radio0.disabled=0\"}, {\"param\": \"network.lan.ipaddr=192.168.1.1\"}], \"delete\": [{\"param\": \"firewall.@rule[0]\"}]}}"
        val element = json.parseToJsonElement(input)
        
        val result = OasisJsonParser.formatUciProposal(element)
        
        val expected = """
            UCI提案:
            set:
              wireless.radio0.disabled=0
              network.lan.ipaddr=192.168.1.1
            delete:
              firewall.@rule[0]
        """.trimIndent()
        
        assertEquals(expected, result)
    }
}

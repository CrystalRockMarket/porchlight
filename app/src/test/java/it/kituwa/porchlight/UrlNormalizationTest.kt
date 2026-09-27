package it.kituwa.porchlight

import it.kituwa.porchlight.data.Http
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlNormalizationTest {

    private val http = Http()

    @Test
    fun bareHostGetsHttpsAndNoTrailingSlash() {
        assertEquals("https://grafana.example.com", http.normalizeUrl("grafana.example.com"))
    }

    @Test
    fun trailingSlashIsRemoved() {
        assertEquals("https://g.example.com", http.normalizeUrl("https://g.example.com/"))
    }

    @Test
    fun explicitHttpIsPreservedForSelfHostedServers() {
        assertEquals("http://192.168.1.10:3000", http.normalizeUrl("http://192.168.1.10:3000/"))
    }

    @Test
    fun subPathIsPreserved() {
        assertEquals("https://example.com/grafana", http.normalizeUrl("https://example.com/grafana/"))
    }
}

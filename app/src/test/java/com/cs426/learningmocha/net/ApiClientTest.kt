package com.cs426.learningmocha.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiClientTest {

    @Test
    fun addsTheTrailingSlashRetrofitNeeds() {
        assertEquals(
            "http://192.168.1.5:8787/",
            ApiClient.normalizeBaseUrl("http://192.168.1.5:8787"),
        )
    }

    @Test
    fun leavesAnAlreadyNormalUrlAlone() {
        assertEquals(
            "http://192.168.1.5:8787/",
            ApiClient.normalizeBaseUrl("http://192.168.1.5:8787/"),
        )
    }

    @Test
    fun assumesHttpWhenTheSchemeIsMissing() {
        assertEquals(
            "http://192.168.1.5:8787/",
            ApiClient.normalizeBaseUrl("192.168.1.5:8787"),
        )
        assertEquals("http://localhost:8787/", ApiClient.normalizeBaseUrl("  localhost:8787  "))
    }

    @Test
    fun keepsAPathPrefix() {
        assertEquals(
            "https://mocha.example.com/api/",
            ApiClient.normalizeBaseUrl("https://mocha.example.com/api"),
        )
    }

    @Test
    fun blankMeansTheBuiltInDefault() {
        assertEquals(ApiClient.DEFAULT_BASE_URL, ApiClient.normalizeBaseUrl(""))
        assertEquals(ApiClient.DEFAULT_BASE_URL, ApiClient.normalizeBaseUrl("   "))
    }

    @Test
    fun rejectsWhatCannotBeAGateway() {
        assertNull(ApiClient.normalizeBaseUrl("not a url"))
        assertNull(ApiClient.normalizeBaseUrl("http://"))
        assertNull(ApiClient.normalizeBaseUrl("ftp://mocha.example.com/"))
    }

    @Test
    fun emulatorEndpointsAreUnchanged() {
        assertEquals(
            "http://10.0.2.2:8787/v1/chat/stream",
            ApiClient.endpoint(ApiClient.DEFAULT_BASE_URL, "v1/chat/stream").toString(),
        )
    }

    @Test
    fun endpointsHangUnderAPathPrefix() {
        assertEquals(
            "https://mocha.example.com/api/v1/chat/stream",
            ApiClient.endpoint("https://mocha.example.com/api/", "v1/chat/stream").toString(),
        )
    }

    @Test
    fun unparseableBaseFallsBackToTheDefaultGateway() {
        assertEquals(
            "http://10.0.2.2:8787/v1/chat/stream",
            ApiClient.endpoint("192.168.1.5:8787", "v1/chat/stream").toString(),
        )
    }
}

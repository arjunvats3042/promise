package app.promise.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorParserTest {
    private val json = NetworkJson.json

    @Test
    fun parsesEnvelopeCode() {
        val error = ApiErrorParser.parse(
            status = 401,
            body = """{"error":{"code":"AUTHENTICATION_FAILED","message":"Authentication failed."}}""",
            retryAfterHeader = null,
            json = json,
        )
        assertEquals(401, error.status)
        assertEquals("AUTHENTICATION_FAILED", error.code)
        assertNull(error.retryAfterSeconds)
    }

    @Test
    fun mapsStatusFallbacks() {
        assertEquals("NOT_FOUND", ApiErrorParser.parse(404, null, null, json).code)
        assertEquals("CONFLICT", ApiErrorParser.parse(409, "{}", null, json).code)
        assertEquals("RATE_LIMITED", ApiErrorParser.parse(429, null, "12", json).code)
        assertEquals("INTERNAL_SERVER_ERROR", ApiErrorParser.parse(500, null, null, json).code)
        assertEquals("UNAUTHENTICATED", ApiErrorParser.parse(401, null, null, json).code)
    }

    @Test
    fun parsesRetryAfter() {
        assertEquals(30, ApiErrorParser.parseRetryAfter("30"))
        assertNull(ApiErrorParser.parseRetryAfter("abc"))
        assertNull(ApiErrorParser.parseRetryAfter(null))
        val error = ApiErrorParser.parse(429, """{"error":{"code":"RATE_LIMITED","message":"x"}}""", "7", json)
        assertEquals(7, error.retryAfterSeconds)
        assertTrue(error.status == 429)
    }

    @Test
    fun doesNotUseMessageAsCode() {
        val error = ApiErrorParser.parse(
            status = 500,
            body = """{"error":{"code":"INTERNAL_SERVER_ERROR","message":"Traceback (most recent call last)"}}""",
            retryAfterHeader = null,
            json = json,
        )
        assertEquals("INTERNAL_SERVER_ERROR", error.code)
        assertEquals(false, error.message?.contains("Traceback") == true)
    }

    @Test
    fun parsesValidationFieldDetails() {
        val error = ApiErrorParser.parse(
            status = 400,
            body = """{"error":{"code":"VALIDATION_ERROR","message":"Invalid input.","details":{"email":["Enter a valid email address."],"password":["This password is too short."]}}}""",
            retryAfterHeader = null,
            json = json,
        )
        assertEquals("VALIDATION_ERROR", error.code)
        assertEquals("Enter a valid email address.", error.fieldErrors["email"])
        assertEquals("This password is too short.", error.fieldErrors["password"])
    }
}

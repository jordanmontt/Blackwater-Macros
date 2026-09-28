package com.blackwatermacros.app.data

import com.blackwatermacros.app.R
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException

/**
 * Server errors are shown in the app language: the server sends a code next
 * to its Spanish text, and the app shows its own `server_error_*` string.
 */
class ResponseErrorMapperTest {

    private fun httpError(code: Int, body: String): HttpException {
        val responseBody = body.toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, responseBody))
    }

    @Test
    fun aKnownCodeBecomesTheAppString() {
        val e = httpError(409, """{"error":"El usuario ya existe","code":"username_exists"}""")
        assertThat(ResponseErrorMapper.messageFrom(e)).isEqualTo(UiText.Res(R.string.server_error_username_exists))
    }

    @Test
    fun anUnknownCodeFallsBackToTheServerText() {
        val e = httpError(400, """{"error":"Algo nuevo","code":"something_new"}""")
        assertThat(ResponseErrorMapper.messageFrom(e)).isEqualTo(UiText.Raw("Algo nuevo"))
    }

    @Test
    fun withoutAnEnvelopeTheStatusDecides() {
        assertThat(ResponseErrorMapper.messageFrom(httpError(404, """{"foo":"bar"}""")))
            .isEqualTo(UiText.Res(R.string.server_error_not_found))
        assertThat(ResponseErrorMapper.messageFrom(httpError(502, "")))
            .isEqualTo(UiText.Res(R.string.server_error_http, listOf(502)))
    }

    @Test
    fun noConnectionHasItsOwnMessage() {
        assertThat(ResponseErrorMapper.messageFrom(IOException("timeout")))
            .isEqualTo(UiText.Res(R.string.server_error_network))
    }

    /** The web's list (`serverErrors` in src/i18n/es.ts) and the app's must be the same codes. */
    @Test
    fun everyServerCodeHasAnAppString() {
        val es = File("../../src/i18n/es.ts").readText()
        val block = es.substringAfter("  serverErrors: {").substringBefore("\n  },")
        val webCodes = Regex("""^ {4}(\w+):""", RegexOption.MULTILINE).findAll(block).map { it.groupValues[1] }.toSet()
        assertWithMessage("codes in src/i18n/es.ts").that(webCodes).isNotEmpty()
        assertThat(ResponseErrorMapper.CODES.keys).containsExactlyElementsIn(webCodes)
    }
}

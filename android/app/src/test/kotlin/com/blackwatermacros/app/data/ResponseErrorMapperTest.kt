package com.blackwatermacros.app.data

import com.google.common.truth.Truth.assertThat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ResponseErrorMapperTest {

    private fun httpError(code: Int, body: String): HttpException {
        val responseBody = body.toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, responseBody))
    }

    @Test
    fun decodesSpanishEnvelopeFromBody() {
        val e = httpError(400, """{"error":"El titulo es obligatorio"}""")
        assertThat(ResponseErrorMapper.messageFrom(e))
            .isEqualTo("El titulo es obligatorio")
    }

    @Test
    fun maps401ToFallbackWhenBodyIsNotUsableError() {
        val e = httpError(401, """{"error":"No autenticado"}""")
        assertThat(ResponseErrorMapper.messageFrom(e)).isEqualTo("No autenticado")
    }

    @Test
    fun fallsBackToStatusCodeWhenNoEnvelope() {
        val e = httpError(404, """{"foo":"bar"}""")
        assertThat(ResponseErrorMapper.messageFrom(e)).isEqualTo("Recurso no encontrado")
    }

    @Test
    fun mapsIoExceptionToConnectionMessage() {
        assertThat(ResponseErrorMapper.messageFrom(IOException("timeout")))
            .isEqualTo("Problema de conexión. Inténtalo de nuevo.")
    }
}
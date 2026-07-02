package com.sibgear.deepseek.chat.data.magnit.internal.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

internal fun magnitCopilotHttpClient(
    json: Json,
    connectTimeoutMillis: Long,
    socketTimeoutMillis: Long,
    requestTimeoutMillis: Long,
): HttpClient =
    HttpClient(CIO) {
        expectSuccess = false
        engine {
            https {
                trustManager = TrustAllCertificates
            }
        }
        install(HttpTimeout) {
            this.connectTimeoutMillis = connectTimeoutMillis
            this.socketTimeoutMillis = socketTimeoutMillis
            this.requestTimeoutMillis = requestTimeoutMillis
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

private object TrustAllCertificates : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = Unit

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = Unit
}

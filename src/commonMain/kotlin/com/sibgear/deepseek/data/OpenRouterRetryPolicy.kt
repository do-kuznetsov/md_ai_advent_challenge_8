package com.sibgear.deepseek.data

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode

internal const val OpenRouterMaxRetries = 1
internal const val OpenRouterRetryDelayMillis = 1_750L

internal fun HttpStatusCode.isOpenRouterRetryableStatus(): Boolean =
    value == 408 ||
        value == 429 ||
        value == 500 ||
        value == 502 ||
        value == 503

internal fun Throwable.isOpenRouterRetryableTransportError(): Boolean =
    this is HttpRequestTimeoutException ||
        this is ConnectTimeoutException ||
        this is SocketTimeoutException

internal fun Throwable.isTimeoutError(): Boolean =
    this is HttpRequestTimeoutException ||
        this is ConnectTimeoutException ||
        this is SocketTimeoutException

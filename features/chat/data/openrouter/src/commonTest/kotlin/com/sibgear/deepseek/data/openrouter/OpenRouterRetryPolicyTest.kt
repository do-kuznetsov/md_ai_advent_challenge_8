package com.sibgear.deepseek.data.openrouter

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenRouterRetryPolicyTest {
    @Test
    fun retriesOnlyTimeoutRateLimitAndServerStatuses() {
        listOf(
            HttpStatusCode.RequestTimeout,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.InternalServerError,
            HttpStatusCode.BadGateway,
            HttpStatusCode.ServiceUnavailable,
        ).forEach { status ->
            assertTrue(status.isOpenRouterRetryableStatus(), "$status should be retryable")
        }
    }

    @Test
    fun doesNotRetryClientAndPaymentStatuses() {
        listOf(
            HttpStatusCode.BadRequest,
            HttpStatusCode.Unauthorized,
            HttpStatusCode.PaymentRequired,
            HttpStatusCode.Forbidden,
            HttpStatusCode.NotFound,
            HttpStatusCode.UnprocessableEntity,
        ).forEach { status ->
            assertFalse(status.isOpenRouterRetryableStatus(), "$status should not be retryable")
        }
    }
}

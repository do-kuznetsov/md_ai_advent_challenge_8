package com.sibgear.aireview

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

interface WebhookSignatureVerifier {
    fun isValid(payload: ByteArray, signatureHeader: String?): Boolean
}

class HmacSha256WebhookSignatureVerifier(
    private val secret: String,
) : WebhookSignatureVerifier {
    override fun isValid(payload: ByteArray, signatureHeader: String?): Boolean {
        if (secret.isBlank() || signatureHeader.isNullOrBlank() || !signatureHeader.startsWith(SignaturePrefix)) {
            return false
        }
        val expected = SignaturePrefix + payload.hmacSha256(secret)
        return expected.constantTimeEquals(signatureHeader)
    }

    private fun ByteArray.hmacSha256(secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.encodeToByteArray(), "HmacSHA256"))
        return mac.doFinal(this).joinToString(separator = "") { "%02x".format(it) }
    }

    private fun String.constantTimeEquals(other: String): Boolean {
        val maxLength = maxOf(length, other.length)
        var diff = length xor other.length
        repeat(maxLength) { index ->
            val left = getOrNull(index)?.code ?: 0
            val right = other.getOrNull(index)?.code ?: 0
            diff = diff or (left xor right)
        }
        return diff == 0
    }

    private companion object {
        const val SignaturePrefix = "sha256="
    }
}

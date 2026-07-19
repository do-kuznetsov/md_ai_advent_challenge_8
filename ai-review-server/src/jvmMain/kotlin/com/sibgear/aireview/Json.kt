package com.sibgear.aireview

import kotlinx.serialization.json.Json

internal val AiReviewJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

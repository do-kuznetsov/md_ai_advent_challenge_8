package com.sibgear.deepseek.chat.ui.internal.mapper

import kotlin.math.roundToLong

internal fun Long.formatMegabytes(): String {
    val megabytes = this / BytesInMegabyte
    val scaled = (megabytes * MegabyteScale).roundToLong()
    val whole = scaled / MegabyteScale.toLong()
    val fraction = (scaled % MegabyteScale.toLong()).toString().padStart(MegabyteFractionDigits, '0')
    return "$whole.$fraction MB"
}

private const val BytesInMegabyte = 1024.0 * 1024.0
private const val MegabyteScale = 100
private const val MegabyteFractionDigits = 2

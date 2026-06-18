package com.rivi.carbonwise.domain

import java.util.Locale

/**
 * Compact, locale-stable formatting for carbon amounts: whole numbers show without a
 * decimal, otherwise one decimal place. Uses [Locale.US] so the decimal separator is always
 * a dot — important because these values also feed JSON prompts and must not vary by device
 * locale. Shared by the engine, assistant phrasing, and the UI so formatting stays uniform.
 */
fun formatAmount(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }

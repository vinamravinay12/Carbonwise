package com.rivi.carbonwise.data

import com.rivi.carbonwise.domain.Footprint
import com.rivi.carbonwise.domain.Swap

/** A fully-rendered logged day, ready for the UI. */
data class LoggedDay(
    val id: Long,
    val epochDay: Long,
    val createdAt: Long,
    val sentence: String,
    val footprint: Footprint,
    val swap: Swap?,
    val unrecognized: List<String>,
    val impactNarrative: String? = null,
)

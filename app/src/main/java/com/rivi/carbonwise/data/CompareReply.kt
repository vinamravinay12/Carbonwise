package com.rivi.carbonwise.data

/**
 * One assistant turn in the Compare conversation. [reply] is the plain conversational text
 * (always present); [result] is a ranked comparison card, present only when this turn
 * actually compared options (a follow-up like "why?" returns text with no card).
 */
data class CompareReply(
    val reply: String,
    val result: CompareResult?,
)

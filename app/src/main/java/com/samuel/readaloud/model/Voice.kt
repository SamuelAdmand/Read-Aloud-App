package com.samuel.readaloud.model

data class Voice(
    val name: String,       // e.g., "Microsoft Server Speech... (en-US, AriaNeural)"
    val shortName: String,  // e.g., "en-US-AriaNeural"
    val gender: String,     // e.g., "Female"
    val locale: String      // e.g., "en-US"
)
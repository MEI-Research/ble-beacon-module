package com.pilrhealth

import kotlin.math.sqrt

class EventTimes(val name: String = "") {
    var previousValue = -1L
    var previousDTMillis = -1L
    var nDT = -1L
    private var maxDT = -1L
    private var sumDT = 0.0
    private var sumDT2 = 0.0

    var previousMillis: Long
        get() { return previousValue }
        set(value) {
            nDT += 1
            if (nDT > 0) {
                previousDTMillis = value - previousValue
                maxDT = maxDT.coerceAtLeast(previousDTMillis)
                sumDT += previousDTMillis
                sumDT2 += previousDTMillis * previousDTMillis
            }
            previousValue = value
        }

    val meanDeltaMillis: Double
        get() {
            return sumDT / nDT
        }

    val stdDeltaMillis: Double
        get() {
            return sqrt((sumDT2 - sumDT * sumDT / nDT) / nDT)
        }

    fun stats() = mapOf(
        "avg${name}_delta_t" to meanDeltaMillis / 1000.0,
        "sd${name}_delta_t" to stdDeltaMillis / 1000.0,
        "max${name}_delta_t" to maxDT / 1000.0,
        "previousDT" to previousDTMillis / 1000.0,
        "num_events" to nDT,
    )
}
package dev.yinpagi.ears

import android.bluetooth.BluetoothDevice

/**
 * Builds the battery `powers` list expected by com.miui.headset.api.HeadsetInfo.
 *
 * Layout (6 ints): [L, R, case, L_charging, R_charging, case_charging]
 * -1 means "unknown"; the card hides that cell.
 */
object BatteryFactory {
    private const val UNKNOWN = -1

    /** Set from the last known values; updated by ProtocolSession later. */
    @Volatile private var left: Int = UNKNOWN
    @Volatile private var right: Int = UNKNOWN
    @Volatile private var caseLevel: Int = UNKNOWN

    fun update(l: Int, r: Int, c: Int) {
        left = l; right = r; caseLevel = c
    }

    /** 6-int list matching MiLink's component battery contract. */
    fun forDevice(@Suppress("UNUSED_PARAMETER") dev: BluetoothDevice): List<Int> =
        listOf(left, right, caseLevel, 0, 0, 0)

    fun unknown(): List<Int> = listOf(UNKNOWN, UNKNOWN, UNKNOWN, 0, 0, 0)
}

/** Tracks the current RCSP noise mode per device address. */
object NoiseState {
    private val modes = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun setMode(address: String, mode: Int) { modes[normalize(address)] = mode }

    /** 0 = off, 1 = anc, 2 = transparency (matches MiLink's AncState). */
    fun currentMode(address: String): Int = modes[normalize(address)] ?: 0

    private fun normalize(a: String) = a.uppercase().replace(":", "")
}

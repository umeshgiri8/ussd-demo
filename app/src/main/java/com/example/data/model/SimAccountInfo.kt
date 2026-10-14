package com.example.data.model

data class SimAccountInfo(
    val subscriptionId: Int,
    val slotIndex: Int, // 0 for SIM 1, 1 for SIM 2
    val displayName: String,
    val carrierName: String,
    val isSimReady: Boolean = true
) {
    val slotLabel: String
        get() = "SIM ${slotIndex + 1}"
}

package com.example.miuipower.heartrate

import com.getcapacitor.JSObject
import java.util.UUID

object TargetDeviceMatcher {
    const val TARGET_NAME = "10705-1"
    val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")

    fun matches(device: HeartRateDevice, settings: AutoHeartRateSettings): Boolean {
        val savedAddress = settings.targetDeviceAddress?.takeIf { it.isNotBlank() }
        if (savedAddress != null && device.address.equals(savedAddress, ignoreCase = true)) {
            return true
        }

        val targetName = settings.targetDeviceName.ifBlank { TARGET_NAME }
        val nameMatches = device.name?.equals(targetName, ignoreCase = false) == true
        if (nameMatches && device.heartRateServiceAdvertised) {
            return true
        }
        return nameMatches
    }

    fun matches(device: JSObject, settings: AutoHeartRateSettings): Boolean {
        val savedAddress = settings.targetDeviceAddress?.takeIf { it.isNotBlank() }
        val address = device.optString("address", "")
        if (savedAddress != null && address.equals(savedAddress, ignoreCase = true)) {
            return true
        }

        val targetName = settings.targetDeviceName.ifBlank { TARGET_NAME }
        val nameMatches = device.optString("name", "") == targetName
        if (nameMatches && device.optBoolean("heartRateServiceAdvertised", false)) {
            return true
        }
        return nameMatches
    }
}

package com.ava.electricity.data

import androidx.compose.ui.graphics.vector.ImageVector

data class Device(
    val id: Int,
    val name: String,
    val powerKwh: Double,
    val icon: ImageVector
)

data class HistoryItem(
    val deviceName: String,
    val timeSchedule: String,
    val price: Double
)


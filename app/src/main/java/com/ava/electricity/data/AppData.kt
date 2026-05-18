package com.ava.electricity.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Restaurant

val languages = listOf(
    "English",
    "French",
    "Spanish",
    "German",
    "Italian",
    "Portuguese",
    "Dutch",
    "Polish",
    "Romanian",
    "Slovenian"
)

val countries = listOf(
    "None",
    "Austria",
    "Belgium",
    "Bulgaria",
    "Switzerland",
    "Czech Republic",
    "Germany / Luxembourg",
    "Germany / Austria / Luxembourg",
    "Denmark 1",
    "Denmark 2",
    "Estonia",
    "Spain",
    "Finland",
    "France",
    "Greece",
    "Croatia",
    "Hungary",
    "Italy Calabria",
    "Italy Centre North",
    "Italy Centre South",
    "Italy North",
    "Italy Sardinia",
    "Italy Sicily",
    "Italy South",
    "Lithuania",
    "Latvia",
    "Montenegro",
    "Netherlands",
    "Norway 1",
    "Norway 2",
    "Norway 3",
    "Norway 4",
    "Norway 5",
    "Poland",
    "Portugal",
    "Romania",
    "Serbia",
    "Sweden 1",
    "Sweden 2",
    "Sweden 3",
    "Sweden 4",
    "Slovenia",
    "Slovakia"
)

val devices = listOf(
    Device(1, "Washing Machine", 1.0, Icons.Default.LocalLaundryService),
    Device(2, "Dishwasher", 1.2, Icons.Default.Restaurant),
    Device(3, "Vacuum Cleaner", 0.8, Icons.Default.CleaningServices),
    Device(4, "Air Conditioner", 2.0, Icons.Default.Air),
    Device(5, "Oven", 2.4, Icons.Default.Kitchen),
    Device(6, "Electric Radiator", 1.5, Icons.Default.LocalFireDepartment)
)

val todayPrices = listOf(
    0.14, 0.13, 0.12, 0.11, 0.10, 0.12,
    0.16, 0.19, 0.22, 0.20, 0.15, 0.11,
    0.08, 0.07, 0.06, 0.08, 0.10, 0.16,
    0.24, 0.29, 0.31, 0.27, 0.20, 0.16
)

enum class PriceLevel { Cheap, Medium, Expensive }

fun priceColorLevel(price: Double): PriceLevel = when {
    price <= 0.09 -> PriceLevel.Cheap
    price <= 0.18 -> PriceLevel.Medium
    else -> PriceLevel.Expensive
}



package com.ava.electricity.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ava.electricity.R
import com.ava.electricity.data.HistoryItem
import com.ava.electricity.data.PriceLevel
import com.ava.electricity.data.devices
import com.ava.electricity.data.localizedDeviceName
import com.ava.electricity.data.todayPrices
import com.ava.electricity.data.uiText
import com.ava.electricity.network.AvaBackendClient
import com.ava.electricity.network.BackendRunner
import com.ava.electricity.storage.UserStorage
import com.ava.electricity.ui.components.BottomNavBar
import com.ava.electricity.ui.components.BottomNavButton
import com.ava.electricity.ui.components.PriceCurve
import java.time.LocalTime

private data class DurationOption(val label: String, val minutes: Int)
private data class RecommendationMetrics(
    val startMinute: Int,
    val endMinute: Int,
    val estimatedCost: Double,
    val energyKwh: Double,
    val averagePrice: Double,
    val savings: Double,
    val riskLabel: String,
    val riskColor: Color,
    val riskScore: Float,
    val powerLimitRatio: Float
)

@Composable
fun RecommendationScreen(language: String, country: String, nickname: String, onHome: () -> Unit, onBack: () -> Unit) {
    val text = uiText(language)
    val storage = UserStorage(LocalContext.current)
    val displayName = nickname.substringBefore("@").ifBlank { recCopy(language).guest }
    val recText = recCopy(language)
    val favoriteIds = storage.loadFavorites(nickname)
    val favoriteDevices = devices.filter { favoriteIds.contains(it.id) }.ifEmpty { devices }
    val durationOptions = localizedDurationOptions(language)
    val contractOptions = listOf("3.45 kW", "4.60 kW", "5.75 kW", "6.90 kW", "10.35 kW")
    val nowMinute = currentPhoneMinute()
    val firstStartMinute = roundUpToNextQuarter(nowMinute)
    val manualStartOptions = timeOptionsFrom(firstStartMinute, 23 * 60 + 45)

    var prices by remember { mutableStateOf(todayPrices) }
    var selectedDevice by remember { mutableStateOf(favoriteDevices.first()) }
    var durationMinutes by remember { mutableIntStateOf(15) }
    var useCustomPower by remember { mutableStateOf(false) }
    var customPowerText by remember(selectedDevice.id) { mutableStateOf("%.1f".format(selectedDevice.powerKwh)) }
    var selectedContract by remember { mutableStateOf("4.60 kW") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPriceRanges by remember { mutableStateOf(false) }
    var manualStartMinute by remember { mutableIntStateOf(firstStartMinute.coerceAtMost(23 * 60 + 45)) }
    var manualEndMinute by remember { mutableIntStateOf((manualStartMinute + 60).coerceAtMost(24 * 60)) }

    LaunchedEffect(country) {
        BackendRunner.run(
            task = { AvaBackendClient.getTodayPrices(country) },
            onSuccess = { prices = it }
        )
    }

    val customPower = customPowerText.replace(",", ".").toDoubleOrNull()
    val effectivePower = if (useCustomPower) {
        customPower?.coerceIn(0.1, 10.0) ?: selectedDevice.powerKwh
    } else {
        selectedDevice.powerKwh
    }
    val contractPowerKw = selectedContract.substringBefore(" ").toDoubleOrNull() ?: 4.6
    val bestRecommendation = buildSmartRecommendation(
        prices = prices,
        powerKw = effectivePower,
        contractPowerKw = contractPowerKw,
        earliestStartMinute = firstStartMinute,
        durationMinutes = durationMinutes
    )
    val shownStartTime = formatMinuteOfDay(bestRecommendation.startMinute)
    val shownEndTime = formatMinuteOfDay(bestRecommendation.endMinute)
    val shownBestCost = bestRecommendation.estimatedCost

    if (manualEndMinute <= manualStartMinute) {
        manualEndMinute = (manualStartMinute + 15).coerceAtMost(24 * 60)
    }
    val manualDuration = (manualEndMinute - manualStartMinute).coerceAtLeast(15)
    val manualCost = calculateCostForMinutes(prices, effectivePower, manualStartMinute, manualDuration)
    val manualEnergy = calculateEnergyKwh(effectivePower, manualDuration)
    val manualAveragePrice = calculatePriceForMinutes(prices, manualStartMinute, manualDuration) / (manualDuration / 60.0)
    val manualRisk = riskLabelFor(manualStartMinute, manualDuration, manualAveragePrice, effectivePower, contractPowerKw)

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.profile_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF8F7FBF8))
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PriceCurve(prices, text.electricityTitle, text.electricitySubtitle)
                Text("${text.bestTimeFor} $displayName", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))

                DarkDropdownField(text.favoriteDevice, localizedDeviceName(selectedDevice.id, language), favoriteDevices.map { localizedDeviceName(it.id, language) }) { name ->
                    selectedDevice = favoriteDevices.first { localizedDeviceName(it.id, language) == name }
                }

                ResultTabs(selectedTab = selectedTab, copy = recText, onSelectTab = { selectedTab = it })

                when (selectedTab) {
                    0 -> {
                        Text(recText.recommendedTimeSlot, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        DarkDropdownField(
                            text.duration,
                            durationOptions.first { it.minutes == durationMinutes }.label,
                            durationOptions.map { it.label }
                        ) { selected ->
                            durationMinutes = durationOptions.first { it.label == selected }.minutes
                        }
                        SmartResultCard(copy = recText, 
                            title = text.bestRecommendation,
                            time = "$shownStartTime - $shownEndTime",
                            cost = shownBestCost,
                            energyKwh = bestRecommendation.energyKwh,
                            averagePrice = bestRecommendation.averagePrice,
                            savings = bestRecommendation.savings,
                            riskLabel = bestRecommendation.riskLabel,
                            riskColor = bestRecommendation.riskColor,
                            riskScore = bestRecommendation.riskScore,
                            powerLimitRatio = bestRecommendation.powerLimitRatio
                        )
                        PriceLegend(text.cheap, text.medium, text.expensive)
                        Button(
                            onClick = {
                                val user = nickname.ifBlank { "Guest" }
                                val devicesToSave = devices.filter { favoriteIds.contains(it.id) }.ifEmpty { listOf(selectedDevice) }
                                devicesToSave.forEach { device ->
                                    val powerForDevice = if (useCustomPower && device.id == selectedDevice.id) {
                                        effectivePower
                                    } else {
                                        device.powerKwh
                                    }
                                    val deviceBest = buildSmartRecommendation(
                                        prices = prices,
                                        powerKw = powerForDevice,
                                        contractPowerKw = contractPowerKw,
                                        earliestStartMinute = firstStartMinute,
                                        durationMinutes = durationMinutes
                                    )
                                    val deviceManualCost = calculateCostForMinutes(prices, powerForDevice, manualStartMinute, manualDuration)
                                    val deviceManualEnergy = calculateEnergyKwh(powerForDevice, manualDuration)
                                    val deviceManualAveragePrice = calculateAveragePriceForMinutes(prices, manualStartMinute, manualDuration)
                                    val deviceManualRisk = riskLabelFor(manualStartMinute, manualDuration, deviceManualAveragePrice, powerForDevice, contractPowerKw)
                                    val displayName = localizedDeviceName(device.id, language)
                                    val summary = buildHistorySummary(
                                        best = deviceBest,
                                        manualStartMinute = manualStartMinute,
                                        manualEndMinute = manualEndMinute,
                                        manualCost = deviceManualCost,
                                        manualEnergy = deviceManualEnergy,
                                        manualAveragePrice = deviceManualAveragePrice,
                                        manualRiskLabel = deviceManualRisk.first,
                                        powerKw = powerForDevice,
                                        contractPowerKw = contractPowerKw
                                    )
                                    storage.saveHistory(
                                        user,
                                        HistoryItem(
                                            displayName,
                                            summary,
                                            deviceBest.estimatedCost
                                        )
                                    )
                                    BackendRunner.run(
                                        task = {
                                            AvaBackendClient.saveHistory(
                                                nickname = user,
                                                deviceId = device.id,
                                                deviceName = displayName,
                                                startTime = formatMinuteOfDay(deviceBest.startMinute),
                                                endTime = formatMinuteOfDay(deviceBest.endMinute),
                                                durationMinutes = durationMinutes,
                                                estimatedCostEur = deviceBest.estimatedCost
                                            )
                                        },
                                        onSuccess = { },
                                        onError = { }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text.saveToHistory)
                        }
                        PriceRangesSubtab(
                            copy = recText,
                            title = text.scheduleFromNow,
                            expanded = showPriceRanges,
                            onExpandedChange = { showPriceRanges = !showPriceRanges },
                            prices = prices,
                            nowMinute = nowMinute,
                            cheap = text.cheap,
                            medium = text.medium,
                            expensive = text.expensive
                        )
                    }

                    1 -> {
                        Text(recText.advancedEngine, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        PowerInputCard(copy = recText, 
                            useCustomPower = useCustomPower,
                            onUseCustomPowerChange = { useCustomPower = it },
                            defaultPower = selectedDevice.powerKwh,
                            customPowerText = customPowerText,
                            onCustomPowerTextChange = { customPowerText = it },
                            effectivePower = effectivePower
                        )
                        DarkDropdownField(recText.contractPowerLimit, selectedContract, contractOptions) { selected ->
                            selectedContract = selected
                        }
                        AdvancedResultCard(copy = recText, 
                            powerKw = effectivePower,
                            contractPowerKw = contractPowerKw,
                            metrics = bestRecommendation
                        )
                    }

                    else -> {
                        Text(recText.manualTimeRange, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        DarkDropdownField(recText.startTime, formatMinuteOfDay(manualStartMinute), manualStartOptions.map { formatMinuteOfDay(it) }) { selected ->
                            manualStartMinute = parseTimeToMinute(selected)
                            if (manualEndMinute <= manualStartMinute) {
                                manualEndMinute = (manualStartMinute + 15).coerceAtMost(24 * 60)
                            }
                        }
                        DarkDropdownField(recText.finishTime, formatMinuteOfDay(manualEndMinute), timeOptionsFrom(manualStartMinute + 15, 24 * 60).map { formatMinuteOfDay(it) }) { selected ->
                            manualEndMinute = parseTimeToMinute(selected).let { if (it == 0) 24 * 60 else it }
                        }
                        ManualResultCard(copy = recText, 
                            time = "${formatMinuteOfDay(manualStartMinute)} - ${formatMinuteOfDay(manualEndMinute)}",
                            cost = manualCost,
                            energyKwh = manualEnergy,
                            averagePrice = manualAveragePrice,
                            riskLabel = manualRisk.first,
                            riskColor = manualRisk.second
                        )
                    }
                }
            }

            BottomNavBar {
                BottomNavButton(text.home, Icons.Default.Home, Modifier.weight(1f), onHome)
                BottomNavButton(text.back, Icons.Default.ArrowBack, Modifier.weight(1f), onBack)
            }
        }
    }
}

@Composable
fun PowerInputCard(
    copy: RecommendationCopy, useCustomPower: Boolean,
    onUseCustomPowerChange: (Boolean) -> Unit,
    defaultPower: Double,
    customPowerText: String,
    onCustomPowerTextChange: (String) -> Unit,
    effectivePower: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xEEFFFFFF), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(copy.devicePower, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text("${copy.defaultPower}: ${"%.1f".format(defaultPower)} kW", color = Color(0xFF64748B), fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(copy.custom, color = Color(0xFF334155), fontSize = 13.sp)
                Switch(checked = useCustomPower, onCheckedChange = onUseCustomPowerChange)
            }
        }
        if (useCustomPower) {
            OutlinedTextField(
                value = customPowerText,
                onValueChange = { value ->
                    onCustomPowerTextChange(value.filter { it.isDigit() || it == '.' || it == ',' }.take(5))
                },
                label = { Text(copy.powerInKw) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text("${copy.usedInCalculation}: ${"%.2f".format(effectivePower)} kW", color = Color(0xFF047857), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ResultTabs(selectedTab: Int, copy: RecommendationCopy, onSelectTab: (Int) -> Unit) {
    val tabs = listOf(copy.bestTimeTab, copy.advancedTab, copy.manualTab)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xEEFFFFFF), RoundedCornerShape(20.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = selectedTab == index
            val bg = if (selected) Color(0xFF10B981) else Color(0xFFF8FAFC)
            val textColor = if (selected) Color.White else Color(0xFF065F46)
            val shadowColor = if (selected) Color(0x3310B981) else Color.Transparent
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(shadowColor, RoundedCornerShape(18.dp))
                    .padding(if (selected) 2.dp else 0.dp)
                    .background(bg, RoundedCornerShape(16.dp))
                    .clickable { onSelectTab(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = textColor,
                    fontWeight = FontWeight.Black,
                    fontSize = if (selected) 14.sp else 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkDropdownField(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = Color(0xFF334155)) },
            textStyle = TextStyle(color = Color(0xFF0F172A)),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFF0F172A),
                unfocusedTextColor = Color(0xFF0F172A),
                focusedLabelColor = Color(0xFF047857),
                unfocusedLabelColor = Color(0xFF334155),
                focusedBorderColor = Color(0xFF10B981),
                unfocusedBorderColor = Color(0xFF94A3B8),
                focusedContainerColor = Color(0xEEFFFFFF),
                unfocusedContainerColor = Color(0xEEFFFFFF),
                focusedTrailingIconColor = Color(0xFF0F172A),
                unfocusedTrailingIconColor = Color(0xFF0F172A),
                cursorColor = Color(0xFF0F172A)
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ResultCard(title: String, time: String, price: String) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier
                .background(Color(0xFFD1FAE5))
                .padding(18.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
            Text(time, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF064E3B))
            Text(price, color = Color(0xFF065F46))
        }
    }
}

@Composable
fun SmartResultCard(
    copy: RecommendationCopy, title: String,
    time: String,
    cost: Double,
    energyKwh: Double,
    averagePrice: Double,
    savings: Double,
    riskLabel: String,
    riskColor: Color,
    riskScore: Float,
    powerLimitRatio: Float
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier
                .background(Color(0xFFD1FAE5))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
            Text(time, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF064E3B))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GaugeIndicator(copy.cost, "EUR ${"%.2f".format(cost)}", 0.72f)
                GaugeIndicator(copy.savings, "EUR ${"%.2f".format(savings.coerceAtLeast(0.0))}", 0.82f)
                GaugeIndicator(copy.risk, localizedRiskLabel(riskLabel, copy), riskScore)
            }
            Text("${copy.energyConsumed}: ${"%.2f".format(energyKwh)} kWh", color = Color(0xFF065F46))
            Text("${copy.averagePrice}: EUR ${"%.3f".format(averagePrice)}/kWh", color = Color(0xFF065F46))
            Text("${copy.deviceUses} ${"%.0f".format(powerLimitRatio * 100)}% ${copy.contractLimitSuffix}", color = Color(0xFF065F46))
            Text(
                "${copy.riskOfPowerLimit}: ${localizedRiskLabel(riskLabel, copy)}",
                modifier = Modifier
                    .background(riskColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun AdvancedResultCard(
    copy: RecommendationCopy,
    powerKw: Double, contractPowerKw: Double, metrics: RecommendationMetrics) {
    val remaining = contractPowerKw - powerKw
    val overLimit = remaining < 0.0
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier
                .background(Color(0xEEFFFFFF))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(copy.powerLimitAnalysis, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GaugeIndicator(copy.device, "${"%.1f".format(powerKw)} kW", (powerKw / contractPowerKw).toFloat().coerceIn(0f, 1.15f))
                GaugeIndicator(copy.contract, "${"%.1f".format(contractPowerKw)} kW", 0.50f)
                GaugeIndicator(copy.limitRisk, localizedRiskLabel(metrics.riskLabel, copy), metrics.riskScore)
            }
            Text(
                if (overLimit) {
                    "${copy.overLimitBy} ${"%.2f".format(-remaining)} kW"
                } else {
                    "${copy.availableMargin}: ${"%.2f".format(remaining)} kW"
                },
                color = if (overLimit) Color(0xFFE11D48) else Color(0xFF334155),
                fontWeight = FontWeight.Bold
            )
            Text(copy.engineExplanation, color = Color(0xFF64748B), fontSize = 13.sp)
        }
    }
}

@Composable
fun ManualResultCard(
    copy: RecommendationCopy,
    time: String, cost: Double, energyKwh: Double, averagePrice: Double, riskLabel: String, riskColor: Color) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier
                .background(Color(0xFFEFF6FF))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(copy.selectedTimeCost, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
            Text(time, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GaugeIndicator(copy.cost, "EUR ${"%.2f".format(cost)}", 0.68f)
                GaugeIndicator(copy.energy, "${"%.2f".format(energyKwh)} kWh", 0.55f)
                GaugeIndicator(copy.risk, localizedRiskLabel(riskLabel, copy), if (riskLabel == "High") 0.92f else if (riskLabel == "Medium") 0.55f else 0.22f)
            }
            Text("${copy.averagePrice}: EUR ${"%.3f".format(averagePrice)}/kWh", color = Color(0xFF334155))
        }
    }
}

@Composable
fun GaugeIndicator(label: String, value: String, progress: Float) {
    val normalized = progress.coerceIn(0f, 1f)
    val color = rainbowGaugeColor(normalized)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp, 72.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val arcWidth = size.width - stroke
                val arcHeight = size.height * 1.55f
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(
                    color = Color(0xFFE2E8F0),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(arcWidth, arcHeight),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f * normalized.coerceAtLeast(0.04f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(arcWidth, arcHeight),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Text(
                value,
                modifier = Modifier.padding(top = 12.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF0F172A)
            )
        }
        Text(label, fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
    }
}

private fun rainbowGaugeColor(progress: Float): Color {
    return when {
        progress < 0.25f -> Color(0xFF2563EB)
        progress < 0.45f -> Color(0xFF14B8A6)
        progress < 0.65f -> Color(0xFFEAB308)
        progress < 0.85f -> Color(0xFFF97316)
        else -> Color(0xFFE11D48)
    }
}

private fun localizedDurationOptions(language: String): List<DurationOption> {
    return listOf(15, 30, 45, 60, 90, 120, 180, 240).map {
        DurationOption(formatDurationLabel(it, language), it)
    }
}

fun formatDurationLabel(minutes: Int, language: String): String {
    val minute = "min"
    val hour = when (language) {
        "French" -> "heure"
        "Spanish" -> "hora"
        "German" -> "Stunde"
        "Italian" -> "ora"
        "Portuguese" -> "hora"
        "Dutch" -> "uur"
        "Polish" -> "godzina"
        "Romanian" -> "ora"
        "Slovenian" -> "ura"
        else -> "hour"
    }
    val hours = when (language) {
        "French" -> "heures"
        "Spanish" -> "horas"
        "German" -> "Stunden"
        "Italian" -> "ore"
        "Portuguese" -> "horas"
        "Dutch" -> "uur"
        "Polish" -> "godziny"
        "Romanian" -> "ore"
        "Slovenian" -> "uri"
        else -> "hours"
    }
    return when {
        minutes < 60 -> "$minutes $minute"
        minutes == 60 -> "1 $hour"
        minutes % 60 == 0 -> "${minutes / 60} $hours"
        else -> "${minutes / 60} h ${minutes % 60} $minute"
    }
}

@Composable
fun PriceLegend(cheap: String, medium: String, expensive: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendItem(cheap, Color(0xFFD1FAE5), Modifier.weight(1f))
        LegendItem(medium, Color(0xFFFEF3C7), Modifier.weight(1f))
        LegendItem(expensive, Color(0xFFFEE2E2), Modifier.weight(1f))
    }
}

@Composable
fun LegendItem(label: String, color: Color, modifier: Modifier) {
    Text(
        text = label,
        modifier = modifier
            .background(color, RoundedCornerShape(12.dp))
            .padding(10.dp),
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun PriceRangesSubtab(
    copy: RecommendationCopy,
    title: String,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    prices: List<Double>,
    nowMinute: Int,
    cheap: String,
    medium: String,
    expensive: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xEEFFFFFF), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(copy.priceRanges, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(if (expanded) title else copy.tapToShowPrices, color = Color(0xFF64748B), fontSize = 12.sp)
            }
            TextButton(onClick = onExpandedChange) {
                Text(if (expanded) copy.hide else copy.show, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
        }
        if (expanded) {
            HourlyScheduleList(prices, nowMinute, cheap, medium, expensive)
        }
    }
}

@Composable
fun HourlyScheduleList(prices: List<Double>, nowMinute: Int, cheap: String, medium: String, expensive: String) {
    val currentHour = nowMinute / 60
    val visiblePrices = prices.drop(currentHour)
    val cheapestVisible = visiblePrices.minOrNull() ?: 0.0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (hour in currentHour..23) {
            val price = prices[hour]
            val level = when {
                price == cheapestVisible || price <= 0.10 -> PriceLevel.Cheap
                price >= 0.19 -> PriceLevel.Expensive
                else -> PriceLevel.Medium
            }
            val color = when (level) {
                PriceLevel.Cheap -> Color(0xFFD1FAE5)
                PriceLevel.Medium -> Color(0xFFFEF3C7)
                PriceLevel.Expensive -> Color(0xFFFEE2E2)
            }
            val label = when (level) {
                PriceLevel.Cheap -> cheap
                PriceLevel.Medium -> medium
                PriceLevel.Expensive -> expensive
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("${formatHour(hour)} - ${formatHour(hour + 1)}", fontWeight = FontWeight.Bold)
                    Text(label, fontSize = 12.sp)
                }
                Text("EUR ${"%.2f".format(price)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun findBestStartMinute(prices: List<Double>, earliestStartMinute: Int, durationMinutes: Int): Int {
    val latestStart = 24 * 60 - durationMinutes
    if (earliestStartMinute > latestStart) return latestStart.coerceAtLeast(0)

    var bestStart = earliestStartMinute
    var bestCost = Double.MAX_VALUE

    for (startMinute in earliestStartMinute..latestStart step 15) {
        val cost = calculatePriceForMinutes(prices, startMinute, durationMinutes)
        if (cost < bestCost) {
            bestCost = cost
            bestStart = startMinute
        }
    }
    return bestStart
}

private fun buildSmartRecommendation(
    prices: List<Double>,
    powerKw: Double,
    contractPowerKw: Double,
    earliestStartMinute: Int,
    durationMinutes: Int
): RecommendationMetrics {
    val latestStart = (24 * 60 - durationMinutes).coerceAtLeast(0)
    val firstStart = earliestStartMinute.coerceAtMost(latestStart)
    val candidates = (firstStart..latestStart step 15).map { startMinute ->
        val averagePrice = calculateAveragePriceForMinutes(prices, startMinute, durationMinutes)
        val cost = calculateCostForMinutes(prices, powerKw, startMinute, durationMinutes)
        val riskScore = calculatePeakRiskScore(startMinute, durationMinutes, averagePrice, powerKw, contractPowerKw)
        val comfortScore = calculateComfortPenalty(startMinute)
        Triple(startMinute, cost, cost + riskScore + comfortScore)
    }

    val best = candidates.minByOrNull { it.third } ?: Triple(firstStart, 0.0, 0.0)
    val mostExpensiveCost = candidates.maxOfOrNull { it.second } ?: best.second
    val bestStart = best.first
    val averagePrice = calculateAveragePriceForMinutes(prices, bestStart, durationMinutes)
    val risk = riskLabelFor(bestStart, durationMinutes, averagePrice, powerKw, contractPowerKw)
    val powerLimitRatio = (powerKw / contractPowerKw).toFloat().coerceIn(0f, 1f)

    return RecommendationMetrics(
        startMinute = bestStart,
        endMinute = bestStart + durationMinutes,
        estimatedCost = best.second,
        energyKwh = calculateEnergyKwh(powerKw, durationMinutes),
        averagePrice = averagePrice,
        savings = mostExpensiveCost - best.second,
        riskLabel = risk.first,
        riskColor = risk.second,
        riskScore = risk.third,
        powerLimitRatio = powerLimitRatio
    )
}

private fun buildHistorySummary(
    best: RecommendationMetrics,
    manualStartMinute: Int,
    manualEndMinute: Int,
    manualCost: Double,
    manualEnergy: Double,
    manualAveragePrice: Double,
    manualRiskLabel: String,
    powerKw: Double,
    contractPowerKw: Double
): String {
    val margin = contractPowerKw - powerKw
    val advancedText = if (margin < 0.0) {
        "Advanced: power ${"%.2f".format(powerKw)} kW, contract ${"%.2f".format(contractPowerKw)} kW, over limit ${"%.2f".format(-margin)} kW, risk ${best.riskLabel}"
    } else {
        "Advanced: power ${"%.2f".format(powerKw)} kW, contract ${"%.2f".format(contractPowerKw)} kW, margin ${"%.2f".format(margin)} kW, risk ${best.riskLabel}"
    }
    return listOf(
        "Best: ${formatMinuteOfDay(best.startMinute)} - ${formatMinuteOfDay(best.endMinute)}, cost EUR ${"%.2f".format(best.estimatedCost)}, energy ${"%.2f".format(best.energyKwh)} kWh, savings EUR ${"%.2f".format(best.savings.coerceAtLeast(0.0))}",
        advancedText,
        "Manual: ${formatMinuteOfDay(manualStartMinute)} - ${formatMinuteOfDay(manualEndMinute)}, cost EUR ${"%.2f".format(manualCost)}, energy ${"%.2f".format(manualEnergy)} kWh, avg EUR ${"%.3f".format(manualAveragePrice)}/kWh, risk $manualRiskLabel"
    ).joinToString("; ")
}

private fun calculateAveragePriceForMinutes(prices: List<Double>, startMinute: Int, durationMinutes: Int): Double {
    val hours = durationMinutes / 60.0
    if (hours <= 0.0) return 0.0
    return calculatePriceForMinutes(prices, startMinute, durationMinutes) / hours
}

private fun calculateEnergyKwh(powerKw: Double, durationMinutes: Int): Double {
    return powerKw * (durationMinutes / 60.0)
}

private fun calculatePeakRiskScore(startMinute: Int, durationMinutes: Int, averagePrice: Double, powerKw: Double, contractPowerKw: Double): Double {
    val endMinute = startMinute + durationMinutes
    val overlapsEveningPeak = startMinute < 21 * 60 && endMinute > 18 * 60
    val powerLimitRatio = powerKw / contractPowerKw
    if (powerLimitRatio > 1.0) return 1.0
    val priceRisk = when {
        averagePrice >= 0.22 -> 0.08
        averagePrice >= 0.16 -> 0.04
        else -> 0.0
    }
    val peakRisk = if (overlapsEveningPeak) 0.05 else 0.0
    val powerRisk = when {
        powerLimitRatio >= 0.85 -> 0.10
        powerLimitRatio >= 0.65 -> 0.05
        powerKw >= 2.0 -> 0.03
        else -> 0.0
    }
    return priceRisk + peakRisk + powerRisk
}

private fun calculateComfortPenalty(startMinute: Int): Double {
    val hour = startMinute / 60
    return when (hour) {
        in 0..5 -> 0.015
        in 22..23 -> 0.005
        else -> 0.0
    }
}

private fun riskLabelFor(startMinute: Int, durationMinutes: Int, averagePrice: Double, powerKw: Double, contractPowerKw: Double): Triple<String, Color, Float> {
    if (powerKw > contractPowerKw) {
        return Triple("High", Color(0xFFFECACA), 1.0f)
    }
    val score = calculatePeakRiskScore(startMinute, durationMinutes, averagePrice, powerKw, contractPowerKw)
    return when {
        score >= 0.11 -> Triple("High", Color(0xFFFECACA), 0.9f)
        score >= 0.05 -> Triple("Medium", Color(0xFFFEF3C7), 0.55f)
        else -> Triple("Low", Color(0xFFBBF7D0), 0.2f)
    }
}

fun calculatePriceForMinutes(prices: List<Double>, startMinute: Int, durationMinutes: Int): Double {
    var remaining = durationMinutes
    var currentMinute = startMinute
    var total = 0.0

    while (remaining > 0) {
        val hour = (currentMinute / 60).coerceIn(0, 23)
        val minutesUntilNextHour = 60 - (currentMinute % 60)
        val usedMinutes = minOf(remaining, minutesUntilNextHour)
        total += prices[hour] * (usedMinutes / 60.0)
        currentMinute += usedMinutes
        remaining -= usedMinutes
    }
    return total
}

fun calculateCostForMinutes(prices: List<Double>, power: Double, startMinute: Int, durationMinutes: Int): Double {
    return calculatePriceForMinutes(prices, startMinute, durationMinutes) * power
}

fun currentPhoneMinute(): Int {
    val now = LocalTime.now()
    return now.hour * 60 + now.minute
}

fun roundUpToNextQuarter(minuteOfDay: Int): Int {
    val remainder = minuteOfDay % 15
    return if (remainder == 0) minuteOfDay else minuteOfDay + (15 - remainder)
}

fun timeOptionsFrom(startMinute: Int, endMinute: Int): List<Int> {
    val start = roundUpToNextQuarter(startMinute).coerceIn(0, 24 * 60)
    val end = endMinute.coerceIn(0, 24 * 60)
    if (start > end) return listOf(end)
    return (start..end step 15).toList()
}

fun parseTimeToMinute(value: String): Int {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return if (hour == 0 && minute == 0 && value == "00:00") 0 else hour * 60 + minute
}

fun formatHour(hour: Int): String = "${hour % 24}:00".padStart(5, '0')

fun formatMinuteOfDay(minuteOfDay: Int): String {
    if (minuteOfDay == 24 * 60) return "00:00"
    val normalized = ((minuteOfDay % (24 * 60)) + (24 * 60)) % (24 * 60)
    val hour = normalized / 60
    val minute = normalized % 60
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}



data class RecommendationCopy(
    val guest: String,
    val recommendedTimeSlot: String,
    val advancedEngine: String,
    val manualTimeRange: String,
    val contractPowerLimit: String,
    val startTime: String,
    val finishTime: String,
    val bestTimeTab: String,
    val advancedTab: String,
    val manualTab: String,
    val devicePower: String,
    val defaultPower: String,
    val custom: String,
    val powerInKw: String,
    val usedInCalculation: String,
    val cost: String,
    val savings: String,
    val risk: String,
    val energyConsumed: String,
    val averagePrice: String,
    val deviceUses: String,
    val contractLimitSuffix: String,
    val riskOfPowerLimit: String,
    val powerLimitAnalysis: String,
    val device: String,
    val contract: String,
    val limitRisk: String,
    val overLimitBy: String,
    val availableMargin: String,
    val engineExplanation: String,
    val selectedTimeCost: String,
    val energy: String,
    val priceRanges: String,
    val tapToShowPrices: String,
    val show: String,
    val hide: String
)

private fun recCopy(language: String): RecommendationCopy = when (language) {
    "Spanish" -> RecommendationCopy("Invitado", "Horario recomendado", "Motor avanzado", "Rango manual", "Limite de potencia contratada", "Hora inicial", "Hora final", "Mejor hora", "Avanzado", "Manual", "Potencia del dispositivo", "Predeterminado", "Personalizado", "Potencia en kW", "Usado en el calculo", "Coste", "Ahorro", "Riesgo", "Energia consumida", "Precio medio", "El dispositivo usa", "del limite contratado seleccionado.", "Riesgo de superar el limite de potencia", "Analisis del limite de potencia", "Dispositivo", "Contrato", "Riesgo limite", "Supera el contrato por", "Margen de potencia disponible", "El motor compara precio, riesgo de hora punta, comodidad del usuario y limite contratado.", "Coste del horario seleccionado", "Energia", "Rangos de precio", "Toca para ver precios horarios", "Mostrar", "Ocultar")
    "French" -> RecommendationCopy("Invite", "Creneau recommande", "Moteur avance", "Plage manuelle", "Limite de puissance du contrat", "Heure de debut", "Heure de fin", "Meilleur moment", "Avance", "Manuel", "Puissance de l'appareil", "Par defaut", "Personnalise", "Puissance en kW", "Utilise dans le calcul", "Cout", "Economies", "Risque", "Energie consommee", "Prix moyen", "L'appareil utilise", "de la limite du contrat selectionne.", "Risque de depasser la limite de puissance", "Analyse de limite de puissance", "Appareil", "Contrat", "Risque limite", "Depasse le contrat de", "Marge de puissance disponible", "Le moteur compare le prix, le risque de pointe, le confort et la limite du contrat.", "Cout de la plage selectionnee", "Energie", "Plages de prix", "Toucher pour afficher les prix horaires", "Afficher", "Masquer")
    "German" -> RecommendationCopy("Gast", "Empfohlenes Zeitfenster", "Erweiterter Motor", "Manueller Zeitraum", "Vertragsleistung", "Startzeit", "Endzeit", "Beste Zeit", "Erweitert", "Manuell", "Geraeteleistung", "Standard", "Benutzerdefiniert", "Leistung in kW", "In Berechnung verwendet", "Kosten", "Ersparnis", "Risiko", "Verbrauchte Energie", "Durchschnittspreis", "Das Geraet nutzt", "des gewaehlten Vertragslimits.", "Risiko einer Leistungsueberschreitung", "Analyse der Leistungsgrenze", "Geraet", "Vertrag", "Grenzrisiko", "Ueber Vertragslimit um", "Verfuegbare Leistungsreserve", "Der Motor vergleicht Preis, Spitzenrisiko, Komfort und Vertragslimit.", "Kosten des gewaehlten Zeitraums", "Energie", "Preisbereiche", "Tippen fuer Stundenpreise", "Anzeigen", "Ausblenden")
    "Italian" -> RecommendationCopy("Ospite", "Fascia oraria consigliata", "Motore avanzato", "Intervallo manuale", "Limite potenza contratto", "Ora inizio", "Ora fine", "Momento migliore", "Avanzato", "Manuale", "Potenza dispositivo", "Predefinito", "Personalizzato", "Potenza in kW", "Usato nel calcolo", "Costo", "Risparmio", "Rischio", "Energia consumata", "Prezzo medio", "Il dispositivo usa", "del limite contratto selezionato.", "Rischio di superare il limite di potenza", "Analisi limite potenza", "Dispositivo", "Contratto", "Rischio limite", "Supera il contratto di", "Margine potenza disponibile", "Il motore confronta prezzo, rischio di picco, comfort e limite contratto.", "Costo intervallo selezionato", "Energia", "Fasce di prezzo", "Tocca per mostrare prezzi orari", "Mostra", "Nascondi")
    "Portuguese" -> RecommendationCopy("Convidado", "Horario recomendado", "Motor avancado", "Intervalo manual", "Limite de potencia contratada", "Hora inicial", "Hora final", "Melhor hora", "Avancado", "Manual", "Potencia do dispositivo", "Padrao", "Personalizado", "Potencia em kW", "Usado no calculo", "Custo", "Poupanca", "Risco", "Energia consumida", "Preco medio", "O dispositivo usa", "do limite contratado selecionado.", "Risco de ultrapassar o limite de potencia", "Analise do limite de potencia", "Dispositivo", "Contrato", "Risco limite", "Ultrapassa o contrato em", "Margem de potencia disponivel", "O motor compara preco, risco de pico, conforto e limite contratado.", "Custo do horario selecionado", "Energia", "Faixas de preco", "Toque para ver precos horarios", "Mostrar", "Ocultar")
    "Dutch" -> RecommendationCopy("Gast", "Aanbevolen tijdslot", "Geavanceerde motor", "Handmatig tijdvak", "Contractvermogen", "Starttijd", "Eindtijd", "Beste tijd", "Geavanceerd", "Handmatig", "Apparaatvermogen", "Standaard", "Aangepast", "Vermogen in kW", "Gebruikt in berekening", "Kosten", "Besparing", "Risico", "Verbruikte energie", "Gemiddelde prijs", "Apparaat gebruikt", "van de geselecteerde contractlimiet.", "Risico op overschrijding van vermogenslimiet", "Analyse vermogenslimiet", "Apparaat", "Contract", "Limietrisico", "Boven contractlimiet met", "Beschikbare vermogensmarge", "De motor vergelijkt prijs, piekrisico, comfort en contractlimiet.", "Kosten van geselecteerde tijd", "Energie", "Prijsbereiken", "Tik om uurprijzen te tonen", "Tonen", "Verbergen")
    "Polish" -> RecommendationCopy("Gosc", "Rekomendowany przedzial", "Silnik zaawansowany", "Zakres reczny", "Limit mocy umowy", "Czas startu", "Czas konca", "Najlepszy czas", "Zaawansowane", "Recznie", "Moc urzadzenia", "Domyslna", "Wlasna", "Moc w kW", "Uzyte w obliczeniu", "Koszt", "Oszczednosc", "Ryzyko", "Zuzyta energia", "Srednia cena", "Urzadzenie uzywa", "wybranego limitu umowy.", "Ryzyko przekroczenia limitu mocy", "Analiza limitu mocy", "Urzadzenie", "Umowa", "Ryzyko limitu", "Przekracza umowe o", "Dostepny margines mocy", "Silnik porownuje cene, ryzyko szczytu, komfort i limit umowy.", "Koszt wybranego czasu", "Energia", "Zakresy cen", "Dotknij, aby pokazac ceny godzinowe", "Pokaz", "Ukryj")
    "Romanian" -> RecommendationCopy("Oaspete", "Interval recomandat", "Motor avansat", "Interval manual", "Limita putere contract", "Ora start", "Ora final", "Cel mai bun timp", "Avansat", "Manual", "Putere dispozitiv", "Implicit", "Personalizat", "Putere in kW", "Folosit in calcul", "Cost", "Economii", "Risc", "Energie consumata", "Pret mediu", "Dispozitivul foloseste", "din limita contractului selectat.", "Risc de depasire a limitei de putere", "Analiza limitei de putere", "Dispozitiv", "Contract", "Risc limita", "Depaseste contractul cu", "Marja de putere disponibila", "Motorul compara pretul, riscul de varf, confortul si limita contractului.", "Costul timpului selectat", "Energie", "Intervale de pret", "Apasa pentru preturi orare", "Arata", "Ascunde")
    "Slovenian" -> RecommendationCopy("Gost", "Priporocen casovni termin", "Napredni izracun", "Rocni casovni razpon", "Omejitev pogodbene moci", "Zacetni cas", "Koncni cas", "Najboljsi cas", "Napredno", "Rocno", "Moc naprave", "Privzeto", "Po meri", "Moc v kW", "Uporabljeno v izracunu", "Strosek", "Prihranek", "Tveganje", "Porabljena energija", "Povprecna cena", "Naprava uporablja", "izbrane pogodbene omejitve.", "Tveganje preseganja omejitve moci", "Analiza omejitve moci", "Naprava", "Pogodba", "Tveganje omejitve", "Presega pogodbo za", "Razpolozljiva rezerva moci", "Izracun primerja ceno, tveganje konice, udobje in pogodbeno omejitev.", "Strosek izbranega casa", "Energija", "Cenovni razponi", "Tapnite za prikaz urnih cen", "Prikazi", "Skrij")
    else -> RecommendationCopy("Guest", "Recommended time slot", "Advanced engine", "Manual time range", "Contract power limit", "Start time", "Finish time", "Best time", "Advanced", "Manual", "Device power", "Default", "Custom", "Power in kW", "Used in calculation", "Cost", "Savings", "Risk", "Energy consumed", "Average price", "Device uses", "of the selected contract limit.", "Risk of exceeding power limits", "Power limit analysis", "Device", "Contract", "Limit risk", "Over contract limit by", "Available power margin", "The engine compares price, evening peak risk, user comfort, and the contract power limit.", "Selected time cost", "Energy", "Price ranges", "Tap to show hourly prices", "Show", "Hide")
}




private fun localizedRiskLabel(value: String, copy: RecommendationCopy): String {
    val level = when (value) {
        "High" -> 2
        "Medium" -> 1
        else -> 0
    }
    return when (copy.guest) {
        "Invitado" -> listOf("Bajo", "Medio", "Alto")[level]
        "Invite" -> listOf("Faible", "Moyen", "Eleve")[level]
        "Gast" -> if (copy.bestTimeTab == "Beste Zeit") listOf("Niedrig", "Mittel", "Hoch")[level] else listOf("Laag", "Middel", "Hoog")[level]
        "Ospite" -> listOf("Basso", "Medio", "Alto")[level]
        "Convidado" -> listOf("Baixo", "Medio", "Alto")[level]
        "Gosc" -> listOf("Niskie", "Srednie", "Wysokie")[level]
        "Oaspete" -> listOf("Scazut", "Mediu", "Ridicat")[level]
        "Gost" -> listOf("Nizko", "Srednje", "Visoko")[level]
        else -> value
    }
}

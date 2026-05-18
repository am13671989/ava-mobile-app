package com.ava.electricity.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ava.electricity.R
import com.ava.electricity.data.devices
import com.ava.electricity.data.localizedDeviceName
import com.ava.electricity.data.todayPrices
import com.ava.electricity.data.uiText
import com.ava.electricity.storage.UserStorage
import com.ava.electricity.data.HistoryItem
import com.ava.electricity.network.AvaBackendClient
import com.ava.electricity.network.BackendRunner
import com.ava.electricity.ui.components.BottomNavBar
import com.ava.electricity.ui.components.BottomNavButton
import com.ava.electricity.ui.components.DeviceItem
import com.ava.electricity.ui.components.HistoryTab
import com.ava.electricity.ui.components.PriceCurve

@Composable
fun ProfileDevicesScreen(
    language: String,
    country: String,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onHome: () -> Unit,
    onContinue: () -> Unit
) {
    val text = uiText(language)
    val storage = UserStorage(LocalContext.current)
    var selectedFavorites by remember { mutableStateOf(setOf<Int>()) }
    var showHistory by remember { mutableStateOf(false) }
    var prices by remember { mutableStateOf(todayPrices) }
    var historyItems by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }

    LaunchedEffect(country) {
        BackendRunner.run(
            task = { AvaBackendClient.getTodayPrices(country) },
            onSuccess = { prices = it }
        )
    }

    LaunchedEffect(nickname) {
        val user = nickname.ifBlank { "Guest" }
        selectedFavorites = storage.loadFavorites(user)
        historyItems = storage.loadHistory(user)
        BackendRunner.run(
            task = { AvaBackendClient.getFavorites(user) },
            onSuccess = { selectedFavorites = it }
        )
        BackendRunner.run(
            task = { AvaBackendClient.getHistory(user) },
            onSuccess = { historyItems = it }
        )
    }

    fun saveAndContinue() {
        val user = nickname.ifBlank { "Guest" }
        storage.saveFavorites(user, selectedFavorites)
        BackendRunner.run(
            task = { AvaBackendClient.saveFavorites(user, selectedFavorites) },
            onSuccess = { },
            onError = { }
        )
        onContinue()
    }

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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xF6F8FFFB), Color(0xF8F7FBF8), Color(0xFAF7FBF8))
                    )
                )
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

                OutlinedTextField(
                    value = nickname,
                    onValueChange = onNicknameChange,
                    readOnly = true,
                    label = { Text(text.nickname) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (showHistory) {
                    HistoryTab(
                        history = historyItems,
                        emptyText = text.noHistory,
                        priceLabel = text.price,
                        onClearHistory = {
                            val user = nickname.ifBlank { "Guest" }
                            storage.clearHistory(user)
                            historyItems = emptyList()
                        }
                    )
                } else {
                    Text(text.chooseFavoriteDevices, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text("Select the devices you use most often", color = Color(0xFF64748B))
                    devices.forEach { device ->
                        DeviceItem(
                            device = device,
                            displayName = localizedDeviceName(device.id, language),
                            isFavorite = selectedFavorites.contains(device.id),
                            onClick = {
                                selectedFavorites = if (selectedFavorites.contains(device.id)) selectedFavorites - device.id else selectedFavorites + device.id
                            }
                        )
                    }
                }
            }
            BottomNavBar {
                BottomNavButton(text.home, Icons.Default.Home, Modifier.weight(1f), onHome)
                BottomNavButton(text.devices, Icons.Default.Star, Modifier.weight(1f), { showHistory = false })
                BottomNavButton(text.history, Icons.Default.History, Modifier.weight(1f), { showHistory = true })
                BottomNavButton(text.continueText, Icons.Default.NavigateNext, Modifier.weight(1f), { saveAndContinue() })
            }
        }
    }
}


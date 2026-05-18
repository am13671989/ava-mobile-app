package com.ava.electricity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ava.electricity.storage.UserStorage
import com.ava.electricity.ui.screens.ProfileDevicesScreen
import com.ava.electricity.ui.screens.RecommendationScreen
import com.ava.electricity.ui.screens.UserProfileScreen
import com.ava.electricity.ui.screens.WelcomeScreen
import com.ava.electricity.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AvaApp()
                }
            }
        }
    }
}

@Composable
fun AvaApp() {
    val storage = UserStorage(LocalContext.current)
    val savedUser = remember { storage.loadSignedInUser() }
    var page by remember { mutableIntStateOf(if (savedUser.isBlank()) 1 else 2) }
    var nickname by remember { mutableStateOf(savedUser) }
    var language by remember { mutableStateOf("English") }
    var country by remember { mutableStateOf("None") }

    when (page) {
        1 -> WelcomeScreen(
            language = language,
            country = country,
            onLanguageChange = { language = it },
            onCountryChange = { country = it },
            onContinue = { page = 2 },
            signedInUser = nickname.takeIf { it.contains("@") }.orEmpty(),
            onAccountSignedIn = { accountEmail ->
                storage.saveSignedInUser(accountEmail)
                nickname = accountEmail
                page = 2
            },
            onAccountSignOut = {
                storage.signOut()
                nickname = ""
                page = 1
            }
        )
        2 -> UserProfileScreen(
            language = language,
            selectedNickname = nickname,
            onNicknameSelected = {
                nickname = it
                if (it.contains("@")) storage.saveSignedInUser(it)
            },
            onBackToWelcome = { page = 1 },
            onContinue = {
                if (nickname.isNotBlank()) {
                    page = 3
                }
            }
        )
        3 -> ProfileDevicesScreen(
            language = language,
            country = country,
            nickname = nickname,
            onNicknameChange = { nickname = it },
            onHome = { page = 2 },
            onContinue = { page = 4 }
        )
        4 -> RecommendationScreen(
            language = language,
            country = country,
            nickname = nickname.ifBlank { "Guest" },
            onHome = { page = 2 },
            onBack = { page = 3 }
        )
    }
}







package com.tableads.app

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tableads.app.kiosk.Kiosk
import com.tableads.app.ui.AdsScreen
import com.tableads.app.ui.AppViewModel
import com.tableads.app.ui.ConfigErrorScreen
import com.tableads.app.ui.HomeScreen
import com.tableads.app.ui.MenuScreen
import com.tableads.app.ui.PairingScreen
import com.tableads.app.ui.PinLockScreen
import com.tableads.app.ui.Screen
import com.tableads.app.ui.SettingsScreen
import com.tableads.app.ui.games.GameHost
import com.tableads.app.ui.games.GamesScreen
import com.tableads.app.ui.theme.TableAdsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = vm.back()
        })

        lifecycleScope.launch {
            for (cmd in vm.repository.commands) {
                if (cmd == "restart_app") restartApp()
            }
        }

        setContent {
            TableAdsTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AppContent(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        Kiosk.enter(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        vm.onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}

@Composable
private fun AppContent(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()

    state.configError?.let {
        ConfigErrorScreen(it)
        return
    }

    val payload = state.payload
    val unlinked = payload == null || !payload.linked
    // Until the device is linked, only the pairing screen and the (master-PIN) settings are reachable.
    val effective = if (unlinked && screen != Screen.PinLock && screen != Screen.Settings) null else screen

    AnimatedContent(
        targetState = effective,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen",
    ) { target ->
        when (target) {
            null -> PairingScreen(state, vm.repository.identity.deviceId, onOpenSettings = { vm.navigate(Screen.PinLock) })
            Screen.Ads -> AdsScreen(state, onTap = { vm.navigate(Screen.Home) })
            Screen.Home -> HomeScreen(state, onNavigate = vm::navigate)
            Screen.Menu -> MenuScreen(state.menuFile, onBack = vm::back)
            Screen.Games -> GamesScreen(onOpen = { vm.navigate(Screen.Game(it)) }, onBack = vm::back)
            is Screen.Game -> GameHost(target.id, onBack = vm::back)
            Screen.PinLock -> PinLockScreen(
                verify = vm.repository::verifyPin,
                onUnlocked = { vm.navigate(Screen.Settings) },
                onCancel = { vm.navigate(if (unlinked) Screen.Ads else Screen.Home) },
            )
            Screen.Settings -> SettingsScreen(vm, onClose = { vm.navigate(if (unlinked) Screen.Ads else Screen.Home) })
        }
    }
}

package com.tableads.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tableads.app.TableAdsApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repository = (app as TableAdsApp).repository
    val state = repository.state

    private val _screen = MutableStateFlow<Screen>(Screen.Ads)
    val screen = _screen.asStateFlow()

    private var lastInteraction = System.currentTimeMillis()

    init {
        // Return to the ad carousel after the restaurant's idle timeout.
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val timeoutMs = (state.value.payload?.restaurant?.idleTimeoutSeconds ?: 60) * 1000L
                if (_screen.value != Screen.Ads && System.currentTimeMillis() - lastInteraction > timeoutMs) {
                    _screen.value = Screen.Ads
                }
            }
        }
    }

    fun onUserInteraction() {
        lastInteraction = System.currentTimeMillis()
    }

    fun navigate(to: Screen) {
        onUserInteraction()
        _screen.value = to
    }

    fun back() {
        navigate(
            when (_screen.value) {
                is Screen.Game -> Screen.Games
                Screen.Ads -> Screen.Ads
                Screen.Home -> Screen.Ads
                else -> Screen.Home
            },
        )
    }
}

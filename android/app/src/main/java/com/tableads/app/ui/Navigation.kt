package com.tableads.app.ui

sealed interface Screen {
    data object Ads : Screen
    data object Home : Screen
    data object Menu : Screen
    data object Games : Screen
    data class Game(val id: String) : Screen
    data object PinLock : Screen
    data object Settings : Screen
}

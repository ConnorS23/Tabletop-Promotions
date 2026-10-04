package com.tableads.app.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tableads.app.ui.ScreenScaffold

/** To add a game: add an entry here and a branch in [GameHost]. */
data class GameInfo(val id: String, val title: String, val emoji: String, val color: Color, val blurb: String)

val GAMES = listOf(
    GameInfo("tictactoe", "Tic-Tac-Toe", "❌", Color(0xFFEF5350), "Play a friend or the tablet"),
)

@Composable
fun GamesScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(title = "Games", onBack = onBack) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(260.dp),
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(GAMES) { game ->
                Card(
                    onClick = { onOpen(game.id) },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = game.color),
                ) {
                    Column(Modifier.padding(28.dp).height(200.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(game.emoji, fontSize = 72.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(game.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(game.blurb, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

@Composable
fun GameHost(id: String, onBack: () -> Unit) {
    val info = GAMES.firstOrNull { it.id == id } ?: return onBack()
    ScreenScaffold(title = info.title, onBack = onBack) {
        when (id) {
            "tictactoe" -> TicTacToeGame()
        }
    }
}

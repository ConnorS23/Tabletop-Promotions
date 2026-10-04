package com.tableads.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val LINES = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
    listOf(0, 4, 8), listOf(2, 4, 6),
)

internal fun ticTacToeWinner(b: List<String>): String? =
    LINES.firstOrNull { (a, c, d) -> b[a].isNotEmpty() && b[a] == b[c] && b[a] == b[d] }?.let { b[it[0]] }

/** Win if possible, else block, else center, else a corner, else anything. */
internal fun ticTacToeAiMove(b: List<String>, me: String, them: String): Int {
    val empty = b.indices.filter { b[it].isEmpty() }
    fun winningMove(p: String) = empty.firstOrNull { i -> ticTacToeWinner(b.toMutableList().also { it[i] = p }) == p }
    return winningMove(me) ?: winningMove(them) ?: empty.firstOrNull { it == 4 }
        ?: empty.filter { it in listOf(0, 2, 6, 8) }.randomOrNull() ?: empty.random()
}

@Composable
fun TicTacToeGame() {
    val board = remember { mutableStateListOf("", "", "", "", "", "", "", "", "") }
    var turn by remember { mutableStateOf("X") }
    var vsComputer by remember { mutableStateOf(true) }
    var score by remember { mutableStateOf(Triple(0, 0, 0)) }

    val winner = ticTacToeWinner(board)
    val draw = winner == null && board.all { it.isNotEmpty() }

    fun reset() {
        for (i in board.indices) board[i] = ""
        turn = "X"
    }

    LaunchedEffect(winner, draw) {
        if (winner != null) score = when (winner) {
            "X" -> score.copy(first = score.first + 1)
            else -> score.copy(second = score.second + 1)
        } else if (draw) score = score.copy(third = score.third + 1)
    }

    LaunchedEffect(turn, vsComputer, winner, draw) {
        if (vsComputer && turn == "O" && winner == null && !draw) {
            delay(450)
            board[ticTacToeAiMove(board, "O", "X")] = "O"
            turn = "X"
        }
    }

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.fillMaxHeight().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (r in 0 until 3) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (c in 0 until 3) {
                        val i = r * 3 + c
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = board[i].isEmpty() && winner == null && !(vsComputer && turn == "O")) {
                                    board[i] = turn
                                    turn = if (turn == "X") "O" else "X"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(board[i], fontSize = 96.sp, fontWeight = FontWeight.Black, color = if (board[i] == "X") Color(0xFFEF5350) else Color(0xFF4ECDC4))
                        }
                    }
                }
            }
        }
        Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                when {
                    winner != null -> "$winner wins!"
                    draw -> "It's a draw"
                    else -> "$turn's turn"
                },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text("X: ${score.first}   O: ${score.second}   Draws: ${score.third}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vsComputer, onClick = { vsComputer = true; reset() }, label = { Text("vs Tablet") })
                FilterChip(selected = !vsComputer, onClick = { vsComputer = false; reset() }, label = { Text("2 Players") })
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::reset) { Text("New game") }
        }
    }
}

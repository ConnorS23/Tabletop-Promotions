package com.tableads.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.tableads.app.data.Ad
import com.tableads.app.data.DeviceState
import kotlinx.coroutines.delay
import java.io.File

private const val LOOP_MULTIPLIER = 1000

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdsScreen(state: DeviceState, onTap: () -> Unit) {
    val ads = state.payload?.ads.orEmpty().filter { state.adFiles.containsKey(it.id) }
    val slideSeconds = state.payload?.restaurant?.slideSeconds ?: 8
    val tap = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)

    if (ads.isEmpty()) {
        Box(Modifier.fillMaxSize().then(tap), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.payload?.restaurant?.name ?: "Welcome",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Tap anywhere to view the menu & games",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        return
    }

    // A large virtual page count gives seamless wrap-around in both swipe directions.
    val pageCount = if (ads.size == 1) 1 else ads.size * LOOP_MULTIPLIER
    val pagerState = rememberPagerState(initialPage = if (ads.size == 1) 0 else ads.size * (LOOP_MULTIPLIER / 2)) { pageCount }
    var videoEndedPage by remember { mutableStateOf(-1) }

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val current = ads[pagerState.settledPage % ads.size]
    // Keyed on settledPage (not isScrollInProgress) so our own animateScrollToPage doesn't cancel itself.
    LaunchedEffect(pagerState.settledPage, isDragged, ads.size, videoEndedPage) {
        if (ads.size <= 1 || isDragged) return@LaunchedEffect
        val page = pagerState.settledPage
        if (current.isVideo && current.durationSeconds == null) {
            // Advance when the video finishes.
            if (videoEndedPage != page) return@LaunchedEffect
        } else {
            delay((current.durationSeconds ?: slideSeconds) * 1000L)
        }
        pagerState.animateScrollToPage(page + 1)
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().background(Color.Black), beyondViewportPageCount = 1) { page ->
        val ad = ads[page % ads.size]
        val file = state.adFiles[ad.id] ?: return@HorizontalPager
        Box(Modifier.fillMaxSize().then(tap)) {
            if (ad.isVideo) {
                VideoAd(file, playing = pagerState.currentPage == page, loop = ads.size == 1, onEnded = { videoEndedPage = page })
            } else {
                AsyncImage(model = file, contentDescription = ad.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoAd(file: File, playing: Boolean, loop: Boolean, onEnded: () -> Unit) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.toURI().toString()))
            volume = 0f
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(playing) {
        if (playing) {
            player.seekTo(0)
            player.play()
        } else {
            player.pause()
        }
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setBackgroundColor(android.graphics.Color.BLACK)
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

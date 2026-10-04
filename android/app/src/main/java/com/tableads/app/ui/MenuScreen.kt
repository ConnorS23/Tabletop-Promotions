package com.tableads.app.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f

@Composable
fun MenuScreen(menuFile: File?, onBack: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    ScreenScaffold(
        title = "Menu",
        onBack = onBack,
        actions = {
            FilledTonalIconButton(onClick = { scale = (scale / 1.5f).coerceAtLeast(MIN_ZOOM); if (scale == 1f) offsetX = 0f }) {
                Icon(Icons.Filled.ZoomOut, "Zoom out")
            }
            FilledTonalIconButton(onClick = { scale = (scale * 1.5f).coerceAtMost(MAX_ZOOM) }) {
                Icon(Icons.Filled.ZoomIn, "Zoom in")
            }
        },
    ) {
        if (menuFile == null) {
            CenteredMessage("Menu not available yet", "Please ask your server for a menu.")
        } else {
            PdfViewer(menuFile, scale, offsetX, onTransform = { s, x -> scale = s; offsetX = x })
        }
    }
}

private class PdfDocument(file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex()
    val pageCount = renderer.pageCount
    val aspectRatios: List<Float> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { it.width.toFloat() / it.height.toFloat() }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val height = (widthPx / aspectRatios[index]).toInt()
                val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    fun close() {
        renderer.close()
        fd.close()
    }
}

@Composable
private fun PdfViewer(file: File, scale: Float, offsetX: Float, onTransform: (Float, Float) -> Unit) {
    val doc = remember(file) { runCatching { PdfDocument(file) }.getOrNull() }
    DisposableEffect(doc) { onDispose { doc?.close() } }
    if (doc == null) {
        CenteredMessage("Could not open the menu")
        return
    }

    val listState = rememberLazyListState()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF2A2D33))) {
        val widthPx = constraints.maxWidth.toFloat()
        // Render at 2x screen width so text stays sharp when zoomed in.
        val renderWidth = (constraints.maxWidth * 2).coerceAtMost(3000)

        val maxOffset = (widthPx * (scale - 1f)) / 2f
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) onTransform(1f, 0f) else onTransform(2.5f, 0f)
                    })
                }
                .pointerInput(scale, offsetX) {
                    // Pinch to zoom anywhere; when zoomed in, one-finger drags pan the page.
                    // At 1x, single-finger drags fall through to the list for native scrolling & fling.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var currentScale = scale
                        var currentX = offsetX
                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.count { it.pressed }
                            if (pointers >= 2 || currentScale > 1f) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val newScale = (currentScale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                val limit = (widthPx * (newScale - 1f)) / 2f
                                currentX = (currentX + pan.x).coerceIn(-limit, limit)
                                currentScale = newScale
                                if (pan.y != 0f) listState.dispatchRawDelta(-pan.y / currentScale)
                                onTransform(currentScale, currentX)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX.coerceIn(-maxOffset, maxOffset)
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = scale <= 1f,
            ) {
                items(doc.pageCount) { index ->
                    val bitmap by produceState<Bitmap?>(null, doc, index, renderWidth) {
                        value = doc.render(index, renderWidth)
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).aspectRatio(doc.aspectRatios[index]).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = bitmap
                        if (bmp == null) {
                            CircularProgressIndicator()
                        } else {
                            Image(bmp.asImageBitmap(), contentDescription = "Menu page ${index + 1}", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

package com.yaz.dialer.feature.call

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.yaz.dialer.core.call.CallInfo
import com.yaz.dialer.core.call.CallStore
import kotlin.math.roundToInt

/**
 * A video call's pictures, under the glass of the call screen: the other
 * side on the whole screen, oneself in a small rounded picture the finger
 * moves anywhere. The line that carries the call (the messaging app)
 * draws into these surfaces; this screen only lends them and picks the
 * camera, which Android lets only the phone app holding the camera do.
 */
@Composable
fun VideoStage(call: CallInfo, actions: CallStore) {
    val context = LocalContext.current
    val camera by actions.camera.collectAsState()
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(call.id) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(call.id, allowed) { if (allowed) actions.camera(call.id, camera) }
    // The pictures' real sizes, from the line, to frame them without stretching.
    var remoteSize by remember { mutableStateOf(0 to 0) }
    var localSize by remember { mutableStateOf(0 to 0) }
    DisposableEffect(call.id) {
        val video = actions.videoCall(call.id)
        val sizes = object : android.telecom.InCallService.VideoCall.Callback() {
            override fun onPeerDimensionsChanged(width: Int, height: Int) { remoteSize = width to height }
            override fun onCameraCapabilitiesChanged(capabilities: android.telecom.VideoProfile.CameraCapabilities?) {
                capabilities?.let { localSize = it.width to it.height }
            }
            override fun onSessionModifyRequestReceived(videoProfile: android.telecom.VideoProfile?) = Unit
            override fun onSessionModifyResponseReceived(status: Int, requestedProfile: android.telecom.VideoProfile?, responseProfile: android.telecom.VideoProfile?) = Unit
            override fun onCallSessionEvent(event: Int) = Unit
            override fun onCallDataUsageChanged(dataUsage: Long) = Unit
            override fun onVideoQualityChanged(videoQuality: Int) = Unit
        }
        runCatching { video?.registerCallback(sizes) }
        onDispose {
            runCatching { video?.setCamera(null) }
            runCatching { video?.unregisterCallback(sizes) }
        }
    }

    val haptics = com.yaz.dialer.ui.component.rememberHaptics()
    // The other side large by default; a tap on the small picture swaps the two.
    var swapped by remember(call.id) { mutableStateOf(false) }
    val mine = camera.on && allowed
    if (!mine) swapped = false
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = 112.dp
        val height = 150.dp
        val margin = with(density) { 16.dp.toPx() }
        val maxX = with(density) { maxWidth.toPx() - width.toPx() } - margin
        val maxY = with(density) { maxHeight.toPx() - height.toPx() } - margin
        var at by remember { mutableStateOf(Offset(maxX, with(density) { 230.dp.toPx() })) }
        // The small picture: moved by the finger anywhere, a tap makes it the large one.
        val small = Modifier
            .zIndex(1f)
            .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
            .size(width, height)
            .clip(RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    at = Offset((at.x + drag.x).coerceIn(margin, maxX), (at.y + drag.y).coerceIn(margin, maxY))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    haptics.tick()
                    swapped = !swapped
                }
            }
        val large = Modifier.fillMaxSize()
        Picture(if (swapped) small else large, remoteSize) { surface -> actions.videoCall(call.id)?.setDisplaySurface(surface) }
        if (mine) {
            Picture(if (swapped) large else small, localSize) { surface -> actions.videoCall(call.id)?.setPreviewSurface(surface) }
        }
    }
}

/**
 * A surface to draw a picture in, handed over while it exists; the
 * picture of [content] size fills it, cut at the edges, never stretched.
 */
@Composable
private fun Picture(modifier: Modifier, content: Pair<Int, Int>, onSurface: (Surface?) -> Unit) {
    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    var surface: Surface? = null
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        surface = Surface(texture).also { runCatching { onSurface(it) } }
                    }
                    // Swapped large for small or back: framed again for the new size.
                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        @Suppress("UNCHECKED_CAST")
                        (tag as? Pair<Int, Int>)?.let { crop(this@apply, it) }
                    }
                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        runCatching { onSurface(null) }
                        surface?.release()
                        surface = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
        update = { view ->
            view.tag = content
            view.post { crop(view, content) }
        },
        modifier = modifier
    )
}

/** Fills the view with the picture, cutting what is beyond its edges, never stretching it. */
private fun crop(view: TextureView, content: Pair<Int, Int>) {
    val (cw, ch) = content
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    if (cw <= 0 || ch <= 0 || vw <= 0f || vh <= 0f) return
    val contentAspect = cw.toFloat() / ch
    val viewAspect = vw / vh
    val matrix = android.graphics.Matrix()
    if (contentAspect > viewAspect) matrix.setScale(contentAspect / viewAspect, 1f, vw / 2, vh / 2)
    else matrix.setScale(1f, viewAspect / contentAspect, vw / 2, vh / 2)
    view.setTransform(matrix)
}

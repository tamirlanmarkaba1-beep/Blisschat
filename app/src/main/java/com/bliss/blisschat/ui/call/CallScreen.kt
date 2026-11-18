package com.bliss.blisschat.ui.call

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.call.CallPhase
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BlissBackdrop
import com.bliss.blisschat.ui.components.BlobButton
import com.bliss.blisschat.ui.components.formatDuration
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallScreen(container: AppContainer) {
    val calls = container.calls
    val ui by calls.ui.collectAsStateWithLifecycle()
    val peer = ui.peer
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) calls.accept() else calls.decline()
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.phase) { while (ui.phase == CallPhase.ACTIVE) { now = System.currentTimeMillis(); delay(1000) } }

    val showVideo = ui.video && (ui.phase == CallPhase.ACTIVE || ui.phase == CallPhase.CONNECTING)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BlissBackdrop(0)
        if (showVideo) {
            VideoView(ui.remoteTrack, container, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            if (!showVideo || ui.remoteTrack == null) {
                Spacer(Modifier.height(48.dp))
                Avatar(peer?.avatarUrl.orEmpty(), peer?.title ?: "?", 150.dp)
            }
            Spacer(Modifier.height(18.dp))
            Text(peer?.title ?: "", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                when (ui.phase) {
                    CallPhase.OUTGOING -> "вызов…"
                    CallPhase.INCOMING -> if (ui.video) "входящий видеозвонок" else "входящий звонок"
                    CallPhase.CONNECTING -> "соединение…"
                    CallPhase.ACTIVE -> formatDuration(now - ui.since)
                    CallPhase.IDLE -> ""
                },
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("🔒 сквозное шифрование", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.weight(1f))

            if (ui.video && ui.cameraOn && ui.localTrack != null && ui.phase != CallPhase.INCOMING) {
                Box(Modifier.align(Alignment.End).size(110.dp, 150.dp).clip(RoundedCornerShape(28.dp)).background(Color.Black)) {
                    VideoView(ui.localTrack, container, Modifier.fillMaxSize(), mirror = true, overlay = true)
                }
                Spacer(Modifier.height(16.dp))
            }

            if (ui.phase == CallPhase.INCOMING) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BlobButton(Icons.Filled.CallEnd, "Отклонить", { calls.decline() }, size = 76.dp, container = MaterialTheme.colorScheme.error, content = MaterialTheme.colorScheme.onError)
                    BlobButton(
                        if (ui.video) Icons.Filled.Videocam else Icons.Filled.Call, "Принять",
                        {
                            if (calls.hasPermissions(ui.video)) calls.accept()
                            else permissions.launch(buildList { add(Manifest.permission.RECORD_AUDIO); if (ui.video) add(Manifest.permission.CAMERA) }.toTypedArray())
                        },
                        size = 76.dp, container = Color(0xFF2EB872), content = Color.White
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    BlobButton(if (ui.muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Микрофон", { calls.toggleMute() }, size = 62.dp,
                        container = if (ui.muted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondaryContainer,
                        content = if (ui.muted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSecondaryContainer)
                    BlobButton(if (ui.speaker) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, "Динамик", { calls.toggleSpeaker() }, size = 62.dp,
                        container = if (ui.speaker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        content = if (ui.speaker) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)
                    if (ui.video) {
                        BlobButton(if (ui.cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, "Камера", { calls.toggleCamera() }, size = 62.dp,
                            container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
                        BlobButton(Icons.Filled.FlipCameraAndroid, "Сменить камеру", { calls.switchCamera() }, size = 62.dp,
                            container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    BlobButton(Icons.Filled.CallEnd, "Завершить", { calls.hangup() }, size = 70.dp, container = MaterialTheme.colorScheme.error, content = MaterialTheme.colorScheme.onError)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VideoView(track: VideoTrack?, container: AppContainer, modifier: Modifier, mirror: Boolean = false, overlay: Boolean = false) {
    val egl = container.calls.egl
    val renderer = remember {
        arrayOfNulls<SurfaceViewRenderer>(1)
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(egl.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                setZOrderMediaOverlay(overlay)
                renderer[0] = this
            }
        }
    )
    DisposableEffect(track) {
        val r = renderer[0]
        if (track != null && r != null) track.addSink(r)
        onDispose { if (track != null && r != null) runCatching { track.removeSink(r) } }
    }
    DisposableEffect(Unit) {
        onDispose { renderer[0]?.release(); renderer[0] = null }
    }
}

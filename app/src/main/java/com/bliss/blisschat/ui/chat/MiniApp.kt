package com.bliss.blisschat.ui.chat

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

/** Opens a bot's mini app (title, https url). Provided by ChatScreen. */
val LocalMiniApp = staticCompositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

/** What the page can call: window.BlissApp.user() returns {"uid","name","username"}, BlissApp.close() closes the window. */
private class BlissBridge(val userJson: String, val close: () -> Unit) {
    @JavascriptInterface fun user(): String = userJson
    @JavascriptInterface fun close() { android.os.Handler(android.os.Looper.getMainLooper()).post { close.invoke() } }
}

/** A website shown inside the app, like a Telegram mini app. Only https links open. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiniAppDialog(title: String, url: String, uid: String, name: String, username: String, onClose: () -> Unit) {
    val safe = url.startsWith("https://")
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Закрыть") }
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                if (!safe) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Разрешены только ссылки https://") }
                } else {
                    val userJson = JSONObject().put("uid", uid).put("name", name).put("username", username).toString()
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean =
                                        request?.url?.scheme != "https"
                                }
                                webChromeClient = WebChromeClient()
                                addJavascriptInterface(BlissBridge(userJson, onClose), "BlissApp")
                                loadUrl(url)
                            }
                        },
                        onRelease = { it.removeJavascriptInterface("BlissApp"); it.destroy() }
                    )
                }
            }
        }
    }
}

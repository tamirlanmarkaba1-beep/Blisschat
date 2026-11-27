package com.bliss.blisschat.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bliss.blisschat.data.plugins.PluginPage
import com.bliss.blisschat.data.plugins.PluginRuntime
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.plugins.PluginSetting
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.OutlinedPillButton
import com.bliss.blisschat.ui.components.PillTextField

/** Where bots can be kept running 24/7 (see the guide). */
const val BOTKEEP_URL = "https://botkeep.cloud"

/** "Плагины": settings that code plugins added + the list of installed code plugins. */
@Composable
fun PluginSettingsPage(container: AppContainer, onOpenCatalog: () -> Unit, onBack: () -> Unit) {
    val runtime = container.plugins.runtime
    val defs by runtime.definitions.collectAsStateWithLifecycle()
    val plugins by runtime.plugins.collectAsStateWithLifecycle()
    val perms by runtime.permissions.collectAsStateWithLifecycle()
    val pages by runtime.pages.collectAsStateWithLifecycle()
    var openPage by remember { mutableStateOf<PluginPage?>(null) }
    openPage?.let { PluginPageDialog(runtime, it) { openPage = null } }

    SettingsPage("плагины", onBack) {
        Spacer(Modifier.height(4.dp))
        BouncyButton("Каталог плагинов и свой код", onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth())
        if (plugins.isEmpty()) {
            BlockLabel("пока пусто")
            SCard {
                Text(
                    "Плагины с кодом добавляют сюда свои настройки, меняют вид приложения и умеют менять текст перед отправкой. " +
                        "Установите плагин из каталога или запустите свой код.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        plugins.forEach { p ->
            BlockLabel(p.name)
            SCard {
                perms[p.id]?.forEach {
                    Text("🔐 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                pages.filter { it.pluginId == p.id }.forEach { pg ->
                    OutlinedPillButton("Открыть: ${pg.title}", onClick = { openPage = pg }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                val mine = defs.filter { it.pluginId == p.id }
                if (mine.isEmpty()) Text("У этого плагина нет настроек", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                mine.forEach { d ->
                    PluginSettingRow(d, runtime.value(d)) { runtime.setValue(d, it) }
                    Spacer(Modifier.height(10.dp))
                }
                TextButton(onClick = { runtime.remove(p.id) }) { Text("Удалить плагин", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun PluginSettingRow(d: PluginSetting, initial: String, onChange: (String) -> Unit) {
    var value by remember(d.pluginId, d.id) { mutableStateOf(initial) }
    Column(Modifier.fillMaxWidth()) {
        when (d.type) {
            "switch" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(d.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(value == "true", { value = it.toString(); onChange(value) })
            }
            "slider" -> {
                Text(d.title, style = MaterialTheme.typography.bodyLarge)
                val lo = d.min
                val hi = if (d.max > d.min) d.max else d.min + 1f
                Slider(
                    value = (value.toFloatOrNull() ?: lo).coerceIn(lo, hi),
                    onValueChange = { value = it.toString(); onChange(value) }, valueRange = lo..hi
                )
            }
            "choice" -> {
                Text(d.title, style = MaterialTheme.typography.bodyLarge)
                d.options.forEach { o ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(value == o, { value = o; onChange(o) })
                        Text(o, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            "text" -> {
                Text(d.title, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                PillTextField(value, { value = it; onChange(it) }, d.title)
            }
            "button" -> OutlinedPillButton(d.title, onClick = { onChange("click") }, modifier = Modifier.fillMaxWidth())
        }
        if (d.description.isNotBlank()) Text(d.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shows what plugins say (bliss.toast and script errors) as short toasts. Put it once into the main screen. */
@Composable
fun PluginMessages(container: AppContainer) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { container.plugins.runtime.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
}

/** How to make bots and plugins. */
@Composable
fun GuidePage(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    SettingsPage("руководство", onBack) {
        Spacer(Modifier.height(4.dp))
        GroupLabel("боты")
        SCard {
            Text("Бот — это Python-скрипт", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            GuideText(
                "1. Нажмите «+» → «Создать бота», задайте имя и username. Вы получите токен вида  my_bot:TOKEN.\n" +
                    "2. Напишите скрипт (пример лежит в репозитории: bots/example_bot.py):"
            )
            Code(
                "from blisschat_bot import Bot\nimport os\n\nbot = Bot(os.environ[\"BLISS_BOT_TOKEN\"])\n\n" +
                    "@bot.command(\"start\")\ndef start(ctx):\n    ctx.reply(\"Привет!\")\n\n" +
                    "@bot.command(\"echo\")\ndef echo(ctx):\n    ctx.reply(\" \".join(ctx.args))\n\nbot.run()"
            )
            GuideText(
                "3. Код бота можно хранить прямо в приложении: Настройки → Боты (Python) → «Код».\n" +
                    "4. Скрипт должен работать постоянно. Для этого нужен сервер: botkeep — хостинг для ботов " +
                    "(есть бесплатный тариф), там скрипт работает 24/7 без вашего телефона."
            )
            Spacer(Modifier.height(8.dp))
            BouncyButton("Открыть botkeep", onClick = { uri.openUri(BOTKEEP_URL) }, modifier = Modifier.fillMaxWidth())
        }
        GroupLabel("плагины")
        SCard {
            Text("Плагин — это JavaScript", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            GuideText(
                "Плагин управляет видом всего приложения (цвета, формы, шрифт, жидкое стекло), добавляет пункты в меню сообщений и чата, " +
                    "свои экраны, настройки, ходит в интернет и может писать в чат. Скрипт работает в изолированной песочнице, " +
                    "а все права видны пользователю в Настройки → Плагины. Каждый плагин проверяет модератор."
            )
            Text("Стиль всего приложения", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Code(STYLE_API)
            Text("Пример: жидкое стекло", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Code(GLASS_EXAMPLE)
            Text("Настройки, события, действия", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Code(FUNCTIONS_API)
            Text("Пример: кнопка «Погода» в меню чата", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Code(WEATHER_EXAMPLE)
            Text("Свой экран", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Code(PAGE_API)
            GuideText(
                "Правило безопасности: плагин либо читает сообщения (bliss.on(\"message\"), onSend), либо ходит в сеть. Оба сразу нельзя: так плагин не сможет утащить переписку.\n\n" +
                    "Свой плагин можно сразу запустить у себя: Настройки → Плагины → «Каталог плагинов и свой код» → «Создать и протестировать». " +
                    "Чтобы поделиться им, упакуйте в .zip файлы manifest.json  {\"name\":\"…\",\"version\":\"1.0\",\"type\":\"code\"}  и main.js, " +
                    "отправьте архив на проверку. Модератор одобрит его, и плагин появится в каталоге."
            )
        }
        Spacer(Modifier.width(1.dp))
    }
}

@Composable
private fun GuideText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun Code(text: String) {
    androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
    }
}


private const val STYLE_API = """bliss.app.setTheme({hue: 0..360, dark: true})
bliss.app.setTextScale(0.8..1.4)
bliss.app.setStyle({
  cornerScale: 0..2,            // скругления везде
  font: "sans|serif|mono|cursive",
  bubbleMine: "#RRGGBB",        // можно и #RRGGBBAA
  bubbleOther: "#RRGGBB",
  bubbleAlpha: 0.2..1,
  chatBackground: "#RRGGBB",
  colors: {                      // любая роль Material 3
    primary: "#..", onPrimary: "#..",
    background: "#..", surface: "#..",
    surfaceContainer: "#..", onSurface: "#..",
    secondaryContainer: "#.."
  },
  glass: {enabled: true, alpha: 0.1..0.9,
          blobs: ["#7C4DFF", "#00E5FF"], border: true}
})"""

private const val GLASS_EXAMPLE = """bliss.settings.add({id: "glass", title: "Жидкое стекло",
  type: "switch", default: "true"});
bliss.settings.add({id: "alpha", title: "Прозрачность",
  type: "slider", min: 0.1, max: 0.9, default: "0.45"});

function apply() {
  if (bliss.settings.get("glass")) {
    bliss.app.setStyle({
      cornerScale: 1.4, bubbleAlpha: 0.7,
      glass: {enabled: true, alpha: bliss.settings.get("alpha"),
              blobs: ["#7C4DFF", "#00E5FF", "#FF4081"], border: true}
    });
  } else {
    bliss.app.setStyle({});
  }
}
bliss.on("setting", apply);
apply();"""

private const val FUNCTIONS_API = """bliss.settings.add({id, title, type, default, min, max, options})
bliss.settings.get(id)
bliss.on("setting", function (id, value) {})

bliss.storage.get(key) / set(key, value) / remove(key)

bliss.on("message", function (m) {})   // m: chatId, text, sender, mine, group
bliss.chat.send(text)                  // в открытый чат, не чаще 1 раза в 1.5 с
bliss.chat.onSend(function (text) { return text })

bliss.ui.addMessageAction({id, title}, function (m) {})  // меню сообщения
bliss.ui.addChatAction({id, title}, function (c) {})     // меню чата

bliss.http.allow(["api.example.com"])  // только https и только эти хосты
bliss.http.fetch(url, {method, headers, body}).then(function (r) {
  // r.ok, r.status, r.text, r.json()
})
bliss.toast("текст")"""

private const val WEATHER_EXAMPLE = """bliss.http.allow(["api.open-meteo.com"]);
bliss.ui.addChatAction({id: "weather", title: "Погода"}, function () {
  bliss.http.fetch("https://api.open-meteo.com/v1/forecast" +
    "?latitude=51.2&longitude=51.4&current_weather=true")
    .then(function (r) {
      bliss.chat.send("Сейчас " + r.json().current_weather.temperature + "°C");
    });
});"""

private const val PAGE_API = """var n = bliss.storage.get("n") || 0;
bliss.ui.addPage({
  id: "counter", title: "Счётчик",
  render: function () {
    return [
      {type: "text", text: "Нажато: " + n, style: "title"},
      {type: "button", id: "plus", text: "Плюс"},
      {type: "input", id: "name", hint: "Имя"},
      {type: "switch", id: "sw", text: "Включено", value: true},
      {type: "list", items: [{id: "a", title: "Пункт", subtitle: "описание"}]},
      {type: "divider"}
    ];
  },
  onEvent: function (id, value) {
    if (id === "plus") { n++; bliss.storage.set("n", n); }
  }
});"""

/** Draws a page of a code plugin (bliss.ui.addPage) from the JSON nodes the plugin returns. */
@Composable
private fun PluginPageDialog(runtime: PluginRuntime, page: PluginPage, onClose: () -> Unit) {
    var nodes by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        val raw = runtime.renderPage(page.pluginId, page.id)
        nodes = runCatching {
            val a = JSONArray(raw)
            (0 until minOf(a.length(), 100)).mapNotNull { a.optJSONObject(it) }
        }.getOrDefault(emptyList())
    }
    fun event(id: String, value: String, rerender: Boolean = true) {
        runtime.pageEvent(page.pluginId, page.id, id, value)
        if (rerender) tick++
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(page.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Закрыть") }
                }
                Text(page.pluginName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (nodes.isEmpty()) Text("Плагин пока ничего не нарисовал", color = MaterialTheme.colorScheme.onSurfaceVariant)
                nodes.forEachIndexed { i, n ->
                    val id = n.optString("id")
                    when (n.optString("type")) {
                        "text" -> Text(
                            n.optString("text").take(2000),
                            style = when (n.optString("style")) {
                                "title" -> MaterialTheme.typography.titleLarge
                                "caption" -> MaterialTheme.typography.bodySmall
                                else -> MaterialTheme.typography.bodyLarge
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        "button" -> BouncyButton(n.optString("text", "OK").take(60), onClick = { event(id, "click") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        "switch" -> {
                            var on by remember(i, id) { mutableStateOf(n.optBoolean("value")) }
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(n.optString("text").take(100), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Switch(on, { on = it; event(id, it.toString()) })
                            }
                        }
                        "input" -> {
                            var v by remember(i, id) { mutableStateOf(n.optString("value")) }
                            Box(Modifier.padding(vertical = 4.dp)) { PillTextField(v, { v = it.take(2000); event(id, v, rerender = false) }, n.optString("hint").take(80)) }
                        }
                        "list" -> {
                            val items = n.optJSONArray("items") ?: JSONArray()
                            for (k in 0 until minOf(items.length(), 100)) {
                                val it = items.optJSONObject(k) ?: continue
                                Column(Modifier.fillMaxWidth().clickable { event(it.optString("id"), "click") }.padding(vertical = 8.dp)) {
                                    Text(it.optString("title").take(100), style = MaterialTheme.typography.bodyLarge)
                                    if (it.optString("subtitle").isNotEmpty()) Text(it.optString("subtitle").take(200), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        "divider" -> androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        "spacer" -> Spacer(Modifier.height(n.optInt("h", 12).coerceIn(0, 120).dp))
                    }
                }
            }
        }
    }
}

package com.viora.music

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URL

class MainActivity : ComponentActivity() {
    private var controller: MediaController? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({ controller = try { future.get() } catch (_: Exception) { null } }, ContextCompat.getMainExecutor(this))
        setContent {
            VioraUI { track ->
                controller?.apply {
                    setMediaItem(MediaItem.Builder().setUri(track.stream).setMediaMetadata(
                        MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()
                    ).build())
                    prepare()
                    play()
                }
            }
        }
    }
    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}

data class Track(val title: String, val artist: String, val stream: String)
@Composable
fun VioraUI(play: (Track) -> Unit) {
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9B8CF6), background = Color(0xFF0D0F14), surface = Color(0xFF191824))) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Viora Music", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF9B8CF6))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search music") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && query.isNotBlank(), onClick = {
                busy = true
                error = ""
                Thread {
                    try {
                        val endpoint = "https://api.audius.co/v1/tracks/search?query=" + java.net.URLEncoder.encode(query, "UTF-8") + "&app_name=VioraMusic"
                        val json = JSONObject(URL(endpoint).openText()).getJSONArray("data")
                        val found = (0 until minOf(json.length(), 30)).map { i ->
                            val t = json.getJSONObject(i)
                            Track(t.optString("title"), t.optJSONObject("user")?.optString("name") ?: "Unknown",
                                "https://api.audius.co/v1/tracks/${t.getString("id")}/stream?app_name=VioraMusic")
                        }
                        android.os.Handler(android.os.Looper.getMainLooper()).post { tracks = found; busy = false }
                    } catch (e: Exception) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { error = e.message ?: "Search failed"; busy = false }
                    }
                }.start()
            }) { Text(if (busy) "Searching…" else "Search") }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Text("Audius search · Availability depends on provider", style = MaterialTheme.typography.labelSmall)
            LazyColumn {
                items(tracks) { track ->
                    TextButton(onClick = { play(track) }, modifier = Modifier.fillMaxWidth()) {
                        Text("▶ ${track.title} — ${track.artist}")
                    }
                }
            }
        }
    }
}

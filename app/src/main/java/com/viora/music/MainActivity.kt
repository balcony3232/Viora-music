package com.viora.music

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

private val Purple = Color(0xFF9B8CF6)
private val Black = Color(0xFF08080B)
private val Surface = Color(0xFF191720)
private val Muted = Color(0xFFA8A4B0)

data class Track(val title: String, val artist: String, val stream: String, val artwork: String = "", val source: String = "Audius")

class MainActivity : ComponentActivity() {
    private var controller: MediaController? = null
    private var pendingTrack: Track? = null
    private val current = mutableStateOf<Track?>(null)
    private val playing = mutableStateOf(false)
    private val loading = mutableStateOf(false)
    private val status = mutableStateOf("")
    private val position = mutableLongStateOf(0L)
    private val duration = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playing.value = isPlaying
                        if (isPlaying) loading.value = false
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        loading.value = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_READY) {
                            duration.longValue = controller?.duration?.coerceAtLeast(0L) ?: 0L
                            status.value = ""
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        loading.value = false
                        status.value = "Playback failed: " + (error.message ?: "stream unavailable")
                    }
                })
                pendingTrack?.let { playTrack(it) }
                pendingTrack = null
            } catch (e: Exception) { status.value = "Player unavailable: " + e.message }
        }, ContextCompat.getMainExecutor(this))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Purple, background = Black, surface = Surface, onBackground = Color.White, onSurface = Color.White, onPrimary = Black, onSurfaceVariant = Muted, onSecondaryContainer = Color.White)) {
                MusicScreen(current.value, playing.value, loading.value, status.value,
                    onPlay = { playTrack(it) },
                    onToggle = { if (controller?.isPlaying == true) controller?.pause() else controller?.play() },
                    onYouTube = { query ->
                        val url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    onSoundCloud = { query ->
                        val url = "https://soundcloud.com/search?q=" + URLEncoder.encode(query, "UTF-8")
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    onSeek = { fraction ->
                        val d = controller?.duration ?: 0L
                        if (d > 0) controller?.seekTo((d * fraction).toLong())
                    },
                    onRepeat = { mode -> controller?.repeatMode = mode },
                    onShuffle = { enabled -> controller?.shuffleModeEnabled = enabled })
            }
        }
    }
    private fun playTrack(track: Track) {
        current.value = track
        loading.value = true
        status.value = "Connecting to stream…"
        val c = controller
        if (c == null) {
            pendingTrack = track
            status.value = "Connecting to audio service…"
            return
        }
        c.setMediaItem(MediaItem.Builder().setUri(track.stream).setMediaMetadata(
            MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()
        ).build())
        c.prepare()
        c.play()
    }
    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}

@Composable
private fun MusicScreen(
    current: Track?, playing: Boolean, loading: Boolean, status: String,
    onPlay: (Track) -> Unit, onToggle: () -> Unit,
    onYouTube: (String) -> Unit, onSoundCloud: (String) -> Unit,
    onSeek: (Float) -> Unit, onRepeat: (Int) -> Unit, onShuffle: (Boolean) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var busy by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var favourites by remember { mutableStateOf(setOf<String>()) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var progress by remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize().background(Black)) {
        if (expanded && current != null) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { expanded = false }) { Text("⌄  Back", color = Purple) }
                    Text("NOW PLAYING", color = Muted)
                }
                Spacer(Modifier.weight(0.6f))
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Surface, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("♫", color = Purple, style = MaterialTheme.typography.displayLarge)
                }
                Spacer(Modifier.height(28.dp))
                Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                Text(current.artist, color = Muted)
                Spacer(Modifier.height(18.dp))
                Slider(value = progress, onValueChange = { progress = it }, onValueChangeFinished = { onSeek(progress) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { shuffle = !shuffle; onShuffle(shuffle) }) { Text("⇄", color = if (shuffle) Purple else Muted) }
                    Button(onClick = onToggle, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text(if (loading) "Loading…" else if (playing) "Ⅱ Pause" else "▶ Play", color = Black) }
                    TextButton(onClick = {
                        repeat = if (repeat == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else if (repeat == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        onRepeat(repeat)
                    }) { Text(if (repeat == Player.REPEAT_MODE_ONE) "↻¹" else "↻", color = if (repeat == Player.REPEAT_MODE_OFF) Muted else Purple) }
                }
                Spacer(Modifier.weight(0.5f))
                Text(status, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(16.dp))
                Text(when(tab) { 0 -> "Good evening" ; 1 -> "Search"; else -> "Your Library" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(20.dp))
                if (tab == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Music", "Podcasts").forEach { label ->
                            Surface(shape = RoundedCornerShape(22.dp), color = if (label == "All") Purple else Surface, modifier = Modifier.clickable { if (label == "Music") tab = 1 }) {
                                Text(label, color = if (label == "All") Black else Color.White, modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    Text("Recently played", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    if (current != null) {
                        Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(8.dp)).clickable { onPlay(current) }.padding(16.dp)) {
                            Text("♫", color = Purple); Spacer(Modifier.width(14.dp)); Text(current.title, color = Color.White, maxLines = 1)
                        }
                    } else {
                        Text("Your recently played music will appear here.", color = Muted)
                    }
                    Spacer(Modifier.height(30.dp))
                    Text("Made for your listening", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Discover music and build your library as you listen.", color = Muted)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { tab = 1 }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Find music", color = Black) }
                } else if (tab == 1) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("What do you want to listen to?", color = Muted) }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = Muted, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = query.isNotBlank() && !busy, onClick = {
                            busy = true; searchError = ""
                            Thread {
                                try {
                                    val endpoint = "https://api.audius.co/v1/tracks/search?query=" + URLEncoder.encode(query, "UTF-8") + "&app_name=VioraMusic"
                                    val json = JSONObject(URL(endpoint).openConnection().apply { connectTimeout = 12000; readTimeout = 12000 }.getInputStream().bufferedReader().use { it.readText() }).getJSONArray("data")
                                    val results = (0 until minOf(json.length(), 40)).map { i ->
                                        val t = json.getJSONObject(i)
                                        Track(t.optString("title"), t.optJSONObject("user")?.optString("name") ?: "Unknown",
                                            "https://api.audius.co/v1/tracks/${t.getString("id")}/stream?app_name=VioraMusic")
                                    }
                                    android.os.Handler(android.os.Looper.getMainLooper()).post { tracks = results; busy = false }
                                } catch (e: Exception) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post { searchError = e.message ?: "Search failed"; busy = false }
                                }
                            }.start()
                        }) { Text(if (busy) "Searching…" else "Search") }
                        OutlinedButton(enabled = query.isNotBlank(), onClick = { onYouTube(query) }) { Text("YouTube") }
                    }
                    TextButton(enabled = query.isNotBlank(), onClick = { onSoundCloud(query) }) { Text("Search SoundCloud ↗", color = Purple) }
                    if (searchError.isNotBlank()) Text(searchError, color = MaterialTheme.colorScheme.error)
                    Text("Audius tracks play here · SoundCloud and YouTube open externally", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(12.dp))
                    LazyColumn {
                        items(tracks) { track ->
                            Row(Modifier.fillMaxWidth().clickable { onPlay(track) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(52.dp).background(Surface, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                    Text("♫", color = Purple)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(track.artist + " · " + track.source, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(onClick = {
                                    favourites = if (track.stream in favourites) favourites - track.stream else favourites + track.stream
                                }) { Text(if (track.stream in favourites) "♥" else "♡", color = Purple) }
                            }
                        }
                    }
                } else {
                    Text("Liked songs", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Saved in this session", color = Muted)
                    Spacer(Modifier.height(12.dp))
                    LazyColumn {
                        items(tracks.filter { it.stream in favourites }) { track ->
                            TextButton(onClick = { onPlay(track) }) { Text("♫  " + track.title + " — " + track.artist) }
                        }
                    }
                }
            }
            if (current != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).background(Surface, RoundedCornerShape(10.dp)).clickable { expanded = true }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(Color(0xFF30283D), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("♫", color = Purple) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(if (loading) "Buffering…" else current.artist, color = Muted, maxLines = 1)
                    }
                    TextButton(onClick = onToggle) { Text(if (playing) "Ⅱ" else "▶", color = Purple) }
                }
                if (status.isNotBlank()) Text(status, color = Muted, modifier = Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.labelSmall)
            }
            NavigationBar(containerColor = Color(0xFF111015)) {
                listOf("⌂" to "Home", "⌕" to "Search", "▤" to "Library").forEachIndexed { index, pair ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Text(pair.first, color = if (tab == index) Purple else Muted, style = MaterialTheme.typography.titleLarge) },
                        label = { Text(pair.second) })
                }
            }
        }
    }
}

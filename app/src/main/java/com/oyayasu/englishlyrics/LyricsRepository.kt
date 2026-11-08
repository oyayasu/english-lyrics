package com.oyayasu.englishlyrics

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String = "",
    val lyrics: String = "",
    val instrumental: Boolean = false,
    val source: String = "LRCLIB"
)

class LyricsException(message: String) : Exception(message)

class LyricsRepository {
    private val requestMutex = Mutex()
    private val clientId = "EnglishLyrics/0.1.0 (https://github.com/oyayasu/english-lyrics)"

    suspend fun search(query: String): List<Song> = requestMutex.withLock {
        withContext(Dispatchers.IO) {
            val endpoint = "https://lrclib.net/api/search?q=" + Uri.encode(query.trim())
            val data = JSONArray(request(endpoint))
            val unique = linkedMapOf<String, Song>()
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val song = parse(item)
                if (song.title.isBlank() || song.artist.isBlank()) continue
                val key = song.title.lowercase() + "|" + song.artist.lowercase()
                val existing = unique[key]
                if (existing == null || (existing.lyrics.isBlank() && song.lyrics.isNotBlank())) {
                    unique[key] = song
                }
            }
            unique.values.toList()
        }
    }

    suspend fun complete(song: Song): Song {
        if (song.lyrics.isNotBlank() || song.id <= 0L || song.instrumental) return song
        return requestMutex.withLock {
            withContext(Dispatchers.IO) {
                parse(JSONObject(request("https://lrclib.net/api/get/" + song.id)))
            }
        }
    }

    private fun request(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", clientId)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code == 429) {
                val retry = connection.getHeaderField("Retry-After")?.toIntOrNull()
                throw LyricsException(if (retry != null) "Limite temporário da busca. Tente novamente em ${retry}s." else "Limite temporário da busca. Tente mais tarde.")
            }
            if (code !in 200..299) throw LyricsException("Falha ao consultar o catálogo (HTTP $code).")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (exception: LyricsException) {
            throw exception
        } catch (exception: Exception) {
            throw LyricsException("Não foi possível acessar o catálogo. Verifique a conexão.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(item: JSONObject): Song {
        val raw = item.optString("plainLyrics").takeUnless { it == "null" }.orEmpty()
        val sync = item.optString("syncedLyrics").takeUnless { it == "null" }.orEmpty()
        return Song(
            id = item.optLong("id", 0L),
            title = item.optString("trackName", item.optString("name")),
            artist = item.optString("artistName"),
            album = item.optString("albumName"),
            lyrics = if (raw.isNotBlank()) raw.trim() else cleanTimestampedLyrics(sync),
            instrumental = item.optBoolean("instrumental"),
            source = "LRCLIB"
        )
    }
}

/** Removes LRC time tags; this app never syncs or highlights lyrics. */
fun cleanTimestampedLyrics(value: String): String {
    val tag = Regex("""\[\d{1,2}:\d{2}(?:[.:]\d+)?\]""")
    return value.lineSequence()
        .map { it.replace(tag, "").trimEnd() }
        .filterNot { it.startsWith("[ar:") || it.startsWith("[ti:") || it.startsWith("[al:") || it.startsWith("[by:") }
        .joinToString("\n")
        .trim()
}

class LyricsStore(context: Context) {
    private val file = File(context.filesDir, "recent_lyrics.json")

    @Synchronized
    fun recent(): List<Song> = try {
        val json = JSONArray(file.takeIf { it.exists() }?.readText() ?: "[]")
        buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                add(Song(
                    id = item.optLong("id"),
                    title = item.optString("title"),
                    artist = item.optString("artist"),
                    album = item.optString("album"),
                    lyrics = item.optString("lyrics"),
                    instrumental = item.optBoolean("instrumental"),
                    source = item.optString("source", "LRCLIB")
                ))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    @Synchronized
    fun save(song: Song) {
        if (song.lyrics.isBlank()) return
        val list = listOf(song) + recent().filterNot {
            it.title.equals(song.title, ignoreCase = true) && it.artist.equals(song.artist, ignoreCase = true)
        }
        val array = JSONArray()
        list.take(35).forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("title", item.title); put("artist", item.artist)
                put("album", item.album); put("lyrics", item.lyrics)
                put("instrumental", item.instrumental); put("source", item.source)
            })
        }
        val temp = File(file.parentFile, "recent_lyrics.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(file)) {
            file.writeText(array.toString())
            temp.delete()
        }
    }
}

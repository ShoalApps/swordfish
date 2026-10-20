package com.aaronparry.tube.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class Settings(
    /** 0 = follow system, 1 = light, 2 = dark */
    val theme: Int = 0,
    val dynamicColor: Boolean = true,
    val sponsorBlock: Boolean = true,
    val sbSkip: Set<String> = setOf("sponsor", "selfpromo", "interaction"),
    val deArrow: Boolean = true,
    val ryd: Boolean = true,
    val quality: Int = 1080,
    val backgroundPlay: Boolean = true,
    val autoPip: Boolean = true,
)

@Serializable
data class Sub(val url: String, val name: String, val avatar: String? = null)

/** Settings, subscriptions and history, saved on the phone. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tube", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load<Settings>("settings") ?: Settings())
    val settings: StateFlow<Settings> = _settings

    private val _subs = MutableStateFlow(load<List<Sub>>("subs") ?: emptyList())
    val subs: StateFlow<List<Sub>> = _subs

    private val _history = MutableStateFlow(load<List<VideoItem>>("history") ?: emptyList())
    val history: StateFlow<List<VideoItem>> = _history

    fun updateSettings(change: (Settings) -> Settings) {
        _settings.update(change)
        save("settings", _settings.value)
    }

    fun isSubscribed(url: String?) = url != null && _subs.value.any { it.url == url }

    fun toggleSub(sub: Sub) {
        _subs.update { list ->
            if (list.any { it.url == sub.url }) list.filterNot { it.url == sub.url } else list + sub
        }
        save("subs", _subs.value)
    }

    fun addHistory(item: VideoItem) {
        _history.update { list -> (listOf(item) + list.filterNot { it.id == item.id }).take(300) }
        save("history", _history.value)
    }

    fun clearHistory() {
        _history.value = emptyList()
        save("history", _history.value)
    }

    private inline fun <reified T> load(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> save(key: String, value: T) {
        prefs.edit().putString(key, json.encodeToString(value)).apply()
    }
}

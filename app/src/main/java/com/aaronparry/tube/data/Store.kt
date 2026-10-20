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
    val qualityWifi: Int = 1080,
    val qualityMobile: Int = 1080,
    val backgroundPlay: Boolean = true,
    val autoPip: Boolean = true,
)

@Serializable
data class Sub(val url: String, val name: String, val avatar: String? = null)

@Serializable
data class Account(val name: String, val handle: String? = null, val avatar: String? = null)

/** Settings, subscriptions, history and sign-in, saved privately on the phone. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tube", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load<Settings>("settings") ?: Settings())
    val settings: StateFlow<Settings> = _settings

    private val _subs = MutableStateFlow(load<List<Sub>>("subs") ?: emptyList())
    val subs: StateFlow<List<Sub>> = _subs

    private val _history = MutableStateFlow(load<List<VideoItem>>("history") ?: emptyList())
    val history: StateFlow<List<VideoItem>> = _history

    private val _cookie = MutableStateFlow(prefs.getString("cookie", null))
    val cookie: StateFlow<String?> = _cookie

    private val _pageId = MutableStateFlow(prefs.getString("pageId", null))
    /** Which Brand Account channel to act as; null means the main channel. */
    val pageId: StateFlow<String?> = _pageId

    private val _account = MutableStateFlow(load<Account>("account"))
    val account: StateFlow<Account?> = _account

    val signedIn: Boolean get() = _cookie.value?.contains("SAPISID") == true

    fun updateSettings(change: (Settings) -> Settings) {
        _settings.update(change)
        save("settings", _settings.value)
    }

    fun isSubscribed(url: String?) = url != null && _subs.value.any { it.url == url }

    /** Returns true if now subscribed. */
    fun toggleSub(sub: Sub): Boolean {
        val nowSubscribed = !isSubscribed(sub.url)
        _subs.update { list -> if (nowSubscribed) list + sub else list.filterNot { it.url == sub.url } }
        save("subs", _subs.value)
        return nowSubscribed
    }

    fun mergeSubs(incoming: List<Sub>) {
        _subs.update { list ->
            val known = list.map { it.url }.toSet()
            list + incoming.filterNot { it.url in known }
        }
        save("subs", _subs.value)
    }

    fun replaceSubs(list: List<Sub>) {
        _subs.value = list
        save("subs", list)
    }

    fun setPageId(id: String?) {
        _pageId.value = id
        prefs.edit().putString("pageId", id).apply()
    }

    fun addHistory(item: VideoItem) {
        _history.update { list -> (listOf(item) + list.filterNot { it.id == item.id }).take(300) }
        save("history", _history.value)
    }

    fun clearHistory() {
        _history.value = emptyList()
        save("history", _history.value)
    }

    fun setCookie(cookie: String?) {
        _cookie.value = cookie
        prefs.edit().putString("cookie", cookie).apply()
    }

    fun setAccount(account: Account?) {
        _account.value = account
        if (account == null) prefs.edit().remove("account").apply() else save("account", account)
    }

    private inline fun <reified T> load(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> save(key: String, value: T) {
        prefs.edit().putString(key, json.encodeToString(value)).apply()
    }
}

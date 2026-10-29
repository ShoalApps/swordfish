package io.github.shoalapps.swordfish.data

import android.content.Context
import android.util.AtomicFile
import io.github.shoalapps.swordfish.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class OfflineAction(
    val id: String = UUID.randomUUID().toString(),
    val account: String,
    val kind: String,
    val target: String,
    val value: String = "",
    val extra: String = "",
    val state: String = "pending",
) {
    val repeatable get() = kind in setOf("subscription", "bell", "rating")
}

/** Desired-state changes collapse; comments remain separate and keep their order. */
object ActionPolicy {
    fun add(list: List<OfflineAction>, action: OfflineAction): List<OfflineAction> =
        list.filterNot { action.repeatable && it.state in setOf("pending", "failed") && it.account == action.account &&
            it.kind == action.kind && it.target == action.target } + action
    fun recover(list: List<OfflineAction>) = list.map {
        if (it.state == "sending") it.copy(state = if (it.repeatable) "pending" else "review") else it
    }
}

/** Private, durable outbox. It never contains sign-in cookies or expiring comment tokens. */
class OfflineActions(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "offline-actions.json"))
    private val snapshots = context.getSharedPreferences("offline-playlists", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _actions = MutableStateFlow(ActionPolicy.recover(runCatching {
        json.decodeFromString<List<OfflineAction>>(file.openRead().bufferedReader().use { it.readText() })
    }.getOrDefault(emptyList())))
    val actions: StateFlow<List<OfflineAction>> = _actions

    fun playlist(key: String): List<VideoItem> {
        val owner = identity() ?: return emptyList()
        val cached = runCatching { json.decodeFromString<List<VideoItem>>(snapshots.getString(owner + key, "[]")!!) }.getOrDefault(emptyList())
        val saved = AppGraph.offline.videos.value.filter { it.complete }.map { it.video }
        val pending = actions.value.filter { it.account == owner }
        val ids = cached.map { it.id }.toMutableSet()
        pending.forEach {
            if (key == "VLLL" && it.kind == "rating") { if (it.value == "like") ids.add(it.target) else ids.remove(it.target) }
            if (key == "VLWL" && it.kind == "watchLater") ids.add(it.target)
        }
        return FeedFilter.apply(saved.filter { it.id in ids })
    }
    fun rememberPlaylist(key: String, list: List<VideoItem>) {
        val owner = identity() ?: return
        snapshots.edit().putString(owner + key, json.encodeToString(list.take(300))).apply()
    }

    /** A changed sign-in or channel cannot inherit another account's queued writes. */
    fun identity(cookie: String? = AppGraph.store.cookie.value, page: String? = AppGraph.store.pageId.value): String? {
        if (cookie == null || !cookie.contains("SAPISID")) return null
        val sid = cookie.split(';').map { it.trim() }.firstOrNull { it.startsWith("SAPISID=") }
            ?: cookie.split(';').map { it.trim() }.firstOrNull { it.startsWith("__Secure-3PAPISID=") }
            ?: return null
        return MessageDigest.getInstance("SHA-256").digest((sid + "|" + page).toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    @Synchronized private fun save(list: List<OfflineAction>) {
        val stream = file.startWrite()
        try {
            stream.write(json.encodeToString(list).toByteArray())
            file.finishWrite(stream)
            _actions.value = list
        } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    @Synchronized private fun change(id: String, state: String?) {
        save(_actions.value.mapNotNull { if (it.id != id) it else state?.let { s -> it.copy(state = s) } })
    }
    @Synchronized fun discard(id: String) {
        if (_actions.value.none { it.id == id && it.state == "sending" }) change(id, null)
    }
    @Synchronized fun retry(id: String) {
        if (_actions.value.none { it.id == id && it.state == "sending" }) change(id, "pending")
    }
    suspend fun submit(kind: String, target: String, value: String = "", extra: String = "") {
        val owner = identity() ?: throw IOException("Sign in before saving an account action")
        synchronized(this) {
            save(ActionPolicy.add(_actions.value, OfflineAction(account = owner, kind = kind, target = target, value = value, extra = extra)))
        }
        schedule()
        AppGraph.scope.launch { flush() }
    }

    fun schedule() {
        if (_actions.value.none { it.state in setOf("pending", "failed") && it.account == identity() }) return
        val request = androidx.work.OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS).build()
        androidx.work.WorkManager.getInstance(AppGraph.app).enqueueUniqueWork("offline-account-sync", androidx.work.ExistingWorkPolicy.KEEP, request)
    }

    suspend fun flush() = mutex.withLock {
        while (AppGraph.online.value && NetworkPolicy.backgroundAllowed()) {
            val owner = identity() ?: break
            val action = synchronized(this) {
                _actions.value.firstOrNull { it.account == owner && it.state == "pending" }?.also { change(it.id, "sending") }
            } ?: break
            try {
                if (identity() != owner) { change(action.id, "pending"); break }
                AppGraph.account.perform(action)
                change(action.id, null)
            } catch (e: CancellationException) {
                change(action.id, if (action.repeatable) "pending" else "review")
                throw e
            } catch (_: Exception) {
                change(action.id, if (action.repeatable) "failed" else "review")
                break
            }
        }
    }
    @Synchronized fun reconnect() {
        save(_actions.value.map { if (it.state == "failed") it.copy(state = "pending") else it })
    }
}


/** Ordinary constrained work: account replay does not start a foreground download service. */
class OfflineSyncWorker(context: Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!NetworkPolicy.backgroundAllowed() || io.github.shoalapps.swordfish.player.NetworkInfo.offline()) return Result.retry()
        AppGraph.actions.reconnect()
        AppGraph.actions.flush()
        return if (AppGraph.actions.actions.value.any { it.account == AppGraph.actions.identity() && it.state in setOf("pending", "failed") })
            Result.retry() else Result.success()
    }
}

internal class ActionOwner(val identity: String) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<ActionOwner>
}

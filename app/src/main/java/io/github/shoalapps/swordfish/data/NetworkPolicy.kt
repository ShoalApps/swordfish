package io.github.shoalapps.swordfish.data

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.shoalapps.swordfish.AppGraph

/** One policy for players, images, downloads, preloading, updates and background sync. */
object NetworkPolicy {
    val changes = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun changed() { changes.value++ }
    fun metered(): Boolean = AppGraph.app.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered != false
    fun savingData(): Boolean = metered() &&
        AppGraph.app.getSystemService(ConnectivityManager::class.java)?.restrictBackgroundStatus ==
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED

    fun backgroundAllowed(): Boolean = !io.github.shoalapps.swordfish.player.NetworkInfo.offline() && !savingData()

    fun downloadsAllowed(): Boolean {
        if (savingData() || io.github.shoalapps.swordfish.player.NetworkInfo.offline()) return false
        if (OfflineVideos.wifi()) return true
        val cm = AppGraph.app.getSystemService(ConnectivityManager::class.java) ?: return false
        return !AppGraph.store.settings.value.downloadWifiOnly &&
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    fun quality(settings: Settings = AppGraph.store.settings.value): Int =
        quality(settings, metered(), savingData())

    fun quality(settings: Settings, metered: Boolean, saving: Boolean): Int =
        (if (metered) settings.qualityMobile else settings.qualityWifi).let { if (saving) it.coerceAtMost(720) else it }
}

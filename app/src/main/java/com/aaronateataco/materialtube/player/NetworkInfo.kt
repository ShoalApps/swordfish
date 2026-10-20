package com.aaronateataco.materialtube.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.aaronateataco.materialtube.AppGraph

object NetworkInfo {
    /** True when traffic is going through a VPN (or a VPN-style proxy app). */
    fun onVpn(): Boolean = runCatching {
        val cm = AppGraph.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)

    fun friendlyError(raw: String?): String {
        val msg = raw.orEmpty()
        val bot = msg.contains("not a bot", true) || msg.contains("LOGIN_REQUIRED")
        return when {
            bot && onVpn() -> "YouTube is blocking your VPN's address. Exclude MaterialTube from the VPN with split tunnelling, then try again."
            bot -> "YouTube is asking this network to prove it isn't a bot. Try again shortly."
            msg.isBlank() -> "Something went wrong"
            else -> msg
        }
    }
}

// Adapted from NewPipe (github.com/TeamNewPipe/NewPipe, app/.../util/potoken), GPL-3.0-or-later.
// Generates YouTube "proof of origin" tokens by running YouTube's BotGuard in a hidden WebView.
package io.github.shoalapps.swordfish.data.potoken

class PoTokenException(message: String) : Exception(message)

// to be thrown if the WebView provided by the system is broken
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception {
    return if (error.contains("SyntaxError")) {
        BadWebViewException(error)
    } else {
        PoTokenException(error)
    }
}

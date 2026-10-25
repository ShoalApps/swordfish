// Raw sockets (VacuumTube's DIAL casting server) don't exist in a WebView, so casting stays off on
// Android. The DIAL code creates its socket and server when it loads, so these are inert objects
// that accept the calls and never do anything.
function inert() {
    const obj = {}
    for (const name of ['on', 'once', 'off', 'bind', 'listen', 'close', 'send', 'setBroadcast', 'setMulticastTTL', 'addMembership', 'dropMembership', 'address', 'removeAllListeners', 'end', 'write']) {
        obj[name] = () => obj
    }
    return obj
}
module.exports = {
    createSocket: inert,
    createServer: inert,
    request: inert,
    get: inert,
    randomUUID: () => crypto.randomUUID(),
}

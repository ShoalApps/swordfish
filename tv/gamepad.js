// Android's WebView has no Gamepad API, so the app reads Bluetooth/USB controllers natively
// (TvActivity) and feeds their state here as one standard-layout gamepad. VacuumTube's controller
// module then works unchanged: YouTube's TV app maps standard buttons (32768 + index) itself.
const pad = {
    id: 'Swordfish controller (STANDARD GAMEPAD)',
    index: 0,
    connected: false,
    mapping: 'standard',
    timestamp: 0,
    axes: [0, 0, 0, 0],
    buttons: Array.from({ length: 17 }, () => ({ pressed: false, touched: false, value: 0 })),
}

// VacuumTube polls once per frame; a tap shorter than a frame would be missed, so a press stays
// visible until it has been read at least once.
const values = new Array(17).fill(0)
const unread = new Array(17).fill(false)

function publish() {
    for (let i = 0; i < pad.buttons.length; i++) {
        const v = unread[i] ? Math.max(values[i], 1) : values[i]
        pad.buttons[i] = { pressed: v > 0.5, touched: v > 0, value: v }
    }
}

window.__swordfishPad = (buttons, axes) => {
    pad.connected = true
    pad.timestamp = performance.now()
    for (let i = 0; i < values.length; i++) {
        const v = buttons[i] || 0
        if (v > 0.5 && values[i] <= 0.5) unread[i] = true
        values[i] = v
    }
    pad.axes = axes.slice(0, 4)
    publish()
}
window.__swordfishPadGone = () => { pad.connected = false }

navigator.getGamepads = () => {
    if (!pad.connected) return [null, null, null, null]
    const snapshot = { ...pad, buttons: pad.buttons.slice(), axes: pad.axes.slice() }
    if (unread.some(Boolean)) {
        unread.fill(false)
        publish()
    }
    return [snapshot, null, null, null]
}

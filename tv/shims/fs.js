// Read-only files baked into the bundle at build time (CSS, translations, the About icon).
const files = require('vt:files')

function norm(p) { return require('path').normalize(p) }

function file(p) {
    const key = norm(p)
    if (!(key in files)) {
        const err = new Error(`ENOENT: ${key}`)
        err.code = 'ENOENT'
        throw err
    }
    const entry = files[key]
    return {
        text: entry.text,
        base64: entry.base64,
    }
}

function readFileSync(p, encoding) {
    const f = file(p)
    if (f.text !== undefined) return encoding ? f.text : { toString: (enc) => (enc === 'base64' ? btoa(unescape(encodeURIComponent(f.text))) : f.text) }
    return encoding ? atob(f.base64) : { toString: (enc) => (enc === 'base64' ? f.base64 : atob(f.base64)) }
}

function readdirSync(dir, opts) {
    const prefix = norm(dir).replace(/\/$/, '') + '/'
    const names = new Set()
    const dirs = new Set()
    for (const key of Object.keys(files)) {
        if (!key.startsWith(prefix)) continue
        const rest = key.slice(prefix.length)
        const name = rest.split('/')[0]
        names.add(name)
        if (rest.includes('/')) dirs.add(name)
    }
    const list = [...names].sort()
    if (opts && opts.withFileTypes) return list.map(name => ({ name, isDirectory: () => dirs.has(name), isFile: () => !dirs.has(name) }))
    return list
}

module.exports = {
    readFileSync,
    readdirSync,
    existsSync: (p) => norm(p) in files,
    promises: {
        readFile: async (p, enc) => readFileSync(p, enc),
        readdir: async (p, opts) => readdirSync(p, opts),
    },
}

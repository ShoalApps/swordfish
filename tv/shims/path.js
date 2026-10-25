// Enough of Node's posix path for VacuumTube's preload code.
function normalize(p) {
    const abs = p.startsWith('/')
    const out = []
    for (const part of p.split('/')) {
        if (!part || part === '.') continue
        if (part === '..') { if (out.length && out[out.length - 1] !== '..') out.pop(); else if (!abs) out.push('..') } else out.push(part)
    }
    return (abs ? '/' : '') + out.join('/')
}
const join = (...parts) => normalize(parts.filter(Boolean).join('/'))
const dirname = (p) => { const n = normalize(p); const i = n.lastIndexOf('/'); return i <= 0 ? (n.startsWith('/') ? '/' : '.') : n.slice(0, i) }
const basename = (p, ext) => { let b = normalize(p).split('/').pop() || ''; if (ext && b.endsWith(ext)) b = b.slice(0, -ext.length); return b }
const extname = (p) => { const b = basename(p); const i = b.lastIndexOf('.'); return i > 0 ? b.slice(i) : '' }
module.exports = { normalize, join, resolve: join, dirname, basename, extname, sep: '/' }

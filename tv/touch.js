// Android touch acts on what is under the finger, rather than pressing Enter on the TV focus.
const overrides = require('./vacuumtube/preload/util/configOverrides')
const css = require('./vacuumtube/preload/util/css')

function scrollTarget(node) {
    for (let el = node; el && el !== document.body; el = el.parentElement) {
        const style = getComputedStyle(el)
        if ((el.scrollHeight > el.clientHeight + 2 && /auto|scroll/.test(style.overflowY)) ||
            (el.scrollWidth > el.clientWidth + 2 && /auto|scroll/.test(style.overflowX))) return el
    }
    return null
}
function press(code) {
    for (const type of ['keydown', 'keyup']) {
        const event = new Event(type, { bubbles: true })
        Object.defineProperty(event, 'keyCode', { value: code })
        Object.defineProperty(event, 'key', { value: ({32780:'ArrowUp',32781:'ArrowDown',32782:'ArrowLeft',32783:'ArrowRight'})[code] })
        document.dispatchEvent(event)
    }
}
module.exports = () => {
    overrides.environmentOverrides.push({ flags: { enable_touch_support: true } })
    css.inject('android-touch', 'yt-virtual-list,.ogHbMc{touch-action:none!important} #vt-settings-overlay-root.vt-settings-hidden{display:none!important}')
    let gesture = null, suppressClickUntil = 0
    window.addEventListener('pointerdown', e => {
        if (e.pointerType !== 'touch' || !e.isPrimary) return
        // VacuumTube's settings have their own touch drag and click handlers.
        if (e.target.closest('#vt-settings-overlay-root')) return
        document.documentElement.classList.add('vt-touch-input')
        gesture = { id:e.pointerId, target:e.target, scroll:scrollTarget(e.target), x:e.clientX, y:e.clientY, startX:e.clientX, startY:e.clientY, moved:false, remainder:0 }
        e.preventDefault(); e.stopImmediatePropagation()
    }, { capture:true, passive:false })
    window.addEventListener('pointermove', e => {
        if (!gesture || e.pointerId !== gesture.id) return
        const g=gesture, dx=e.clientX-g.x, dy=e.clientY-g.y
        g.x=e.clientX; g.y=e.clientY
        if (Math.hypot(e.clientX-g.startX,e.clientY-g.startY)>8) g.moved=true
        if (g.moved) {
            if (g.scroll) g.scroll.scrollBy(-dx,-dy)
            else {
                const vertical=Math.abs(dy)>=Math.abs(dx)
                g.remainder+=vertical?dy:dx
                while (Math.abs(g.remainder)>=48) {
                    press(vertical?(g.remainder<0?32781:32780):(g.remainder<0?32783:32782))
                    g.remainder+=g.remainder<0?48:-48
                }
            }
        }
        e.preventDefault(); e.stopImmediatePropagation()
    }, { capture:true, passive:false })
    const finish = e => {
        if (!gesture || e.pointerId!==gesture.id) return
        const g=gesture; gesture=null; suppressClickUntil=Date.now()+400
        e.preventDefault(); e.stopImmediatePropagation()
        if (e.type==='pointercancel' || g.moved) return
        const hit=document.elementFromPoint(e.clientX,e.clientY)
        if (!hit || !(g.target===hit || g.target.contains(hit) || hit.contains(g.target))) return
        const node=hit.closest('.vt-focusable,a,button,[role="button"],[tabindex],.yt-focusable') || hit
        node.focus?.({preventScroll:true})
        for (const type of ['mouseover','mousemove','mousedown','mouseup','click']) node.dispatchEvent(new MouseEvent(type,{bubbles:true,cancelable:true,view:window,clientX:e.clientX,clientY:e.clientY,button:0}))
    }
    window.addEventListener('pointerup',finish,{capture:true,passive:false})
    window.addEventListener('pointercancel',finish,{capture:true,passive:false})
    window.addEventListener('click',e=>{if(e.isTrusted&&Date.now()<suppressClickUntil){e.preventDefault();e.stopImmediatePropagation()}},true)
    window.addEventListener('blur',()=>{gesture=null})
}

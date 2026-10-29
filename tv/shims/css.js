// Constructed sheets work inside YouTube's CSP. Keep a style-element fallback for older WebViews.
const sheets = new Map()
function apply() {
    if ('adoptedStyleSheets' in document && typeof CSSStyleSheet.prototype.replaceSync === 'function') {
        const ours = [...sheets.values()].map(x=>x.sheet).filter(Boolean)
        document.adoptedStyleSheets = [...document.adoptedStyleSheets.filter(x=>!x.__shoal),...ours]
    }
}
async function inject(id,text) {
    remove(id)
    if ('adoptedStyleSheets' in document && typeof CSSStyleSheet.prototype.replaceSync === 'function') {
        const sheet = new CSSStyleSheet(); sheet.__shoal=true; sheet.replaceSync(text)
        sheets.set(id,{sheet}); apply()
    } else {
        if (!document.head) await new Promise(resolve=>document.addEventListener('DOMContentLoaded',resolve,{once:true}))
        const node=document.createElement('style'); node.id=`vt-${id}`
        const nonce=document.querySelector('style[nonce],script[nonce]')?.nonce
        if(nonce) node.nonce=nonce
        node.textContent=text; document.head.append(node); sheets.set(id,{node})
    }
}
function remove(id) {const old=sheets.get(id);old?.node?.remove();sheets.delete(id);apply()}
module.exports={inject,delete:remove}

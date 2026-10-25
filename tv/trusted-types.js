// YouTube's TV page requires Trusted Types. In Electron, VacuumTube's preload runs in an isolated
// world where that doesn't apply; in a WebView it shares the page's world, so its innerHTML and
// DOMParser calls need a default policy. YouTube itself only uses named policies.
if (window.trustedTypes && window.trustedTypes.createPolicy && !window.trustedTypes.defaultPolicy) {
    try {
        window.trustedTypes.createPolicy('default', { createHTML: (s) => s, createScript: (s) => s, createScriptURL: (s) => s })
    } catch (err) {
        console.error('[swordfish] could not create the default Trusted Types policy', err)
    }
}

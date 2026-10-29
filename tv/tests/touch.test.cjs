const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path')
function setup() {
    const handlers={},events=[],scrolls=[],classes=new Set()
    const node={parentElement:null,scrollHeight:1000,clientHeight:300,scrollWidth:300,clientWidth:300,contains:x=>x===node,closest:selector=>selector==='#vt-settings-overlay-root'?null:node,focus:()=>{},dispatchEvent:e=>events.push(e.type),scrollBy:(x,y)=>scrolls.push([x,y])}
    const context={module:{exports:{}},require:id=>id.endsWith('configOverrides')?{environmentOverrides:[]}:{inject:()=>{}},getComputedStyle:()=>({overflowY:'auto',overflowX:'hidden'}),MouseEvent:class{constructor(type,args){this.type=type;Object.assign(this,args)}},Event:class{constructor(type){this.type=type}},Date,Math,Object,
      document:{body:{},documentElement:{classList:{add:x=>classes.add(x)}},elementFromPoint:()=>node,dispatchEvent:e=>events.push(e.type)},
      window:{addEventListener:(type,fn)=>handlers[type]=fn}}
    vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../touch.js'),'utf8'),context);context.module.exports()
    const emit=(type,x=0,y=0)=>handlers[type]({type,pointerId:1,pointerType:'touch',isPrimary:true,target:node,clientX:x,clientY:y,preventDefault(){},stopImmediatePropagation(){}})
    return{emit,events,scrolls}
}
test('tap activates the touched item once',()=>{const h=setup();h.emit('pointerdown',20,20);h.emit('pointerup',20,20);assert.deepEqual(h.events,['mouseover','mousemove','mousedown','mouseup','click'])})
test('drag scrolls the list and never opens a video',()=>{const h=setup();h.emit('pointerdown',20,100);h.emit('pointermove',20,40);h.emit('pointerup',20,40);assert.deepEqual(h.scrolls,[[-0,60]]);assert.equal(h.events.length,0)})
test('cancelled touch never clicks',()=>{const h=setup();h.emit('pointerdown');h.emit('pointercancel');assert.equal(h.events.length,0)})

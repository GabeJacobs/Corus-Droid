// Execute the production MapKit page with a deterministic DOM/map stand-in.
// These checks measure work and data preservation, not device frame rate.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
const source = readFileSync(new URL('../app/src/main/java/fm/corus/android/ui/screens/map/AppleCityMapView.kt', import.meta.url), 'utf8');
const inline = source.slice(source.indexOf('<!doctype html>')).match(/<script>([\s\S]*?)<\/script>/)[1];
class Element {
  constructor() {
    this.children=[]; this.parentNode=null; this.dataset={}; this.className=''; this.textContent='';
    this.style={setProperty(){}}; this.clientWidth=400; this.clientHeight=600;
    this.classList={contains:n=>this.className.split(' ').includes(n),
      add:(...names)=>{this.className=[...new Set([...this.className.split(' ').filter(Boolean),...names])].join(' ')},
      remove:(...names)=>{this.className=this.className.split(' ').filter(n=>!names.includes(n)).join(' ')},
      toggle:(name,on)=>{on??=!this.classList.contains(name);on?this.classList.add(name):this.classList.remove(name)}};
  }
  get childNodes(){return this.children}
  get isConnected(){return !!this.parentNode}
  append(...elements){for(const el of elements){el.remove();el.parentNode=this;this.children.push(el)}}
  appendChild(el){this.append(el);return el}
  remove(){if(this.parentNode)this.parentNode.children=this.parentNode.children.filter(c=>c!==this);this.parentNode=null}
  replaceChildren(...elements){for(const c of [...this.children])c.remove();this.append(...elements)}
  replaceWith(el){const parent=this.parentNode;if(!parent)return;const index=parent.children.indexOf(this);el.remove();parent.children[index]=el;el.parentNode=parent;this.parentNode=null}
  querySelector(selector){for(const child of this.children){if(child.classList.contains(selector.slice(1)))return child;const found=child.querySelector(selector);if(found)return found}return null}
  getBoundingClientRect(){return {left:0,top:0,right:0,bottom:0,width:0,height:0}}
}
async function page() {
  const callbacks=new Map(), frames=new Map(), calls={added:0,removed:0,settled:0};let frameId=0,map;
  const root=new Element(),body=new Element(),container=new Element();
  const context=vm.createContext({
    document:{documentElement:root,body,getElementById:()=>container,createElement:()=>new Element(),addEventListener(){}},
    innerWidth:400,innerHeight:600,addEventListener(){},DOMPoint:class{constructor(x,y){this.x=x;this.y=y}},
    requestAnimationFrame:fn=>{frames.set(++frameId,fn);return frameId},cancelAnimationFrame:id=>frames.delete(id),
    setTimeout:fn=>{fn();return 1},clearTimeout(){},
    CorusAndroidMap:{loadState(){},markersRendered(){},cameraSettled(){calls.settled++},selectCity(){}},
    mapkit:{addEventListener(){},load:async()=>({MapType:{Standard:'standard'},ColorScheme:{Light:'light',Dark:'dark'},
      Map:class{
        constructor(_,options){map=this;this.region=options.region;this.items=[]}
        addEventListener(name,fn){callbacks.set(name,fn)}
        addAnnotation(a){this.items.push(a);calls.added++}
        removeAnnotation(a){this.items=this.items.filter(v=>v!==a);calls.removed++}
        setRegionAnimated(region){this.region=region}
      },
      Annotation:class{constructor(coordinate,view,options){this.coordinate=coordinate;Object.assign(this,options);this.element=view()}}
    })}
  });
  context.window=context;
  vm.runInContext(inline.replace('$gestures','true').replace('${if (compact) "true" else "false"}','false'),context);
  context.initMapKitLoaderV2();await new Promise(resolve=>setImmediate(resolve));
  map.region={center:{latitude:40,longitude:-74},span:{latitudeDelta:2,longitudeDelta:2}};
  return {context,map,calls,callbacks,frame(time=1000){const pending=[...frames.values()];frames.clear();for(const fn of pending)fn(time)}};
}
function city(id,latitude=40,longitude=-74){return{id,name:id,latitude,longitude,count:1,faces:[{id:'u-'+id,name:id,avatar:'https://example.invalid/'+id,artwork:''}]}}
function payload(cities,focusId=null){const focus=cities.find(c=>c.id===focusId);return{cities,dark:false,focus:focus?{id:focus.id,latitude:focus.latitude,longitude:focus.longitude}:null,preserveCameraOnFocus:true}}

test('full map mounts nearby cities and its offscreen focus, not the global directory',async()=>{
  const {context,map}=await page();
  const cities=[city('near'),city('far',-35,145),city('target',51,0)];
  context.CorusAppleMap.update(payload(cities,'target'));
  assert.deepEqual(Array.from(map.items,a=>a.corusId).sort(),['near','target']);
  assert.equal(context.CorusAppleMap.data.cities.length,3,'Culling must retain the complete directory');
});
test('identical data does no annotation/layout work',async()=>{
  const {context,map}=await page();const data=payload([city('near')]);
  context.CorusAppleMap.update(data);const button=map.items[0].corusButton;
  let checks=0;const remove=button.classList.remove;button.classList.remove=(...args)=>{checks++;return remove(...args)};
  context.CorusAppleMap.update(JSON.parse(JSON.stringify(data)));
  assert.equal(checks,0,'Duplicate updates must not reapply marker classes');
});
test('focus and artwork changes retain avatar DOM, while changed avatars refresh',async()=>{
  const {context,map}=await page();const cities=[city('a'),city('b',40.1,-74)];
  context.CorusAppleMap.update(payload(cities,'a'));const a=map.items.find(a=>a.corusId==='a'),b=map.items.find(a=>a.corusId==='b');
  const aFaces=a.corusButton.querySelector('.faces'),bFaces=b.corusButton.querySelector('.faces');
  context.CorusAppleMap.update(payload(cities,'b'));
  assert.equal(a.corusButton.querySelector('.faces'),aFaces,'Focus must not recreate outgoing avatars');
  assert.equal(b.corusButton.querySelector('.faces'),bFaces,'Focus must not recreate incoming avatars');
  const withArt=JSON.parse(JSON.stringify(cities));withArt[1].faces[0].artwork='https://example.invalid/cover';
  context.CorusAppleMap.update(payload(withArt,'b'));
  assert.equal(b.corusButton.querySelector('.faces'),bFaces,'Artwork arrival must not recreate avatars');
  assert.ok(b.corusButton.querySelector('.art-fan'));
  withArt[1].faces[0].avatar='https://example.invalid/new-avatar';
  context.CorusAppleMap.update(payload(withArt,'b'));
  assert.notEqual(b.corusButton.querySelector('.faces'),bFaces,'Real avatar changes must propagate');
});
test('long pans replenish the buffer before settling without sending camera callbacks per frame',async()=>{
  const {context,map,callbacks,calls,frame}=await page();
  context.CorusAppleMap.update(payload([city('start'),city('destination',48,-74)]));
  assert.ok(!map.items.some(a=>a.corusId==='destination'),'Destination should start outside the buffer');
  callbacks.get('region-change-start')?.();map.region={...map.region,center:{latitude:48,longitude:-74}};
  frame(1000);frame(1200);
  assert.ok(map.items.some(a=>a.corusId==='destination'),'Long drag must populate its approaching viewport');
  assert.equal(calls.settled,0);
  callbacks.get('region-change-end')();assert.equal(calls.settled,1);
});
test('date-line wrap, zoom-out and removed cities keep the right markers',async()=>{
  const {context,map,callbacks}=await page();
  map.region={center:{latitude:0,longitude:179},span:{latitudeDelta:2,longitudeDelta:2}};
  const cities=[city('wrapped',0,-179.8),city('far',0,160)];
  context.CorusAppleMap.update(payload(cities));
  assert.deepEqual(Array.from(map.items,a=>a.corusId),['wrapped']);
  map.region={...map.region,span:{latitudeDelta:40,longitudeDelta:50}};callbacks.get('region-change-end')();
  assert.equal(map.items.length,2);
  context.CorusAppleMap.update(payload([cities[1]]));assert.deepEqual(Array.from(map.items,a=>a.corusId),['far']);
});

test('every visible city survives dense populations without thinning',async()=>{
  const {context,map}=await page();const cities=Array.from({length:200},(_,i)=>city(String(i),39.1+(i%20)*.09,-74.9+Math.floor(i/20)*.18));
  context.CorusAppleMap.update(payload(cities));assert.equal(map.items.length,200);
});
test('small pans reuse annotation objects and zoom-in contracts the buffer',async()=>{
  const {context,map,callbacks,calls,frame}=await page();
  context.CorusAppleMap.update(payload([city('center'),city('edge',41.8,-74)]));
  const center=map.items.find(a=>a.corusId==='center'),added=calls.added;
  callbacks.get('region-change-start')();map.region={...map.region,center:{latitude:40.1,longitude:-74}};frame(1000);
  assert.equal(calls.added,added);assert.equal(map.items.find(a=>a.corusId==='center'),center);
  map.region={...map.region,span:{latitudeDelta:.2,longitudeDelta:.2}};callbacks.get('region-change-end')();
  assert.deepEqual(Array.from(map.items,a=>a.corusId),['center']);
});
test('detaching a retained surface stops viewport polling and another gesture restarts it',async()=>{
  const {context,map,callbacks,frame}=await page();
  context.CorusAppleMap.update(payload([city('start'),city('destination',48,-74)]));
  callbacks.get('region-change-start')();context.CorusAppleMap.suspend();
  map.region={...map.region,center:{latitude:48,longitude:-74}};frame(1000);
  assert.ok(!map.items.some(a=>a.corusId==='destination'));
  callbacks.get('region-change-start')();frame(1200);assert.ok(map.items.some(a=>a.corusId==='destination'));
});
test('a city rename updates its label without replacing faces or annotation identity',async()=>{
  const {context,map}=await page();const cities=[city('a')];context.CorusAppleMap.update(payload(cities,'a'));
  const annotation=map.items[0],faces=annotation.corusButton.querySelector('.faces');
  context.CorusAppleMap.update(payload([{...cities[0],name:'Renamed city'}],'a'));
  assert.equal(map.items[0],annotation);assert.equal(annotation.corusButton.querySelector('.faces'),faces);
  assert.equal(annotation.corusButton.querySelector('.label').textContent,'Renamed city');
});
test('shared marker updates preserve the one-cover listening presentation',async()=>{
  const {context,map}=await page();const cities=[city('a')];cities[0].faces.push({id:'second',name:'Second',avatar:'second',artwork:'cover-2'});cities[0].faces[0].artwork='cover-1';
  context.CorusAppleMap.update(payload(cities,'a'));const faces=map.items[0].corusButton.querySelector('.faces');
  context.CorusAppleMap.update({...payload(cities,'a'),playbackMode:'listen',playingUserId:'second'});
  assert.equal(map.items[0].corusButton.querySelector('.faces'),faces);
  assert.equal(map.items[0].corusButton.querySelector('.art-fan').children.length,1);
  assert.equal(map.items[0].corusButton.querySelector('.art-fan').children[0].src,'cover-2');
});

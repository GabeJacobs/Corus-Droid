// Run with Node's built-in test runner: node --test scripts/test_mapkit_startup.mjs
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../app/src/main/java/fm/corus/android/ui/screens/map/AppleCityMapView.kt', import.meta.url), 'utf8');
const html = source.slice(source.indexOf('<!doctype html>'));
// The production inline script has just two Kotlin boolean substitutions.
const inline = html.match(/<script>([\s\S]*?)<\/script>/)[1];

function page({ compact = false, rejectLoad = false, failMap = false } = {}) {
  const states = [], listeners = new Map();
  const element = () => ({ style: {}, classList: { toggle() {} }, clientWidth: 400, clientHeight: 600 });
  const root = element(), body = element(), container = element();
  const kit = {
    MapType: { Standard: 'standard' }, ColorScheme: { Light: 'light', Dark: 'dark' },
    Map: class {
      constructor(id, options) {
        assert.equal(id, 'map');
        if (failMap) throw new Error('renderer unavailable');
        this.region = options.region;
      }
      addEventListener() {}
    },
  };
  const context = vm.createContext({
    document: { documentElement: root, body, getElementById: () => container, addEventListener() {} },
    innerWidth: 400, innerHeight: 600, addEventListener() {},
    CorusAndroidMap: { loadState: state => states.push(state) },
    mapkit: {
      addEventListener: (name, handler) => listeners.set(name, handler),
      load: () => rejectLoad ? Promise.reject(new Error('network unavailable')) : Promise.resolve(kit),
    },
  });
  context.window = context;
  vm.runInContext(inline
    .replace('$gestures', String(!compact))
    .replace('${if (compact) "true" else "false"}', String(compact)), context);
  return { context, states, listeners };
}

test('a cached SDK executes only after its callback, bridge and container exist', () => {
  assert.ok(html.indexOf('src="https://cdn.apple-mapkit.com/') > html.indexOf('</script>'));
  const { context } = page();
  assert.equal(typeof context.initMapKitLoaderV2, 'function');
  assert.equal(typeof context.CorusAppleMap.update, 'function');
});

for (const compact of [false, true]) {
  test(`startup retains updates delivered before SDK readiness (compact=${compact})`, async () => {
    const { context, states } = page({ compact });
    context.CorusAppleMap.update({ cities: [], dark: true, focus: null });
    context.initMapKitLoaderV2();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(context.CorusAppleMap.ready, true);
    assert.equal(context.CorusAppleMap.data.dark, true);
    assert.deepEqual(states, ['ready']);
  });
}

for (const failure of ['rejectLoad', 'failMap']) {
  test(`${failure} reaches the native recovery UI`, async () => {
    const { context, states } = page({ [failure]: true });
    context.initMapKitLoaderV2();
    await new Promise(resolve => setImmediate(resolve));
    assert.deepEqual(states, ['initialization_error']);
    assert.equal(context.CorusAppleMap.ready, false);
  });
}

test('SDK configuration, library and script errors reach native recovery', async () => {
  const { context, states, listeners } = page();
  context.initMapKitLoaderV2();
  await new Promise(resolve => setImmediate(resolve));
  states.length = 0;
  listeners.get('error')();
  listeners.get('load-error')();
  vm.runInContext(html.match(/onerror="([^"]+)"/)[1], context);
  assert.deepEqual(states, ['configuration_error', 'library_error', 'script_error']);
});

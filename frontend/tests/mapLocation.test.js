import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import * as helpers from '../src/mapLocation.js'

function harness(demoMode = false) {
  let map
  const source = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8').split('<script setup>')[1].split('</script>')[0]
    .replace(/^import .*$/gm, '').replaceAll('import.meta.env', 'env')
  const context = {
    ...helpers, ref: value => ({ value }), reactive: value => value, computed: get => ({ get value() { return get() } }),
    nextTick: async () => {}, watch() {}, onMounted() {}, onUnmounted() {}, demoMode,
    authStore: { getToken: () => null }, api: { updateStationCoordinates: async () => {} }, env: { VITE_AMAP_KEY: 'test' },
    navigator: { get geolocation() { throw Error('Operator location must not be requested') } },
    window: { setTimeout: () => 1, clearTimeout() {}, AMap: {
      Map: class {
        constructor(container, options) { map = this; this.center = options.center; this.zoom = options.zoom; this.markers = [] }
        setCenter(center) { this.center = center }
        setZoom(zoom) { this.zoom = zoom }
        clearMap() { this.markers = [] }
        add(marker) { this.markers.push(marker) }
        destroy() {}
      },
      Geocoder: class { getLocation(address, done) { done('error', {}) } },
      Marker: class { constructor(options) { Object.assign(this, options) } on(event, callback) { this.click = callback } }
    } }
  }
  vm.runInNewContext(`${source}\nthis.app = { initStationMap, selectMapStation, mapStationKeyword, mapStationResults, selectedMapStationId, mapContainer, stations };`, context)
  context.app.mapContainer.value = { querySelector: () => ({}) }
  context.app.stations.value = [
    { id: 1, name: '新密中医院', address: '河南省新密市', longitude: 113.39, latitude: 34.54, status: 'OPERATING' },
    { id: 2, name: '成都示范站', address: '四川省成都市', longitude: 104.06, latitude: 30.67, status: 'CLOSED' }
  ]
  return { ...context.app, get map() { return map } }
}

for (const demoMode of [false, true]) {
  test(`${demoMode ? 'Pages' : 'Web'} centers on stations without requesting operator location`, async () => {
    const app = harness(demoMode)
    await app.initStationMap()
    assert.deepEqual(Array.from(app.map.center), [113.39, 34.54])
    assert.equal(app.map.zoom, 14)
    await app.selectMapStation(app.stations.value[1])
    assert.deepEqual(Array.from(app.map.center), [104.06, 30.67])
    app.map.setCenter([104.1, 30.7])
    await app.initStationMap()
    assert.deepEqual(Array.from(app.map.center), [104.1, 30.7])
  })
}

test('station search matches names and city/address and handles no matches', () => {
  const app = harness()
  app.mapStationKeyword.value = '新密'
  assert.equal(app.mapStationResults.value.length, 1)
  app.mapStationKeyword.value = '四川'
  assert.equal(app.mapStationResults.value[0].id, 2)
  app.mapStationKeyword.value = '不存在'
  assert.equal(app.mapStationResults.value.length, 0)
})

test('custom marker escapes station names and clicking selects the station', async () => {
  const app = harness()
  app.stations.value[0].name = '<img src=x>'
  await app.initStationMap()
  assert.match(app.map.markers[0].content, /&lt;img src=x&gt;/)
  assert.equal(app.map.markers[0].label, undefined)
  await app.map.markers[1].click()
  assert.equal(app.selectedMapStationId.value, 2)
  assert.match(app.map.markers[1].content, /is-selected/)
})

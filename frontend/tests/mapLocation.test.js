import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import * as locationHelpers from '../src/mapLocation.js'

const xinmi = [113.39, 34.54]
const chengdu = [104.06, 30.67]
const gps = (point = xinmi, accuracy = 25, timestamp = Date.now()) => ({ coords: { longitude: point[0], latitude: point[1], accuracy }, timestamp })
const converter = { convertFrom: (point, type, done) => done('complete', { info: 'ok', locations: [[point[0] + 0.006, point[1] - 0.002]] }) }
const request = (position, overrides = {}) => locationHelpers.requestBrowserLocation({ geolocation: { getCurrentPosition: done => done(position) }, AMap: converter, ...overrides })

test('fresh device coordinates are converted before use', async () => {
  const result = await request(gps())
  assert.deepEqual(result.location, [113.396, 34.538])
  assert.equal(result.accuracy, 25)
})

test('rejects coarse, missing, zero, negative and stale accuracy/positions', async () => {
  for (const accuracy of [500, null, undefined, 0, -1, NaN]) {
    const position = gps()
    position.coords.accuracy = accuracy
    assert.equal((await request(position)).location, undefined)
  }
  assert.match((await request(gps(xinmi, 25, Date.now() - 120000))).error, /过期/)
})

test('conversion failure or a missing callback never exposes unconverted GPS', async () => {
  assert.equal((await request(gps(), { AMap: null })).location, undefined)
  assert.equal((await request(gps(), { AMap: { convertFrom: (point, type, done) => done('error', {}) } })).location, undefined)
  assert.match((await request(gps(), { AMap: { convertFrom() {} }, conversionTimeoutMs: 10 })).error, /转换失败/)
})

test('permission failure and HTTP have actionable messages', async () => {
  const result = await request(gps(), { geolocation: { getCurrentPosition: (done, fail) => fail({ code: 1 }) } })
  assert.match(result.error, /权限/)
  assert.match((await request(gps(), { secure: false })).error, /HTTPS/)
})

// Run the actual App.vue setup functions with map/device doubles. This checks
// viewport movement and stale async results, rather than only numeric helpers.
function appHarness(demoMode = false) {
  let nextPosition = gps()
  let failPosition = false
  let geolocationRequests = 0
  let map
  const ref = value => ({ value })
  const context = {
    ...locationHelpers, ref, reactive: value => value, computed: get => ({ get value() { return get() } }),
    nextTick: async () => {}, watch() {}, onMounted() {}, onUnmounted() {},
    demoMode, authStore: { getToken: () => null }, api: { updateStationCoordinates: async () => {} },
    env: { VITE_AMAP_KEY: 'test' },
    navigator: { geolocation: { getCurrentPosition(done, fail) { geolocationRequests++; failPosition ? fail({ code: 2 }) : done(nextPosition) } } },
    window: { isSecureContext: true, setTimeout: () => 1, clearTimeout() {}, AMap: {
      ...converter,
      // Any IP fallback would immediately break this test.
      Geolocation: class { constructor() { throw new Error('IP fallback used') } },
      Map: class {
        constructor(container, options) { map = this; this.center = options.center; this.zoom = options.zoom; this.moves = 0; this.markers = [] }
        setCenter(center) { this.center = center; this.moves++ }
        setZoom(zoom) { this.zoom = zoom }
        clearMap() { this.markers = [] }
        add(marker) { this.markers.push(marker) }
        destroy() {}
      },
      Geocoder: class { getLocation(address, done) { done('complete', { geocodes: [{ formattedAddress: '河南省郑州市新密市青屏大街', location: xinmi }] }) } },
      Marker: class { constructor(options) { Object.assign(this, options) } on() {} }
    } },
  }
  const source = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8').split('<script setup>')[1].split('</script>')[0]
    .replace(/^import .*$/gm, '').replaceAll('import.meta.env', 'env')
  vm.runInNewContext(`${source}\nthis.app = { initStationMap, relocateMap, selectMapAddress, searchMapAddress, mapAddress, mapAddressResults, usePendingMapLocation, pendingMapLocation, mapPosition, mapLocationLabel, mapContainer, stations };`, context)
  context.app.mapContainer.value = { querySelector: () => ({}) }
  context.app.stations.value = []
  return {
    ...context.app, get map() { return map }, get requests() { return geolocationRequests },
    position: value => { nextPosition = value; failPosition = false }, fail: () => { failPosition = true }
  }
}

for (const demoMode of [false, true]) {
  test(`${demoMode ? 'Pages' : 'ordinary Web'}: failed retry and refresh preserve the previous street view`, async () => {
    const app = appHarness(demoMode)
    await app.initStationMap()
    const center = app.map.center
    assert.equal(app.map.zoom, 17)
    app.fail()
    await app.relocateMap()
    assert.deepEqual(app.map.center, center)
    assert.match(app.mapLocationLabel.value, /保留上次位置/)
    app.map.setCenter(xinmi) // User pans the map.
    const requests = app.requests
    await app.initStationMap()
    assert.deepEqual(app.map.center, xinmi)
    assert.equal(app.requests, requests)
  })

  test(`${demoMode ? 'Pages' : 'ordinary Web'}: retry cannot jump from Xinmi to Chengdu without a user choice`, async () => {
    const app = appHarness(demoMode)
    await app.initStationMap()
    const center = app.map.center
    app.position(gps(chengdu))
    await app.relocateMap()
    assert.deepEqual(app.map.center, center)
    assert.ok(app.pendingMapLocation.value.distance > 800)
    await app.usePendingMapLocation()
    assert.ok(app.map.center[0] < 105)
  })
}

test('manual address selection works without device GPS and survives retry', async () => {
  const app = appHarness()
  app.fail()
  await app.initStationMap()
  assert.equal(app.map.markers.length, 0)
  assert.match(app.mapLocationLabel.value, /尚未获取当前位置/)
  app.mapAddress.value = '河南省新密市青屏大街'
  await app.searchMapAddress()
  await app.selectMapAddress(app.mapAddressResults.value[0])
  assert.deepEqual(app.map.center, xinmi)
  assert.equal(app.map.zoom, 17)
  assert.match(app.map.markers[0].title, /手动选择/)
  await app.relocateMap()
  assert.deepEqual(app.map.center, xinmi)
})

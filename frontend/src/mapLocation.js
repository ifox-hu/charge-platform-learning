export function isChinaCoordinate(longitude, latitude) {
  const valid = value => value != null && value !== '' && Number.isFinite(Number(value))
  return valid(longitude) && valid(latitude)
    && Number(longitude) >= 73 && Number(longitude) <= 135
    && Number(latitude) >= 3 && Number(latitude) <= 54
}

export function toLocationArray(point) {
  if (!point) return null
  const location = typeof point.toArray === 'function' ? point.toArray() : point
  return isChinaCoordinate(location?.[0], location?.[1]) ? [Number(location[0]), Number(location[1])] : null
}

export function distanceKm(left, right) {
  const radians = value => Number(value) * Math.PI / 180
  const a = Math.sin(radians(right[1] - left[1]) / 2) ** 2
    + Math.cos(radians(left[1])) * Math.cos(radians(right[1]))
    * Math.sin(radians(right[0] - left[0]) / 2) ** 2
  return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, a)))
}

// Bound third-party callbacks too: SDK conversion/geocoding may never return.
export function callbackResult(start, timeoutMs = 5000) {
  return new Promise(resolve => {
    const timer = setTimeout(() => resolve(null), timeoutMs)
    const finish = value => { clearTimeout(timer); resolve(value) }
    try { start(finish) } catch { finish(null) }
  })
}

export async function requestBrowserLocation({ geolocation, AMap, secure = true, now = Date.now(), timeoutMs = 15000, conversionTimeoutMs = 5000 }) {
  if (!secure) return { error: '当前网址不支持安全定位，请使用 HTTPS 或 localhost' }
  if (!geolocation) return { error: '浏览器不支持定位' }
  const result = await callbackResult(finish => geolocation.getCurrentPosition(
    position => finish({ position }),
    error => finish({ error }),
    { enableHighAccuracy: true, timeout: timeoutMs, maximumAge: 0 }
  ), timeoutMs + 1000)
  if (!result?.position) {
    const error = result?.error?.code === 1 ? '定位权限未开启'
      : result?.error?.code === 2 ? '设备暂时无法获取位置' : '定位超时'
    return { error }
  }
  const { coords, timestamp } = result.position
  const gps = toLocationArray([coords?.longitude, coords?.latitude])
  const accuracy = coords?.accuracy
  // accuracy is an estimate, not proof of GPS. Never use IP fallback.
  if (!gps || typeof accuracy !== 'number' || !Number.isFinite(accuracy) || accuracy <= 0 || accuracy > 100) {
    return { error: '定位精度不足（需在 100 米以内）' }
  }
  if (!Number.isFinite(timestamp) || timestamp < now - 60000 || timestamp > Date.now() + 10000) {
    return { error: '设备返回了过期位置，请重新定位' }
  }
  if (!AMap?.convertFrom) return { error: '地图坐标转换暂不可用' }
  const location = await callbackResult(finish => AMap.convertFrom(gps, 'gps', (status, converted) => {
    finish(status === 'complete' && converted?.info === 'ok' ? toLocationArray(converted.locations?.[0]) : null)
  }), conversionTimeoutMs)
  // WGS-84 must never be passed to a GCJ-02 map after conversion fails.
  return location ? { location, accuracy } : { error: '地图坐标转换失败，请重试' }
}

export function assessLocation(candidate, previous) {
  if (!candidate?.location) return { ...candidate, accepted: false }
  const distance = previous ? distanceKm(previous.location, candidate.location) : 0
  return distance > 5
    ? { ...candidate, accepted: false, distance, error: `新位置与上次相差 ${Math.round(distance)} 公里，已保留原位置` }
    : { ...candidate, accepted: true }
}

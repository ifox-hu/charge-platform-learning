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

// Bound third-party callbacks too: SDK conversion/geocoding may never return.
export function callbackResult(start, timeoutMs = 5000) {
  return new Promise(resolve => {
    const timer = setTimeout(() => resolve(null), timeoutMs)
    const finish = value => { clearTimeout(timer); resolve(value) }
    try { start(finish) } catch { finish(null) }
  })
}

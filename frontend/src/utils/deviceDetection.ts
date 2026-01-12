export interface DeviceInfo {
  deviceName: string
  browser: string
  osName: string
}

export function getDeviceInfo(): DeviceInfo {
  const userAgent = navigator.userAgent

  // Detect OS
  let osName = 'Unknown OS'
  if (userAgent.indexOf('Win') > -1) osName = 'Windows'
  if (userAgent.indexOf('Mac') > -1) osName = 'macOS'
  if (userAgent.indexOf('X11') > -1) osName = 'UNIX'
  if (userAgent.indexOf('Linux') > -1) osName = 'Linux'
  if (userAgent.indexOf('iPhone') > -1) osName = 'iOS'
  if (userAgent.indexOf('Android') > -1) osName = 'Android'

  // Detect Browser
  let browser = 'Unknown Browser'
  if (userAgent.indexOf('Edge') > -1 || userAgent.indexOf('Edg') > -1) browser = 'Edge'
  else if (userAgent.indexOf('Chrome') > -1 && userAgent.indexOf('Chromium') === -1) browser = 'Chrome'
  else if (userAgent.indexOf('Safari') > -1 && userAgent.indexOf('Chrome') === -1) browser = 'Safari'
  else if (userAgent.indexOf('Firefox') > -1) browser = 'Firefox'
  else if (userAgent.indexOf('Trident') > -1) browser = 'Internet Explorer'

  // Detect Device Type
  const isMobile = /Mobile|Android|iPhone/.test(userAgent)
  const isTablet = /iPad|Android/.test(userAgent) && !/Mobile/.test(userAgent)
  
  let deviceName = 'Computer'
  if (isMobile) {
    deviceName = osName === 'iOS' ? 'iPhone' : 'Android Phone'
  } else if (isTablet) {
    deviceName = osName === 'iOS' ? 'iPad' : 'Android Tablet'
  } else if (osName === 'Windows') {
    deviceName = 'Windows PC'
  } else if (osName === 'macOS') {
    deviceName = 'Mac'
  }

  return {
    deviceName: `${deviceName} - ${browser}`,
    browser,
    osName
  }
}

// format number -> shorthand for values >= 100k (k/M/B suffix)
export function formatNumberCompact(num: number, decimals: number = 1): string {
  if (num < 100000) {
    // Display normally with commas for numbers under 100k
    return num.toLocaleString('en-US', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2
    })
  }

  const absNum = Math.abs(num)
  const sign = num < 0 ? '-' : ''

  if (absNum >= 1000000000) {
    return sign + (absNum / 1000000000).toFixed(decimals) + 'B'
  } else if (absNum >= 1000000) {
    return sign + (absNum / 1000000).toFixed(decimals) + 'M'
  } else if (absNum >= 1000) {
    return sign + (absNum / 1000).toFixed(decimals) + 'k'
  }

  return num.toLocaleString('en-US', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
  })
}

// format currency -> shorthand for values >= 100k
export function formatCurrencyCompact(num: number, currency: string = '$', decimals: number = 1): string {
  if (num < 100000) {
    // Display normally with commas for numbers under 100k
    return currency + num.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })
  }

  const absNum = Math.abs(num)
  const sign = num < 0 ? '-' : ''

  if (absNum >= 1000000000) {
    return sign + currency + (absNum / 1000000000).toFixed(decimals) + 'B'
  } else if (absNum >= 1000000) {
    return sign + currency + (absNum / 1000000).toFixed(decimals) + 'M'
  } else if (absNum >= 1000) {
    return sign + currency + (absNum / 1000).toFixed(decimals) + 'k'
  }

  return currency + num.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

// format relative time (e.g., "5m ago", "Just now")
export function formatRelativeTime(timestamp: string): string {
  const now = new Date()
  const then = new Date(timestamp)
  const diffMs = now.getTime() - then.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return 'Just now'
  if (diffMins < 60) return `${diffMins}m ago`
  if (diffHours < 24) return `${diffHours}h ago`
  if (diffDays < 7) return `${diffDays}d ago`

  return 'Long ago'
}

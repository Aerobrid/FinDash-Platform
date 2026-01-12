/**
 * Format a number with shorthand notation for large values
 * Numbers < 100,000 display normally with commas
 * Numbers >= 100,000 display as shorthand (k, M, B)
 * @param num - The number to format
 * @param decimals - Number of decimal places (default: 1)
 * @returns Formatted string
 */
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

/**
 * Format a currency value with shorthand notation for large values
 * @param num - The number to format
 * @param currency - Currency symbol (default: '$')
 * @param decimals - Number of decimal places (default: 1)
 * @returns Formatted string with currency symbol
 */
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

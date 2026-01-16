// convert UTC timestamp -> Central Time display
// ensures timestamp treated as UTC by adding Z suffix if missing
function ensureUTC(timestamp: string): string {
  return timestamp.endsWith('Z') ? timestamp : timestamp + 'Z'
}

// full date + time in Central Time (e.g., "Jan 15, 2026, 02:30:45 PM")
export function formatCentralTime(timestamp: string): string {
  const date = new Date(ensureUTC(timestamp))
  return date.toLocaleString('en-US', {
    timeZone: 'America/Chicago',
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// date only in Central Time (e.g., "Jan 15, 2026")
export function formatCentralDate(timestamp: string): string {
  const date = new Date(ensureUTC(timestamp))
  return date.toLocaleDateString('en-US', {
    timeZone: 'America/Chicago',
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  })
}

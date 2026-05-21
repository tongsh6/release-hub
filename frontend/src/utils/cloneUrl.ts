const SCP_LIKE = /^git@([^:]+):(.+)$/
const SUPPORTED_SCHEMES = new Set(['http:', 'https:', 'ssh:', 'mock:'])

export function isSupportedCloneUrl(value: string): boolean {
  const trimmed = value.trim()
  if (!trimmed || trimmed.length > 512) return false

  const scpMatch = trimmed.match(SCP_LIKE)
  if (scpMatch) {
    return hasRepoPath(scpMatch[2])
  }

  try {
    const url = new URL(trimmed)
    if (!SUPPORTED_SCHEMES.has(url.protocol)) return false
    if (url.protocol !== 'mock:' && !url.hostname) return false
    return hasRepoPath(url.pathname)
  } catch {
    return false
  }
}

function hasRepoPath(path: string): boolean {
  const normalizedPath = path.replace(/^\/+/, '').replace(/\/+$/, '').replace(/\.git$/, '')
  return normalizedPath.length > 0 && !normalizedPath.endsWith('/')
}

import type { GroupNode } from '@/api/modules/group'

export function resolveGroupPath(code: string | undefined, nodes: GroupNode[], ancestors: string[] = []): string {
  if (!code) return ''
  for (const node of nodes) {
    const currentPath = [...ancestors, node.name || node.code || '']
    if (node.code === code) {
      return currentPath.filter(Boolean).join(' / ')
    }
    const childPath = resolveGroupPath(code, node.children || [], currentPath)
    if (childPath) {
      return childPath
    }
  }
  return ''
}

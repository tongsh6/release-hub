import { describe, it, expect } from 'vitest'
import type { GroupNode } from '@/api/modules/group'

// Extract pure functions for direct testing
function findByCode(nodes: GroupNode[], code?: string | null): GroupNode | null {
  if (!code) return null
  for (const n of nodes) {
    if (n.code === code) return n
    const child = findByCode(n.children || [], code)
    if (child) return child
  }
  return null
}

const makeNode = (code: string, children: GroupNode[] = []): GroupNode =>
  ({ code, name: code, children } as GroupNode)

describe('findByCode', () => {
  it('should return null for null code', () => {
    expect(findByCode([], null)).toBeNull()
  })

  it('should find a top-level node', () => {
    const a = makeNode('a')
    expect(findByCode([a], 'a')).toBe(a)
  })

  it('should find a nested child', () => {
    const c = makeNode('c')
    const b = makeNode('b', [c])
    const a = makeNode('a', [b])
    expect(findByCode([a], 'c')).toBe(c)
  })

  it('should return null when not found', () => {
    expect(findByCode([makeNode('a')], 'z')).toBeNull()
  })

  it('should find in sibling subtrees', () => {
    const c = makeNode('c')
    const a = makeNode('a')
    const b = makeNode('b', [c])
    expect(findByCode([a, b], 'c')).toBe(c)
  })
})

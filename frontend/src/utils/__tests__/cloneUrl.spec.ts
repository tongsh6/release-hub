import { describe, expect, it } from 'vitest'
import { isSupportedCloneUrl } from '../cloneUrl'

describe('cloneUrl', () => {
  it('accepts supported Git clone URL forms', () => {
    expect(isSupportedCloneUrl('git@gitlab.example.com:Customer/Payment.git')).toBe(true)
    expect(isSupportedCloneUrl('https://gitlab.example.com/customer/payment')).toBe(true)
    expect(isSupportedCloneUrl('ssh://git@gitlab.example.com/customer/payment.git')).toBe(true)
    expect(isSupportedCloneUrl('mock:///customer/payment.git')).toBe(true)
  })

  it('rejects ambiguous or unsupported clone URLs', () => {
    expect(isSupportedCloneUrl('not-a-git-url')).toBe(false)
    expect(isSupportedCloneUrl('ftp://gitlab.example.com/customer/payment.git')).toBe(false)
    expect(isSupportedCloneUrl('https://gitlab.example.com/')).toBe(false)
    expect(isSupportedCloneUrl('')).toBe(false)
  })
})

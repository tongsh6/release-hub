import type { Directive, DirectiveBinding } from 'vue'
import { hasPerm } from '@/utils/perm'

function apply(el: HTMLElement, binding: DirectiveBinding<string>) {
  const required = binding.value
  const ok = hasPerm(required)
  const disable = !!binding.modifiers.disable
  if (!ok) {
    if (disable) {
      el.setAttribute('disabled', 'true')
      el.style.pointerEvents = 'none'
      el.style.opacity = '0.6'
    } else {
      el.style.display = 'none'
    }
  } else {
    if (disable) {
      el.removeAttribute('disabled')
      el.style.pointerEvents = ''
      el.style.opacity = ''
    }
    if (el.style.display === 'none') {
      el.style.display = ''
    }
  }
}

const permDirective: Directive = {
  mounted: apply,
  updated: apply
}

export default permDirective

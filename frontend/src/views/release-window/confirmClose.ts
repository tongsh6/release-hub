import { h, ref } from 'vue'
import { ElCheckbox, ElMessageBox } from 'element-plus'

export async function confirmWindowClose(t: (key: string) => string): Promise<boolean> {
  const triggerCi = ref(false)
  await ElMessageBox.confirm(
    () => h('div', [
      h('p', t('releaseWindow.confirmClose')),
      h(ElCheckbox, {
        modelValue: triggerCi.value,
        'onUpdate:modelValue': (value: boolean | string | number) => { triggerCi.value = value === true }
      }, () => t('releaseWindow.triggerCleanupCi'))
    ]),
    t('common.warning'),
    { type: 'warning' }
  )
  return triggerCi.value
}

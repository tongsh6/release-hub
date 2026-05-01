import { ref, toRaw } from 'vue'
import type { CrudMode, Id } from '@/types/crud'

interface UseDetailFormOptions<T> {
  fetchById: (id: Id) => Promise<T>
  create: (payload: T) => Promise<any>
  update: (id: Id, payload: T) => Promise<any>
  defaultForm: T
}

export function useDetailForm<T extends object>(options: UseDetailFormOptions<T>) {
  const { fetchById, create, update, defaultForm } = options

  const form = ref<T>({ ...defaultForm }) as any // Ref to generic object
  const mode = ref<CrudMode>('create')
  const loading = ref(false)
  const saving = ref(false)
  const currentId = ref<Id | null>(null)

  const reset = () => {
    form.value = { ...defaultForm }
    currentId.value = null
    mode.value = 'create'
  }

  const load = async (id: Id, newMode: CrudMode = 'view') => {
    currentId.value = id
    mode.value = newMode
    loading.value = true
    try {
      const res = await fetchById(id)
      form.value = res
    } catch (error) {
      console.error('Failed to load detail:', error)
    } finally {
      loading.value = false
    }
  }

  const submit = async () => {
    if (mode.value === 'view') return

    saving.value = true
    try {
      if (mode.value === 'create') {
        await create(toRaw(form.value))
      } else {
        if (!currentId.value) throw new Error('Missing ID for update')
        await update(currentId.value, toRaw(form.value))
      }
      return true
    } catch (error) {
      console.error('Failed to submit form:', error)
      throw error
    } finally {
      saving.value = false
    }
  }

  return {
    form,
    mode,
    loading,
    saving,
    load,
    submit,
    reset
  }
}

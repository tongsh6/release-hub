import { ref, toRaw } from 'vue'
import type { CrudMode, Id } from '@/types/crud'

interface UseDialogFormOptions<T> {
  fetchById: (id: Id) => Promise<T>
  create: (payload: T) => Promise<any>
  update: (id: Id, payload: T) => Promise<any>
  defaultForm: T
}

interface OpenPayload<T> {
  id?: Id
  mode?: CrudMode
  preset?: Partial<T>
}

export function useDialogForm<T extends object>(options: UseDialogFormOptions<T>) {
  const { fetchById, create, update, defaultForm } = options

  const visible = ref(false)
  const mode = ref<CrudMode>('create')
  const loading = ref(false)
  const saving = ref(false)
  const form = ref<T>({ ...defaultForm }) as any
  const currentId = ref<Id | null>(null)

  // Callback for success
  let onSuccessCallback: ((payload?: any) => void) | null = null
  const onSuccess = (fn: (payload?: any) => void) => {
    onSuccessCallback = fn
  }

  const open = async (payload: OpenPayload<T> = {}) => {
    const { id, mode: newMode = 'create', preset } = payload
    
    // Reset state
    visible.value = true
    mode.value = newMode
    currentId.value = id || null
    loading.value = false
    saving.value = false
    
    // Initialize form
    if (newMode === 'create') {
      form.value = { ...defaultForm, ...(preset || {}) }
    } else if (id) {
      loading.value = true
      try {
        const res = await fetchById(id)
        form.value = { ...res, ...(preset || {}) }
      } catch (error) {
        console.error('Failed to load dialog data:', error)
        // Optionally close dialog on error or show message
      } finally {
        loading.value = false
      }
    } else {
      console.warn('Open dialog in edit/view mode without ID')
    }
  }

  const close = () => {
    visible.value = false
  }

  const submit = async () => {
    if (mode.value === 'view') {
      close()
      return
    }

    saving.value = true
    try {
      let res
      if (mode.value === 'create') {
        res = await create(toRaw(form.value))
      } else {
        if (!currentId.value) throw new Error('Missing ID for update')
        res = await update(currentId.value, toRaw(form.value))
      }
      
      if (onSuccessCallback) {
        onSuccessCallback(res)
      }
      close()
    } catch (error) {
      console.error('Failed to submit dialog:', error)
      throw error
    } finally {
      saving.value = false
    }
  }

  return {
    visible,
    mode,
    loading,
    saving,
    form,
    open,
    close,
    submit,
    onSuccess
  }
}

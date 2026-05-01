import { reactive, ref, toRaw, onMounted } from 'vue'
import type { PageQuery, PageResult } from '@/types/crud'
import { handleError } from '@/utils/error'

interface UseListPageOptions<T, Q extends PageQuery> {
  fetcher: (query: Q) => Promise<PageResult<T>>
  defaultQuery: Omit<Q, 'page' | 'pageSize'>
  beforeFetch?: (query: Q) => Q | void
  afterFetch?: (list: T[]) => void
  immediate?: boolean
}

export function useListPage<T, Q extends PageQuery>(options: UseListPageOptions<T, Q>) {
  const { fetcher, defaultQuery, beforeFetch, afterFetch, immediate = true } = options

  const loading = ref(false)
  const total = ref(0)
  const list = ref<T[]>([]) as any // Type assertion to avoid complexity with ref array types

  // Initialize query with default values and pagination
  const query = reactive({
    ...defaultQuery,
    page: 1,
    pageSize: 10
  }) as Q

  const fetch = async () => {
    loading.value = true
    try {
      // Allow modification of query before fetch
      const finalQuery = beforeFetch ? (beforeFetch(toRaw(query)) || toRaw(query)) : toRaw(query)
      
      const res = await fetcher(finalQuery)
      list.value = res.list
      total.value = res.total
      
      if (afterFetch) {
        afterFetch(list.value)
      }
    } catch (error) {
      handleError(error)
    } finally {
      loading.value = false
    }
  }

  const search = () => {
    query.page = 1
    fetch()
  }

  const reset = () => {
    // Reset query fields to default, keeping pagination reset
    Object.keys(defaultQuery).forEach(key => {
      // @ts-expect-error allow dynamic index assignment
      query[key] = defaultQuery[key]
    })
    query.page = 1
    query.pageSize = 10
    fetch()
  }

  const onPageChange = (page: number) => {
    query.page = page
    fetch()
  }

  const onPageSizeChange = (size: number) => {
    query.pageSize = size
    query.page = 1
    fetch()
  }

  if (immediate) {
    onMounted(() => {
      fetch()
    })
  }

  return {
    query,
    loading,
    list,
    total,
    fetch,
    search,
    reset,
    onPageChange,
    onPageSizeChange
  }
}

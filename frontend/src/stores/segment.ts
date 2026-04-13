import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getSegments, getSegment } from '@/api/segments'
import type { Segment, SegmentQuery } from '@/types/segment'
import type { PageResult } from '@/types'

export const useSegmentStore = defineStore('segment', () => {
  const segments = ref<Segment[]>([])
  const total = ref(0)
  const loading = ref(false)
  const currentSegment = ref<Segment | null>(null)

  async function fetchSegments(query: SegmentQuery) {
    loading.value = true
    try {
      const result: PageResult<Segment> = await getSegments(query)
      segments.value = result.content
      total.value = result.totalElements
      return result
    } finally {
      loading.value = false
    }
  }

  async function fetchSegment(id: number) {
    loading.value = true
    try {
      const result = await getSegment(id)
      currentSegment.value = result
      return result
    } finally {
      loading.value = false
    }
  }

  return {
    segments,
    total,
    loading,
    currentSegment,
    fetchSegments,
    fetchSegment,
  }
})

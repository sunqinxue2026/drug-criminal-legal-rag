import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { favoriteApi } from '@/api/favorite'
import type { Category, Item } from '@/types'

type FavoriteRecord = {
  itemId?: number
  id?: number
}

export const useItemStore = defineStore('item', () => {
  const items = ref<Item[]>([])
  const categories = ref<Category[]>([])
  const currentItem = ref<Item | null>(null)
  const loading = ref(false)
  const favoriteIds = ref<number[]>([])
  const favoritesLoadedUserId = ref<number | null>(null)

  const favoriteCount = computed(() => favoriteIds.value.length)

  function setItems(itemList: Item[]) {
    items.value = itemList
  }

  function setCategories(categoryList: Category[]) {
    categories.value = categoryList
  }

  function setCurrentItem(item: Item | null) {
    currentItem.value = item
  }

  function setLoading(value: boolean) {
    loading.value = value
  }

  function addItem(item: Item) {
    items.value.unshift(item)
  }

  function updateItem(item: Item) {
    const index = items.value.findIndex((entry) => entry.id === item.id)
    if (index !== -1) {
      items.value[index] = item
    }
  }

  function removeItem(itemId: number) {
    items.value = items.value.filter((entry) => entry.id !== itemId)
  }

  function isFavorite(itemId?: number) {
    return favoriteIds.value.includes(Number(itemId))
  }

  function applyFavoriteCount(itemId: number, isAdding: boolean) {
    const updateCount = (target?: Item | null) => {
      if (!target || target.id !== itemId) return
      const current = Number(target.favoriteCount || 0)
      target.favoriteCount = isAdding ? current + 1 : Math.max(0, current - 1)
    }

    items.value.forEach(updateCount)
    updateCount(currentItem.value)
  }

  async function loadFavorites(userId?: number, force = false) {
    if (!userId) {
      favoriteIds.value = []
      favoritesLoadedUserId.value = null
      return
    }

    if (!force && favoritesLoadedUserId.value === userId && favoriteIds.value.length >= 0) {
      return
    }

    const res = await favoriteApi.getList(userId)
    favoriteIds.value = (Array.isArray(res.data) ? res.data : []).map((item: FavoriteRecord) => Number(item.itemId || item.id))
    favoritesLoadedUserId.value = userId
  }

  async function syncFavoriteStatus(userId?: number, itemId?: number) {
    if (!userId || !itemId) return false
    const res = await favoriteApi.check(userId, itemId)
    const next = !!(res.data?.isFavorite ?? res.data?.favorite)
    if (next && !favoriteIds.value.includes(itemId)) {
      favoriteIds.value = [...favoriteIds.value, itemId]
    }
    if (!next && favoriteIds.value.includes(itemId)) {
      favoriteIds.value = favoriteIds.value.filter((id) => id !== itemId)
    }
    return next
  }

  async function toggleFavorite(userId?: number, item?: Item | null) {
    if (!userId || !item?.id) {
      throw new Error('missing favorite context')
    }

    if (isFavorite(item.id)) {
      await favoriteApi.remove(userId, item.id)
      favoriteIds.value = favoriteIds.value.filter((id) => id !== item.id)
      applyFavoriteCount(item.id, false)
      return false
    }

    await favoriteApi.add(userId, item.id)
    favoriteIds.value = [...favoriteIds.value, item.id]
    applyFavoriteCount(item.id, true)
    return true
  }

  return {
    items,
    categories,
    currentItem,
    loading,
    favoriteIds,
    favoriteCount,
    setItems,
    setCategories,
    setCurrentItem,
    setLoading,
    addItem,
    updateItem,
    removeItem,
    loadFavorites,
    isFavorite,
    toggleFavorite,
    syncFavoriteStatus
  }
})

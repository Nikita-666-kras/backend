import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_PREFIX = 'post-editor-draft-v1:'

export interface PostEditorDraft {
  title: string
  shortDescription: string
  content: string
  tags: string
  categories: string
  coverMediaId: string | null
  mediaObjectNames: string[]
  savedAt: string
}

function keyById(postId: string) {
  return `${STORAGE_PREFIX}${postId || 'new'}`
}

export const usePostEditorStore = defineStore('postEditor', () => {
  const draftExists = ref(false)

  function saveDraft(postId: string, draft: PostEditorDraft) {
    sessionStorage.setItem(keyById(postId), JSON.stringify(draft))
    draftExists.value = true
  }

  function loadDraft(postId: string): PostEditorDraft | null {
    const raw = sessionStorage.getItem(keyById(postId))
    if (!raw) {
      draftExists.value = false
      return null
    }
    draftExists.value = true
    try {
      return JSON.parse(raw) as PostEditorDraft
    } catch {
      return null
    }
  }

  function hasDraft(postId: string) {
    return Boolean(sessionStorage.getItem(keyById(postId)))
  }

  function clearDraft(postId: string) {
    sessionStorage.removeItem(keyById(postId))
    draftExists.value = false
  }

  return {
    draftExists,
    saveDraft,
    loadDraft,
    clearDraft,
    hasDraft
  }
})

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi } from '../api/user'
import type {
  UpdateProfilePayload,
  UpdateUserSettingPayload,
  UserProfile,
  UserSetting,
} from '../types/user'

export const useUserStore = defineStore('user', () => {
  const profile = ref<UserProfile | null>(null)
  const settings = ref<UserSetting[]>([])

  async function fetchProfile(): Promise<UserProfile> {
    profile.value = await userApi.profile()
    return profile.value
  }

  async function updateProfile(payload: UpdateProfilePayload): Promise<UserProfile> {
    profile.value = await userApi.updateProfile(payload)
    return profile.value
  }

  async function fetchSettings(): Promise<void> {
    settings.value = await userApi.listSettings()
  }

  async function updateSetting(payload: UpdateUserSettingPayload): Promise<void> {
    const updated = await userApi.updateSetting(payload)
    const index = settings.value.findIndex((item) => item.key === updated.key)
    if (index >= 0) settings.value[index] = updated
    else settings.value.push(updated)
  }

  function reset(): void {
    profile.value = null
    settings.value = []
  }

  return {
    profile, settings,
    fetchProfile, updateProfile, fetchSettings, updateSetting, reset,
  }
})

import { patch, request } from './request'
import type {
  UpdateProfilePayload,
  UpdateUserSettingPayload,
  UserProfile,
  UserSetting,
} from '../types/user'

function segment(value: string): string {
  return encodeURIComponent(value)
}

export const userApi = {
  profile: () => request<UserProfile>('/api/users/me'),
  updateProfile: (payload: UpdateProfilePayload) => patch<UserProfile>('/api/users/me', payload),

  listSettings: () => request<UserSetting[]>('/api/users/me/settings'),
  getSetting: (key: string) => request<UserSetting>(`/api/users/me/settings/${segment(key)}`),
  updateSetting: (payload: UpdateUserSettingPayload) => patch<UserSetting>('/api/users/me/settings', payload),
}

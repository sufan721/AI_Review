import { del, patch, post, put, request } from './request'
import type {
  AdminUser,
  IdentityProvider,
  IdentityProviderPayload,
  InstanceSetting,
  InstanceSettingPayload,
} from '../types/admin'

function segment(value: string): string {
  return encodeURIComponent(value)
}

export const identityProviderApi = {
  list: () => request<IdentityProvider[]>('/api/identity-providers'),
  detail: (uid: string) => request<IdentityProvider>(`/api/identity-providers/${segment(uid)}`),
  create: (payload: IdentityProviderPayload) => post<IdentityProvider>('/api/identity-providers', payload),
  update: (uid: string, payload: IdentityProviderPayload) =>
    patch<IdentityProvider>(`/api/identity-providers/${segment(uid)}`, payload),
  remove: (uid: string) => del<null>(`/api/identity-providers/${segment(uid)}`),
}

export const instanceSettingApi = {
  list: () => request<InstanceSetting[]>('/api/instance/settings'),
  get: (name: string) => request<InstanceSetting>(`/api/instance/settings/${segment(name)}`),
  upsert: (name: string, payload: InstanceSettingPayload) =>
    put<InstanceSetting>(`/api/instance/settings/${segment(name)}`, payload),
}

export const adminApi = {
  users: () => request<AdminUser[]>('/api/admin/users'),
}

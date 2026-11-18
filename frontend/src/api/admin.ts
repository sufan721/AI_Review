import { del, patch, post, put, request } from './request'
import type {
  AdminUser,
  BootstrapStatus,
  GeneralSetting,
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
  general: () => request<GeneralSetting>('/api/instance/settings/general'),
  updateGeneral: (payload: GeneralSetting) =>
    put<GeneralSetting>('/api/instance/settings/general', payload),
}

/** 实例级匿名状态（首次注册引导）。 */
export const instanceApi = {
  bootstrap: () => request<BootstrapStatus>('/api/instance/bootstrap'),
}

export const adminApi = {
  users: () => request<AdminUser[]>('/api/admin/users'),
  createUser: (payload: { username: string; password: string }) =>
    post<AdminUser>('/api/admin/users', payload),
  grantAdmin: (id: number) => post<null>(`/api/admin/users/${id}/roles/admin`),
  revokeAdmin: (id: number) => del<null>(`/api/admin/users/${id}/roles/admin`),
  updateStatus: (id: number, status: number) =>
    put<null>(`/api/admin/users/${id}/status?status=${status}`),
  deleteUser: (id: number) => del<null>(`/api/admin/users/${id}`),
  changePassword: (id: number, newPassword: string) =>
    put<null>(`/api/admin/users/${id}/password`, { newPassword }),
  changeUsername: (id: number, newUsername: string) =>
    put<null>(`/api/admin/users/${id}/username`, { newUsername }),
}

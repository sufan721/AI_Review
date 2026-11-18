<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useUserStore } from '../stores/user'
import type { UserSettingKey } from '../types/user'

const user = useUserStore()

const error = ref('')
const settingDrafts = ref<Record<string, string>>({})
const savingKey = ref('')

const settingKeys: UserSettingKey[] = ['GENERAL', 'TAGS', 'EDITOR']
const settingLabel: Record<UserSettingKey, string> = {
  GENERAL: '通用偏好',
  TAGS: '标签规则',
  EDITOR: '编辑器偏好',
}
const settingHint: Record<UserSettingKey, string> = {
  GENERAL: '界面语言、默认可见性等基础项',
  TAGS: '自动提取标签的匹配规则',
  EDITOR: '编辑器行为，如自动保存、字数提示',
}

async function run(action: () => Promise<void>): Promise<void> {
  error.value = ''
  try {
    await action()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '操作失败'
  }
}

function load(): Promise<void> {
  return run(async () => {
    await user.fetchSettings()
    const drafts: Record<string, string> = {}
    for (const setting of user.settings) drafts[setting.key] = setting.value
    settingDrafts.value = drafts
  })
}

async function saveSetting(key: string): Promise<void> {
  await run(async () => {
    savingKey.value = key
    try {
      await user.updateSetting({ key, value: settingDrafts.value[key] ?? '' })
    } finally {
      savingKey.value = ''
    }
  })
}

onMounted(load)
</script>

<template>
  <section class="page page--mid">
    <header class="page-head">
      <div class="page-head__text">
        <h1 class="page-title">偏好设置</h1>
        <p class="page-sub">设置项只对当前账号生效</p>
      </div>
    </header>

    <p v-if="error !== ''" class="form-error">{{ error }}</p>

    <section class="card">
      <div class="card__head">
        <h2 class="card__title">设置项</h2>
        <span class="card__hint">支持 JSON 或纯文本</span>
      </div>

      <div class="settings">
        <div v-for="key in settingKeys" :key="key" class="setting">
          <div class="setting__info">
            <div class="setting__title">
              <span class="setting__name">{{ settingLabel[key] }}</span>
              <span class="chip chip--outline mono">{{ key }}</span>
            </div>
            <p class="setting__hint">{{ settingHint[key] }}</p>
          </div>
          <div class="setting__control">
            <textarea
              class="control mono"
              rows="2"
              :value="settingDrafts[key] ?? ''"
              :placeholder="`${settingLabel[key]} 设置值（JSON 或纯文本）`"
              :aria-label="`${settingLabel[key]} 设置值`"
              @input="settingDrafts[key] = ($event.target as HTMLTextAreaElement).value"
            />
            <button class="btn btn-sm" type="button" :disabled="savingKey === key" @click="saveSetting(key)">
              {{ savingKey === key ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.settings {
  display: flex;
  flex-direction: column;
}

.setting {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: var(--space-5);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--border);
}

.setting:last-child {
  border-bottom: none;
}

.setting__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.setting__name {
  font-size: 14px;
  font-weight: 600;
}

.setting__hint {
  margin: 4px 0 0;
  color: var(--fg-subtle);
  font-size: 12.5px;
  line-height: 1.5;
}

.setting__control {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  min-width: 0;
}

.setting__control textarea {
  flex: 1;
  min-height: 58px;
  background: var(--bg-sunken);
}

@media (max-width: 720px) {
  .setting {
    grid-template-columns: 1fr;
    gap: var(--space-2);
  }
}
</style>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import '../../styles/auth.css'

defineProps<{
  title: string
  subtitle?: string
}>()

const pointer = ref<{ x: number; y: number } | null>(null)

const purpleBody = ref<HTMLElement | null>(null)
const darkBody = ref<HTMLElement | null>(null)
const orangeBody = ref<HTMLElement | null>(null)
const yellowBody = ref<HTMLElement | null>(null)

const purpleFace = ref<HTMLElement | null>(null)
const darkFace = ref<HTMLElement | null>(null)
const orangeFace = ref<HTMLElement | null>(null)
const yellowFace = ref<HTMLElement | null>(null)

function onPointerMove(event: MouseEvent) {
  pointer.value = { x: event.clientX, y: event.clientY }
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

function bodyMotion(el: HTMLElement | null) {
  if (!el || !pointer.value) return { faceX: 0, faceY: 0, bodySkew: 0 }

  const rect = el.getBoundingClientRect()
  const dx = pointer.value.x - (rect.left + rect.width / 2)
  const faceOriginY = rect.top + rect.height / 3

  return {
    faceX: clamp(dx / 20, -15, 15),
    faceY: clamp((pointer.value.y - faceOriginY) / 30, -10, 10),
    bodySkew: clamp(-dx / 120, -6, 6),
  }
}

function lookOffset(el: HTMLElement | null, max: number) {
  if (!el || !pointer.value) return { x: 0, y: 0 }

  const rect = el.getBoundingClientRect()
  const dx = pointer.value.x - (rect.left + rect.width / 2)
  const dy = pointer.value.y - (rect.top + rect.height / 2)
  const distance = Math.min(Math.hypot(dx, dy), max)
  const angle = Math.atan2(dy, dx)

  return { x: Math.cos(angle) * distance, y: Math.sin(angle) * distance }
}

let purpleBlinkTimer: ReturnType<typeof setTimeout> | undefined
let darkBlinkTimer: ReturnType<typeof setTimeout> | undefined
const purpleBlink = ref(false)
const darkBlink = ref(false)

function scheduleBlink(who: 'purple' | 'dark') {
  const timer = setTimeout(
    () => {
      const flag = who === 'purple' ? purpleBlink : darkBlink
      flag.value = true
      setTimeout(() => {
        flag.value = false
        scheduleBlink(who)
      }, 150)
    },
    3000 + Math.random() * 4000,
  )

  if (who === 'purple') purpleBlinkTimer = timer
  else darkBlinkTimer = timer
}

function eyeStyle(size: number, blinking: boolean) {
  return {
    width: `${size}px`,
    height: blinking ? '2px' : `${size}px`,
  }
}

function pupilStyle(el: HTMLElement | null, max: number) {
  const { x, y } = lookOffset(el, max)
  return { transform: `translate(${x}px, ${y}px)` }
}

const purplePupilA = ref<HTMLElement | null>(null)
const purplePupilB = ref<HTMLElement | null>(null)
const darkPupilA = ref<HTMLElement | null>(null)
const darkPupilB = ref<HTMLElement | null>(null)
const orangePupilA = ref<HTMLElement | null>(null)
const orangePupilB = ref<HTMLElement | null>(null)
const yellowPupilA = ref<HTMLElement | null>(null)
const yellowPupilB = ref<HTMLElement | null>(null)

const purplePupilAStyle = computed(() => pupilStyle(purplePupilA.value, 5))
const purplePupilBStyle = computed(() => pupilStyle(purplePupilB.value, 5))
const darkPupilAStyle = computed(() => pupilStyle(darkPupilA.value, 4))
const darkPupilBStyle = computed(() => pupilStyle(darkPupilB.value, 4))
const orangePupilAStyle = computed(() => pupilStyle(orangePupilA.value, 5))
const orangePupilBStyle = computed(() => pupilStyle(orangePupilB.value, 5))
const yellowPupilAStyle = computed(() => pupilStyle(yellowPupilA.value, 5))
const yellowPupilBStyle = computed(() => pupilStyle(yellowPupilB.value, 5))

const purpleMotion = computed(() => bodyMotion(purpleBody.value))
const darkMotion = computed(() => bodyMotion(darkBody.value))
const orangeMotion = computed(() => bodyMotion(orangeBody.value))
const yellowMotion = computed(() => bodyMotion(yellowBody.value))

const purpleBodyStyle = computed(() => ({
  transform: `skewX(${purpleMotion.value.bodySkew}deg)`,
}))
const darkBodyStyle = computed(() => ({
  transform: `skewX(${darkMotion.value.bodySkew}deg)`,
}))
const orangeBodyStyle = computed(() => ({
  transform: `skewX(${orangeMotion.value.bodySkew}deg)`,
}))
const yellowBodyStyle = computed(() => ({
  transform: `skewX(${yellowMotion.value.bodySkew}deg)`,
}))

const purpleFaceStyle = computed(() => ({
  transform: `translate(${purpleMotion.value.faceX}px, ${purpleMotion.value.faceY}px)`,
}))
const darkFaceStyle = computed(() => ({
  transform: `translate(${darkMotion.value.faceX}px, ${darkMotion.value.faceY}px)`,
}))
const orangeFaceStyle = computed(() => ({
  transform: `translate(${orangeMotion.value.faceX}px, ${orangeMotion.value.faceY}px)`,
}))
const yellowFaceStyle = computed(() => ({
  transform: `translate(${yellowMotion.value.faceX}px, ${yellowMotion.value.faceY}px)`,
}))
const yellowMouthStyle = computed(() => ({
  transform: `translate(${yellowMotion.value.faceX}px, ${yellowMotion.value.faceY}px)`,
}))

onMounted(() => {
  window.addEventListener('mousemove', onPointerMove)
  scheduleBlink('purple')
  scheduleBlink('dark')
})
onUnmounted(() => {
  window.removeEventListener('mousemove', onPointerMove)
  clearTimeout(purpleBlinkTimer)
  clearTimeout(darkBlinkTimer)
})
</script>

<template>
  <div class="auth-page">
    <!-- 左侧品牌区（宽屏显示） -->
    <section class="auth-brand">
      <div class="auth-brand-top">
        <span class="auth-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 6.5A2.5 2.5 0 0 1 6.5 4H17a2.5 2.5 0 0 1 2.5 2.5v11A2.5 2.5 0 0 1 17 20H6.5A2.5 2.5 0 0 1 4 17.5z" />
            <path d="M8.5 9h7M8.5 12.5h7M8.5 16h4" />
          </svg>
        </span>
        <span>备忘录</span>
      </div>

      <div class="auth-brand-stage">
        <div class="auth-scene">
          <div ref="purpleBody" class="auth-pillar purple" :style="purpleBodyStyle" style="left: 70px; width: 180px; height: 400px">
            <div ref="purpleFace" class="auth-eyes" :style="purpleFaceStyle" style="left: 45px; top: 40px; gap: 32px">
              <div class="auth-eye" :style="eyeStyle(18, purpleBlink)">
                <div ref="purplePupilA" class="auth-pupil" :style="purplePupilAStyle" style="width: 7px; height: 7px"></div>
              </div>
              <div class="auth-eye" :style="eyeStyle(18, purpleBlink)">
                <div ref="purplePupilB" class="auth-pupil" :style="purplePupilBStyle" style="width: 7px; height: 7px"></div>
              </div>
            </div>
          </div>

          <div ref="darkBody" class="auth-pillar ink" :style="darkBodyStyle" style="left: 240px; width: 120px; height: 310px">
            <div ref="darkFace" class="auth-eyes" :style="darkFaceStyle" style="left: 26px; top: 32px; gap: 24px">
              <div class="auth-eye" :style="eyeStyle(16, darkBlink)">
                <div ref="darkPupilA" class="auth-pupil" :style="darkPupilAStyle" style="width: 6px; height: 6px"></div>
              </div>
              <div class="auth-eye" :style="eyeStyle(16, darkBlink)">
                <div ref="darkPupilB" class="auth-pupil" :style="darkPupilBStyle" style="width: 6px; height: 6px"></div>
              </div>
            </div>
          </div>

          <div ref="orangeBody" class="auth-pillar orange" :style="orangeBodyStyle" style="left: 0px; width: 240px; height: 200px">
            <div ref="orangeFace" class="auth-eyes dot-eyes" :style="orangeFaceStyle" style="left: 82px; top: 90px; gap: 32px">
              <div ref="orangePupilA" class="auth-pupil" :style="orangePupilAStyle" style="width: 12px; height: 12px"></div>
              <div ref="orangePupilB" class="auth-pupil" :style="orangePupilBStyle" style="width: 12px; height: 12px"></div>
            </div>
          </div>

          <div ref="yellowBody" class="auth-pillar yellow" :style="yellowBodyStyle" style="left: 310px; width: 140px; height: 230px">
            <div ref="yellowFace" class="auth-eyes dot-eyes" :style="yellowFaceStyle" style="left: 52px; top: 40px; gap: 24px">
              <div ref="yellowPupilA" class="auth-pupil" :style="yellowPupilAStyle" style="width: 12px; height: 12px"></div>
              <div ref="yellowPupilB" class="auth-pupil" :style="yellowPupilBStyle" style="width: 12px; height: 12px"></div>
            </div>
            <div class="auth-mouth" :style="yellowMouthStyle" style="left: 40px; top: 88px"></div>
          </div>
        </div>
      </div>

      <p class="auth-brand-foot">记录想法，沉淀知识 · 自托管，数据在自己手里</p>

      <div class="auth-grid" aria-hidden="true"></div>
      <div class="auth-blob auth-blob-a" aria-hidden="true"></div>
      <div class="auth-blob auth-blob-b" aria-hidden="true"></div>
    </section>

    <!-- 右侧表单区 -->
    <section class="auth-panel">
      <div class="auth-card">
        <div class="auth-mobile-brand">
          <span class="auth-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 6.5A2.5 2.5 0 0 1 6.5 4H17a2.5 2.5 0 0 1 2.5 2.5v11A2.5 2.5 0 0 1 17 20H6.5A2.5 2.5 0 0 1 4 17.5z" />
              <path d="M8.5 9h7M8.5 12.5h7M8.5 16h4" />
            </svg>
          </span>
          <span>备忘录</span>
        </div>

        <header class="auth-head">
          <h1>{{ title }}</h1>
          <p v-if="subtitle">{{ subtitle }}</p>
        </header>

        <slot />
      </div>
    </section>
  </div>
</template>

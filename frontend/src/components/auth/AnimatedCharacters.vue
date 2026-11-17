<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = defineProps<{
  typing: boolean
  passwordActive: boolean
  passwordVisible: boolean
}>()

const stage = ref<HTMLElement | null>(null)
const pointer = ref({ x: 270, y: 160 })
const purpleBlink = ref(false)
const darkBlink = ref(false)
const peeking = ref(false)
let blinkTimer: ReturnType<typeof setTimeout> | undefined
let darkBlinkTimer: ReturnType<typeof setTimeout> | undefined
let blinkEndTimer: ReturnType<typeof setTimeout> | undefined
let darkBlinkEndTimer: ReturnType<typeof setTimeout> | undefined
let peekTimer: ReturnType<typeof setTimeout> | undefined
let peekEndTimer: ReturnType<typeof setTimeout> | undefined

function onPointerMove(event: MouseEvent) {
  const bounds = stage.value?.getBoundingClientRect()
  if (bounds) pointer.value = { x: event.clientX - bounds.left, y: event.clientY - bounds.top }
}

function scheduleBlink(dark = false) {
  const timer = setTimeout(() => {
    if (dark) darkBlink.value = true
    else purpleBlink.value = true
    const endTimer = setTimeout(() => {
      if (dark) darkBlink.value = false
      else purpleBlink.value = false
      scheduleBlink(dark)
    }, 150)
    if (dark) darkBlinkEndTimer = endTimer
    else blinkEndTimer = endTimer
  }, 3000 + Math.random() * 4000)
  if (dark) darkBlinkTimer = timer
  else blinkTimer = timer
}

function schedulePeek() {
  peekTimer = setTimeout(() => {
    if (props.passwordActive && props.passwordVisible) {
      peeking.value = true
      peekEndTimer = setTimeout(() => {
        peeking.value = false
        schedulePeek()
      }, 800)
    } else schedulePeek()
  }, 2000 + Math.random() * 3000)
}

const hiding = computed(() => props.passwordActive && !props.passwordVisible)
const lookingAway = computed(() => props.passwordActive && props.passwordVisible)

function eyeOffset(x: number, y: number, max: number) {
  if (lookingAway.value) return { x: peeking.value ? max : -max, y: peeking.value ? max : -max }
  if (hiding.value) return { x: -max, y: -max }
  const dx = pointer.value.x - x
  const dy = pointer.value.y - y
  const distance = Math.hypot(dx, dy) || 1
  return { x: Math.round(dx / distance * Math.min(max, distance / 25)), y: Math.round(dy / distance * Math.min(max, distance / 25)) }
}

const purpleEye = computed(() => eyeOffset(205, 110, 5))
const darkEye = computed(() => eyeOffset(315, 180, 4))
const orangeEye = computed(() => eyeOffset(142, 310, 5))
const yellowEye = computed(() => eyeOffset(392, 245, 5))
const purpleStyle = computed(() => ({
  height: props.typing || hiding.value ? '440px' : '400px',
  transform: lookingAway.value ? 'skewX(0deg)' : hiding.value || props.typing ? 'skewX(-12deg) translateX(40px)' : `skewX(${Math.max(-6, Math.min(6, (270 - pointer.value.x) / 70))}deg)`,
}))
const darkStyle = computed(() => ({
  transform: lookingAway.value ? 'skewX(0deg)' : `skewX(${Math.max(-6, Math.min(6, (270 - pointer.value.x) / 70))}deg)`,
}))

onMounted(() => {
  window.addEventListener('mousemove', onPointerMove)
  scheduleBlink()
  scheduleBlink(true)
  schedulePeek()
})
onUnmounted(() => {
  window.removeEventListener('mousemove', onPointerMove)
  clearTimeout(blinkTimer)
  clearTimeout(darkBlinkTimer)
  clearTimeout(blinkEndTimer)
  clearTimeout(darkBlinkEndTimer)
  clearTimeout(peekTimer)
  clearTimeout(peekEndTimer)
})
</script>

<template>
  <div ref="stage" class="characters" aria-hidden="true">
    <div class="character purple" :style="purpleStyle">
      <div class="eyes white-eyes purple-eyes" :class="{ blink: purpleBlink }">
        <span class="eye"><i :style="{ transform: `translate(${purpleEye.x}px, ${purpleEye.y}px)` }" /></span>
        <span class="eye"><i :style="{ transform: `translate(${purpleEye.x}px, ${purpleEye.y}px)` }" /></span>
      </div>
    </div>
    <div class="character dark" :style="darkStyle">
      <div class="eyes white-eyes dark-eyes" :class="{ blink: darkBlink }">
        <span class="eye"><i :style="{ transform: `translate(${darkEye.x}px, ${darkEye.y}px)` }" /></span>
        <span class="eye"><i :style="{ transform: `translate(${darkEye.x}px, ${darkEye.y}px)` }" /></span>
      </div>
    </div>
    <div class="character orange">
      <div class="eyes dot-eyes orange-eyes" :style="{ transform: `translate(${orangeEye.x}px, ${orangeEye.y}px)` }"><i /><i /></div>
    </div>
    <div class="character yellow">
      <div class="eyes dot-eyes yellow-eyes" :style="{ transform: `translate(${yellowEye.x}px, ${yellowEye.y}px)` }"><i /><i /></div>
      <div class="mouth" />
    </div>
  </div>
</template>

<style scoped>
.characters { position: relative; width: 550px; height: 400px; flex: none; }
.character { position: absolute; bottom: 0; transform-origin: bottom center; transition: transform .5s ease, height .5s ease; }
.purple { left: 70px; width: 180px; height: 400px; border-radius: 10px 10px 0 0; background: var(--character-purple); z-index: 1; }
.dark { left: 240px; width: 120px; height: 310px; border-radius: 8px 8px 0 0; background: var(--character-dark); z-index: 2; }
.orange { left: 0; width: 240px; height: 200px; border-radius: 120px 120px 0 0; background: var(--character-orange); z-index: 3; }
.yellow { left: 310px; width: 140px; height: 230px; border-radius: 70px 70px 0 0; background: var(--character-yellow); z-index: 4; }
.eyes { position: absolute; display: flex; align-items: center; transition: transform .1s ease-out; }
.purple-eyes { left: 45px; top: 40px; gap: 32px; }
.dark-eyes { left: 26px; top: 32px; gap: 24px; }
.eye { display: grid; place-items: center; width: 18px; height: 18px; border-radius: 50%; background: white; transition: height .15s ease; }
.dark .eye { width: 16px; height: 16px; }
.eye i { width: 7px; height: 7px; border-radius: 50%; background: var(--character-dark); transition: transform .1s ease-out; }
.dark .eye i { width: 6px; height: 6px; }
.blink .eye { height: 2px; overflow: hidden; }
.dot-eyes { gap: 32px; }
.dot-eyes i { width: 12px; height: 12px; border-radius: 50%; background: var(--character-dark); }
.orange-eyes { left: 82px; top: 90px; }
.yellow-eyes { left: 52px; top: 40px; gap: 24px; }
.mouth { position: absolute; left: 40px; top: 88px; width: 80px; height: 4px; border-radius: 4px; background: var(--character-dark); }
@media (max-width: 1250px) { .characters { transform: scale(.85); transform-origin: center bottom; } }
@media (prefers-reduced-motion: reduce) {
  .character, .eyes, .eye, .eye i { transition: none; }
}
</style>

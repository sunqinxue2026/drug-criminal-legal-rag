/**
 * 权限指令
 * 用法: v-permission="['admin']"
 */
import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

export const permission: Directive = {
  mounted(el, binding) {
    const { value } = binding
    const userStore = useUserStore()
    const userRole = userStore.user?.role

    if (value && value instanceof Array && value.length > 0) {
      const permissionRoles = value
      const hasPermission = permissionRoles.includes(userRole)

      if (!hasPermission) {
        el.style.display = 'none'
        // 或者直接移除元素
        // el.parentNode?.removeChild(el)
      }
    } else {
      throw new Error('需要指定权限角色，如 v-permission="[\'admin\']"')
    }
  }
}

/**
 * 防抖指令
 * 用法: v-debounce:500="handleClick"
 */
export const debounce: Directive = {
  mounted(el, binding) {
    const { value, arg } = binding
    const delay = arg ? parseInt(arg) : 300

    let timer: ReturnType<typeof setTimeout> | null = null

    el.addEventListener('click', () => {
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => {
        value()
      }, delay)
    })
  }
}

/**
 * 节流指令
 * 用法: v-throttle:1000="handleClick"
 */
export const throttle: Directive = {
  mounted(el, binding) {
    const { value, arg } = binding
    const delay = arg ? parseInt(arg) : 1000

    let lastTime = 0

    el.addEventListener('click', () => {
      const now = Date.now()
      if (now - lastTime >= delay) {
        lastTime = now
        value()
      }
    })
  }
}

/**
 * 复制指令
 * 用法: v-copy="text"
 */
export const copy: Directive = {
  mounted(el, binding) {
    el.addEventListener('click', () => {
      const text = binding.value
      if (!text) return

      if (navigator.clipboard) {
        navigator.clipboard.writeText(text).then(() => {
          console.log('复制成功')
        })
      } else {
        // 兼容旧浏览器
        const textarea = document.createElement('textarea')
        textarea.value = text
        textarea.style.position = 'fixed'
        textarea.style.opacity = '0'
        document.body.appendChild(textarea)
        textarea.select()
        document.execCommand('copy')
        document.body.removeChild(textarea)
        console.log('复制成功')
      }
    })
  }
}

/**
 * 懒加载指令
 * 用法: v-lazy="imageUrl"
 */
export const lazy: Directive = {
  mounted(el, binding) {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const img = entry.target as HTMLImageElement
          img.src = binding.value
          observer.unobserve(img)
        }
      })
    })

    observer.observe(el)
  }
}

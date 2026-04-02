/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent, ComponentPublicInstance } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

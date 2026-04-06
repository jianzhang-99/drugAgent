import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// TDesign 按需引入
import {
  Button,
  Icon,
  Input,
  Textarea,
  Select,
  Option,
  Dialog,
  Drawer,
  Table,
  Tag,
  Loading,
  MessagePlugin
} from 'tdesign-vue-next'
import 'tdesign-vue-next/dist/tdesign.css'
import 'element-plus/dist/index.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Button)
app.use(Icon)
app.use(Input)
app.use(Textarea)
app.use(Select)
app.use(Option)
app.use(Dialog)
app.use(Drawer)
app.use(Table)
app.use(Tag)
app.use(Loading)

// 挂载全局消息组件
app.config.globalProperties.$message = MessagePlugin

app.mount('#app')

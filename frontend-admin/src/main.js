
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as Icons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './styles/admin.css'

const app = createApp(App)
for (const [key, component] of Object.entries(Icons)) app.component(key, component)
app.use(createPinia()).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')

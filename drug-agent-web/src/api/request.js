import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
    baseURL: '/api',
    timeout: 60000 // AI接口可能较慢，设60秒
})

// 请求拦截器
request.interceptors.request.use(config => {
    // 后续可加 Token
    return config
})

// 响应拦截器（兼容两种返回结构）
// 1) 统一结构: { code, message, data }
// 2) 直接结构: { ...业务字段 }
request.interceptors.response.use(
    response => {
        const res = response.data

        // 统一结构
        if (res && typeof res === 'object' && Object.prototype.hasOwnProperty.call(res, 'code')) {
            if (res.code !== 200) {
                ElMessage.error(res.message || '请求失败')
                return Promise.reject(new Error(res.message || '请求失败'))
            }
            return res.data
        }

        // 非统一结构：直接返回
        return res
    },
    error => {
        const status = error?.response?.status
        const backendMsg = error?.response?.data?.message
        const msg = backendMsg || (status ? `请求失败(${status})` : '网络异常，请检查后端服务和代理配置')
        ElMessage.error(msg)
        return Promise.reject(error)
    }
)

export default request

import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
    baseURL: '/api',
    timeout: 60000      // AI接口可能较慢，设60秒
})

// 请求拦截器
request.interceptors.request.use(config => {
    // 后续可加 Token
    return config
})

// 响应拦截器
request.interceptors.response.use(
    response => {
        const res = response.data
        if (res.code !== 200) {
            ElMessage.error(res.message || '请求失败')
            return Promise.reject(new Error(res.message))
        }
        return res.data
    },
    error => {
        ElMessage.error(error.response?.data?.message || '网络异常')
        return Promise.reject(error)
    }
)

export default request

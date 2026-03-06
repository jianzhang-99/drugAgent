import request from './request'

// 上传文件
export const uploadFile = (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/compliance/file/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}

// 发送对话
export const sendChat = async (data) => {
    // 走后端私有化聚合接口，匹配 “合规审查专家” Profile
    return request.post('/compliance/chat/send', data)
}

// 获取会话列表
export const getSessions = (type = 'compliance') => {
    return request.get('/compliance/sessions', { params: { type } })
}

// 获取会话消息
export const getMessages = (sessionId) => {
    return request.get(`/compliance/sessions/${sessionId}/messages`)
}

// 创建新会话
export const createSession = (data) => {
    return request.post('/compliance/sessions', data)
}

// 删除会话
export const deleteSession = (id) => {
    return request.delete(`/compliance/sessions/${id}`)
}

// 获取文件列表
export const getFiles = () => request.get('/compliance/files')

// 删除文件
export const deleteFile = (id) => request.delete(`/compliance/files/${id}`)

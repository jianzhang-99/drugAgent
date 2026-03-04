import request from './request'

export const uploadKnowledge = (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/knowledge/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}

export const getKnowledgeList = () => request.get('/knowledge/list')

export const deleteKnowledge = (id) => request.delete(`/knowledge/${id}`)

export const testSearch = (query, topK = 5) => {
    return request.post('/knowledge/test-search', { query, topK })
}

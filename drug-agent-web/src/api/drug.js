import request from './request'

// 导入 Excel
export const importExcel = (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/drug/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}

// 下载模板
export const downloadTemplate = () => {
    window.open('http://localhost:8123/api/drug/template', '_blank')
}

// 查询药品列表
export const queryDrugList = (params) => {
    return request.get('/drug/list', { params })
}

// 获取药品名称列表（下拉）
export const getDrugNames = () => request.get('/drug/names')

// AI 分析
export const analyzeDrug = (data) => {
    return request.post('/drug/analyze', data)
}

import request from '@/api/request'

// Tender 标书审查 API
export const tenderApi = {
  // 查询标书审查任务列表
  listTasks: () => request.get('/tender/tasks'),

  // 获取任务详情
  getTask: (caseId) => request.get(`/tender/tasks/${caseId}`),

  // 获取审查结果
  getResult: (caseId) => request.get(`/tender/tasks/${caseId}/result`),

  // 执行审查任务
  execute: (caseId) => request.post(`/tender/tasks/${caseId}/execute`),

  // 创建标书审查任务（上传文件）
  createCase: (files, submittedBy = 'anonymous') => {
    const formData = new FormData()
    if (Array.isArray(files)) {
      files.forEach(file => formData.append('files', file))
    } else {
      formData.append('files', files)
    }
    formData.append('submittedBy', submittedBy)

    return request.post('/tender/cases', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 解析指定文档
  parseDocument: (caseId, docId) => request.post(`/tender/cases/${caseId}/parse/${docId}`)
}

/**
 * Gemini API Service
 * 提供与 Gemini API 的交互能力
 *
 * 设计说明：
 * - 当前为 Mock 模式，返回模拟数据
 * - 预留切换真实后端接口的能力
 * - 包含重试逻辑和错误处理
 */

// API 配置
const API_CONFIG = {
  // Mock 模式开关 - 设为 false 可切换到真实 API
  MOCK_MODE: true,

  // 真实 API 配置 (当 MOCK_MODE = false 时使用)
  API_ENDPOINT: 'https://api.gemini.example.com/v1/chat',
  API_KEY: '',
  MODEL: 'gemini-pro',

  // 重试配置
  MAX_RETRIES: 5,
  RETRY_DELAY_BASE: 1000,
  RETRY_DELAY_MAX: 30000,

  // 超时配置
  TIMEOUT: 60000
}

// 当前使用的后端模式
let currentBackend = 'mock'

/**
 * 设置后端模式
 * @param {string} mode - 'mock' 或 'real'
 */
export const setBackendMode = (mode) => {
  if (mode === 'mock' || mode === 'real') {
    currentBackend = mode
    console.log(`[GeminiService] Backend mode switched to: ${mode}`)
  } else {
    console.warn(`[GeminiService] Unknown backend mode: ${mode}, keeping current: ${currentBackend}`)
  }
}

/**
 * 获取当前后端模式
 */
export const getBackendMode = () => currentBackend

/**
 * 延迟函数
 * @param {number} ms - 延迟毫秒数
 */
const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms))

/**
 * 计算指数退避延迟
 * @param {number} attempt - 当前尝试次数 (从 0 开始)
 */
const getRetryDelay = (attempt) => {
  const d = API_CONFIG.RETRY_DELAY_BASE * Math.pow(2, attempt)
  return Math.min(d, API_CONFIG.RETRY_DELAY_MAX)
}

/**
 * 带重试的 fetch 封装
 * @param {string} url - 请求 URL
 * @param {object} options - fetch 选项
 */
const fetchWithRetry = async (url, options = {}) => {
  let lastError

  for (let attempt = 0; attempt < API_CONFIG.MAX_RETRIES; attempt++) {
    try {
      const controller = new AbortController()
      const timeoutId = setTimeout(() => controller.abort(), API_CONFIG.TIMEOUT)

      const response = await fetch(url, {
        ...options,
        signal: controller.signal
      })

      clearTimeout(timeoutId)

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      return await response.json()
    } catch (error) {
      lastError = error
      console.warn(`[GeminiService] Attempt ${attempt + 1} failed:`, error.message)

      if (attempt < API_CONFIG.MAX_RETRIES - 1) {
        const retryDelay = getRetryDelay(attempt)
        console.log(`[GeminiService] Retrying in ${retryDelay}ms...`)
        await delay(retryDelay)
      }
    }
  }

  throw new Error(`All ${API_CONFIG.MAX_RETRIES} attempts failed. Last error: ${lastError.message}`)
}

/**
 * 构建请求头
 */
const buildHeaders = () => {
  const headers = {
    'Content-Type': 'application/json'
  }

  if (!API_CONFIG.MOCK_MODE && API_CONFIG.API_KEY) {
    headers['Authorization'] = `Bearer ${API_CONFIG.API_KEY}`
  }

  return headers
}

/**
 * 构建请求体
 * @param {string} prompt - 用户输入
 * @param {string} systemInstruction - 系统指令
 */
const buildRequestBody = (prompt, systemInstruction) => {
  return {
    model: API_CONFIG.MODEL,
    messages: [
      {
        role: 'system',
        content: systemInstruction || '你是一个专业的医药监管合规AI助手。'
      },
      {
        role: 'user',
        content: prompt
      }
    ],
    temperature: 0.7,
    top_p: 0.9,
    max_tokens: 2048
  }
}

/**
 * 调用真实 Gemini API
 * @param {string} prompt - 用户输入
 * @param {string} systemInstruction - 系统指令
 */
const callRealGeminiAPI = async (prompt, systemInstruction) => {
  const response = await fetchWithRetry(API_CONFIG.API_ENDPOINT, {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify(buildRequestBody(prompt, systemInstruction))
  })

  if (!response.choices || !response.choices[0]) {
    throw new Error('Invalid API response format')
  }

  return response.choices[0].message.content
}

/**
 * Mock API 调用
 * 根据场景返回模拟结果
 * @param {string} prompt - 用户输入
 * @param {string} systemInstruction - 系统指令
 */
const callMockAPI = async (prompt, systemInstruction) => {
  // 模拟网络延迟
  await delay(1500 + Math.random() * 1000)

  // 根据 prompt 内容判断场景
  let scene = 'tender'
  if (prompt.includes('合同') || prompt.includes('采购合同')) {
    scene = 'contract'
  } else if (prompt.includes('合规') || prompt.includes('预警') || prompt.includes('分析')) {
    scene = 'compliance'
  }

  // 导入 mock 服务生成结果
  const { generateMockResult } = await import('./mock.js')
  const result = generateMockResult(scene)

  return {
    success: true,
    scene,
    result,
    message: result.summary
  }
}

/**
 * 主调用函数 - 调用 Gemini API
 *
 * @param {string} prompt - 用户输入的 prompt
 * @param {string} systemInstruction - 可选的系统指令
 * @param {object} options - 可选配置 { backendMode: 'mock' | 'real' }
 * @returns {Promise<object>} - 返回结果对象
 */
export const callGemini = async (prompt, systemInstruction = null, options = {}) => {
  const backendMode = options.backendMode || currentBackend

  console.log(`[GeminiService] Calling Gemini with ${backendMode} backend...`)
  console.log(`[GeminiService] Prompt:`, prompt.substring(0, 100) + (prompt.length > 100 ? '...' : ''))

  const startTime = Date.now()

  try {
    let result

    if (backendMode === 'real' && !API_CONFIG.MOCK_MODE) {
      result = await callRealGeminiAPI(prompt, systemInstruction)
    } else {
      result = await callMockAPI(prompt, systemInstruction)
    }

    const duration = Date.now() - startTime
    console.log(`[GeminiService] Request completed in ${duration}ms`)

    return {
      success: true,
      data: result,
      duration,
      backend: backendMode
    }
  } catch (error) {
    const duration = Date.now() - startTime
    console.error(`[GeminiService] Request failed after ${duration}ms:`, error)

    return {
      success: false,
      error: error.message,
      duration,
      backend: backendMode
    }
  }
}

/**
 * 流式调用 (预留接口)
 * @param {string} prompt - 用户输入
 * @param {string} systemInstruction - 系统指令
 * @param {function} onChunk - 接收数据块的回调
 */
export const callGeminiStream = async (prompt, systemInstruction, onChunk) => {
  console.warn('[GeminiService] Streaming is not yet implemented, using regular call')

  const result = await callGemini(prompt, systemInstruction)

  if (result.success && onChunk) {
    // 模拟流式返回
    const message = result.data.message || result.data
    const words = message.split('')

    for (const word of words) {
      onChunk(word)
      await delay(30)
    }
  }

  return result
}

/**
 * 健康检查
 */
export const healthCheck = async () => {
  if (currentBackend === 'mock' || API_CONFIG.MOCK_MODE) {
    return {
      status: 'healthy',
      backend: 'mock',
      message: 'Mock service is running'
    }
  }

  try {
    const response = await fetchWithRetry(`${API_CONFIG.API_ENDPOINT}/health`, {
      method: 'GET',
      headers: buildHeaders()
    })

    return {
      status: 'healthy',
      backend: 'real',
      data: response
    }
  } catch (error) {
    return {
      status: 'unhealthy',
      backend: 'real',
      error: error.message
    }
  }
}

export default {
  callGemini,
  callGeminiStream,
  setBackendMode,
  getBackendMode,
  healthCheck
}

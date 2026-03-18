const STORAGE_KEYS = {
  recentTenderTask: 'recentTenderTask',
  tenderTasks: 'tenderTasks',
  tenderParseResults: 'tenderParseResults',
  knowledgeDocuments: 'knowledgeDocuments',
  knowledgeRules: 'knowledgeRules',
  knowledgeDictionaries: 'knowledgeDictionaries',
  auditLogs: 'auditLogs',
  userPreferences: 'userPreferences'
}

const readJson = (key, fallback) => {
  const raw = localStorage.getItem(key)
  if (!raw) return fallback
  try {
    return JSON.parse(raw)
  } catch (error) {
    console.error(`Failed to parse storage key: ${key}`, error)
    return fallback
  }
}

const writeJson = (key, value) => {
  localStorage.setItem(key, JSON.stringify(value))
}

export const getStorageKey = (name) => STORAGE_KEYS[name]

export const getRecentTenderTask = () => readJson(STORAGE_KEYS.recentTenderTask, null)

export const setRecentTenderTask = (task) => writeJson(STORAGE_KEYS.recentTenderTask, task)

export const getTenderTasks = () => readJson(STORAGE_KEYS.tenderTasks, [])

export const upsertTenderTask = (task) => {
  const tasks = getTenderTasks()
  const nextTasks = [task, ...tasks.filter((item) => item.caseId !== task.caseId)]
  writeJson(STORAGE_KEYS.tenderTasks, nextTasks)
}

export const updateTenderTask = (caseId, updater) => {
  const tasks = getTenderTasks()
  const nextTasks = tasks.map((task) => {
    if (task.caseId !== caseId) return task
    return typeof updater === 'function' ? updater(task) : { ...task, ...updater }
  })
  writeJson(STORAGE_KEYS.tenderTasks, nextTasks)
}

export const getTenderParseResults = () => readJson(STORAGE_KEYS.tenderParseResults, {})

export const setTenderParseResult = (caseId, docId, result) => {
  const allResults = getTenderParseResults()
  const caseResults = allResults[caseId] || {}
  caseResults[docId] = result
  allResults[caseId] = caseResults
  writeJson(STORAGE_KEYS.tenderParseResults, allResults)
}

export const getKnowledgeDocuments = () => readJson(STORAGE_KEYS.knowledgeDocuments, [])

export const setKnowledgeDocuments = (documents) => writeJson(STORAGE_KEYS.knowledgeDocuments, documents)

export const getKnowledgeRules = () => {
  return readJson(STORAGE_KEYS.knowledgeRules, [
    { id: 'rule-1', name: '围标相似度规则组', scene: '标书审查', enabled: true, priority: '高' },
    { id: 'rule-2', name: '合同敏感条款规则组', scene: '合同预审', enabled: true, priority: '中' },
    { id: 'rule-3', name: '价格异常波动规则组', scene: '合规预警', enabled: false, priority: '中' }
  ])
}

export const setKnowledgeRules = (rules) => writeJson(STORAGE_KEYS.knowledgeRules, rules)

export const getKnowledgeDictionaries = () => {
  return readJson(STORAGE_KEYS.knowledgeDictionaries, [
    { id: 'dict-1', type: '药品', term: '阿司匹林肠溶片', alias: '阿司匹林' },
    { id: 'dict-2', type: '器械', term: '冠脉支架系统', alias: '冠脉支架' },
    { id: 'dict-3', type: '供应商', term: '横渡医疗科技有限公司', alias: '横渡医疗' }
  ])
}

export const setKnowledgeDictionaries = (items) => writeJson(STORAGE_KEYS.knowledgeDictionaries, items)

export const getAuditLogs = () => readJson(STORAGE_KEYS.auditLogs, [])

export const appendAuditLog = (log) => {
  const logs = getAuditLogs()
  writeJson(STORAGE_KEYS.auditLogs, [log, ...logs].slice(0, 100))
}

export const getUserPreferences = () => {
  return readJson(STORAGE_KEYS.userPreferences, {
    submittedBy: 'anonymous',
    streamOutput: true,
    autoParseAfterUpload: true,
    defaultSceneHint: 'AUTO'
  })
}

export const setUserPreferences = (preferences) => writeJson(STORAGE_KEYS.userPreferences, preferences)

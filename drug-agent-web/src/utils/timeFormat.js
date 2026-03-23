/**
 * 统一时间格式化工具
 * 规则：
 * - 今天内的消息显示：HH:mm
 * - 昨天的消息显示：昨天 HH:mm
 * - 7天内的消息显示：星期X HH:mm
 * - 超过7天的显示：YYYY-MM-DD
 *
 * @param {string|Date} isoString - ISO 格式的时间字符串或 Date 对象
 * @returns {string} 格式化后的时间字符串
 */
export function formatTime(isoString) {
  if (!isoString) return ''

  const date = new Date(isoString)
  if (isNaN(date.getTime())) return ''

  const now = new Date()
  const diff = now - date
  const oneDay = 24 * 60 * 60 * 1000
  const oneWeek = 7 * oneDay

  // 今天：HH:mm
  if (diff < oneDay && date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  // 昨天：昨天 HH:mm
  const yesterday = new Date(now - oneDay)
  if (diff < 2 * oneDay && yesterday.toDateString() === date.toDateString()) {
    return '昨天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  // 7天内：星期X HH:mm
  if (diff < oneWeek) {
    const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
    return weekdays[date.getDay()] + ' ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  // 超过7天：YYYY-MM-DD
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

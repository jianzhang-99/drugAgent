/**
 * LocalStorage Utility Functions
 */

/**
 * Save data to localStorage
 * @param {string} key - The key to store the data under
 * @param {any} data - The data to store (will be JSON stringified)
 */
export function save(key, data) {
  try {
    const serialized = JSON.stringify(data)
    localStorage.setItem(key, serialized)
    return true
  } catch (error) {
    console.error(`Failed to save ${key} to localStorage:`, error)
    return false
  }
}

/**
 * Load data from localStorage
 * @param {string} key - The key to retrieve data for
 * @param {any} [defaultValue=null] - Default value if key doesn't exist
 */
export function load(key, defaultValue = null) {
  try {
    const item = localStorage.getItem(key)
    if (item === null) return defaultValue
    return JSON.parse(item)
  } catch (error) {
    console.error(`Failed to load ${key} from localStorage:`, error)
    return defaultValue
  }
}

/**
 * Clear a specific key from localStorage
 */
export function clear(key) {
  try {
    localStorage.removeItem(key)
    return true
  } catch (error) {
    console.error(`Failed to clear ${key} from localStorage:`, error)
    return false
  }
}

/**
 * Clear all keys with a specific prefix
 */
export function clearPrefix(prefix) {
  let count = 0
  const keysToRemove = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key && key.startsWith(prefix)) keysToRemove.push(key)
  }
  keysToRemove.forEach(key => { localStorage.removeItem(key); count++ })
  return count
}

export default { save, load, clear, clearPrefix }

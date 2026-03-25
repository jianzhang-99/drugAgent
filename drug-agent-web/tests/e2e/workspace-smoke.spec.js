import { test, expect, request } from '@playwright/test'

const backendBaseUrl = 'http://127.0.0.1:8124/api'

async function createSession(apiContext, title) {
  const response = await apiContext.post(`${backendBaseUrl}/agent/sessions`, {
    data: {
      title,
      scene: 'general'
    }
  })
  const payload = await response.json()
  return payload.data
}

async function addAssistantMessage(apiContext, sessionId, content) {
  await apiContext.post(`${backendBaseUrl}/agent/sessions/${sessionId}/messages`, {
    data: {
      role: 'assistant',
      content
    }
  })
}

async function deleteSession(apiContext, sessionId) {
  await apiContext.delete(`${backendBaseUrl}/agent/sessions/${sessionId}`)
}

test.describe('Workspace smoke flow', () => {
  let apiContext

  test.beforeAll(async () => {
    apiContext = await request.newContext()
  })

  test.afterAll(async () => {
    await apiContext.dispose()
  })

  test('can open a history session from sidebar', async ({ page }) => {
    const title = `投资人演示历史会话-${Date.now()}`
    const session = await createSession(apiContext, title)
    await addAssistantMessage(apiContext, session.id, '这是一条用于自动化验证的历史消息')

    await page.goto('/')
    await page.getByTestId(`history-session-${session.id}`).click()

    await expect(page).toHaveURL(new RegExp(`sessionId=${session.id}`))
    await expect(page.getByText('这是一条用于自动化验证的历史消息')).toBeVisible()

    await deleteSession(apiContext, session.id)
  })

  test('can start a new chat and stay on the created session', async ({ page }) => {
    await page.route('**/api/agent/chat', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            answer: '自动化测试响应',
            summary: '自动化测试响应',
            scene: 'GENERAL'
          }
        })
      })
    })

    await page.goto('/')
    await page.getByTestId('new-chat-button').click()
    await page.getByTestId('chat-input').fill('请帮我检查演示链路')
    await page.getByTestId('send-button').click()

    await expect(page).toHaveURL(/sessionId=/)
    await expect(page.getByText('请帮我检查演示链路')).toBeVisible()
    await expect(page.getByText('自动化测试响应').first()).toBeVisible()

    const currentUrl = new URL(page.url())
    const sessionId = currentUrl.searchParams.get('sessionId')
    if (sessionId) {
      await deleteSession(apiContext, sessionId)
    }
  })
})

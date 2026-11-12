import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test, expect, type Page } from '@playwright/test'

const adminUser = { username: 'admin', password: 'admin123' }
const normalUser = { username: 'user1', password: '123456' }
const apiBase = 'http://localhost:8080/api'
const drugKeyword = '\u6bd2\u54c1'
const drugQuestion =
  '\u5bb6\u5c5e\u6536\u5230\u6d89\u6bd2\u6848\u4ef6\u62d8\u7559\u901a\u77e5\u540e\uff0c\u73b0\u5728\u6700\u5e94\u8be5\u5148\u505a\u4ec0\u4e48\uff1f'
const autoTestName = '\u81ea\u52a8\u5316\u6d4b\u8bd5'
const favoriteLabel = '\u6536\u85cf'
const unfavoriteLabel = '\u53d6\u6d88'
const currentDir = path.dirname(fileURLToPath(import.meta.url))

async function loginByApi(page: Page, username: string, password: string) {
  const response = await page.request.post(`${apiBase}/user/login`, {
    data: { username, password }
  })
  expect(response.ok()).toBeTruthy()
  const payload = await response.json()
  const auth = payload.data
  await page.addInitScript((loginPayload) => {
    localStorage.setItem('token', String(loginPayload.token ?? ''))
    localStorage.setItem('user', JSON.stringify(loginPayload.user ?? null))
  }, auth)
}

async function getFirstItemId(page: Page, keyword = '') {
  const response = await page.request.get(`${apiBase}/item/page`, {
    params: {
      pageNum: 1,
      pageSize: 1,
      keyword: keyword || undefined
    }
  })
  expect(response.ok()).toBeTruthy()
  const payload = await response.json()
  return payload.data.records[0]?.id as number | undefined
}

test.describe('legal-rag smoke', () => {
  test('normal user can search cases', async ({ page }) => {
    await loginByApi(page, normalUser.username, normalUser.password)
    await page.goto('/items')

    const inputs = page.locator('input')
    await inputs.nth(1).fill(drugKeyword)
    await page.locator('.filters .el-button--primary').click()
    await expect(page.locator('.case-card').first()).toContainText(drugKeyword)

    await inputs.nth(1).fill('__codex_nohit__')
    await page.locator('.filters .el-button--primary').click()
    await expect(page.locator('.case-card')).toHaveCount(0)
  })

  test('normal user can send chat message and receive references', async ({ page }) => {
    await loginByApi(page, normalUser.username, normalUser.password)
    await page.goto('/chat')

    await page.locator('.new-session').click()
    await page.locator('textarea').fill(drugQuestion)
    await page.locator('.composer .el-button--primary').click()

    await expect(page.locator('.loading-card')).toHaveCount(0, { timeout: 60_000 })
    await expect(page.locator('.analysis-card').first()).toBeVisible({ timeout: 60_000 })
    await expect(page.locator('.reference-card').first()).toBeVisible({ timeout: 60_000 })
  })

  test('admin can filter cases in item management', async ({ page }) => {
    await loginByApi(page, adminUser.username, adminUser.password)
    await page.goto('/admin/items')

    await page.locator('.filter-grid input').first().fill(drugKeyword)
    await page.locator('.filter-grid .el-button--primary').click()

    await expect(page.locator('.el-table__body-wrapper tbody tr').first()).toContainText(drugKeyword)
    await expect(page.locator('.stat-card strong').first()).not.toContainText('0')
  })

  test('register API stores profile fields', async ({ page }) => {
    const stamp = Date.now()
    const username = `codex_e2e_${stamp}`
    const registerResponse = await page.request.post(`${apiBase}/user/register`, {
      data: {
        username,
        password: 'Pass123456',
        realName: autoTestName,
        phone: '13800000000',
        email: `${username}@test.local`
      }
    })
    expect(registerResponse.ok()).toBeTruthy()
    const registerPayload = await registerResponse.json()
    const userId = registerPayload.data.id

    const infoResponse = await page.request.get(`${apiBase}/user/info/${userId}`)
    expect(infoResponse.ok()).toBeTruthy()
    const infoPayload = await infoResponse.json()

    expect(infoPayload.data.realName).toBe(autoTestName)
    expect(infoPayload.data.phone).toBe('13800000000')
    expect(infoPayload.data.email).toBe(`${username}@test.local`)

    await page.request.delete(`${apiBase}/user/${userId}`)
  })

  test('normal user can favorite case in detail page', async ({ page }) => {
    await loginByApi(page, normalUser.username, normalUser.password)
    const itemId = await getFirstItemId(page, drugKeyword)
    expect(itemId).toBeTruthy()

    await page.goto(`/item/${itemId}`)
    const favoriteButton = page.locator('.detail-side .el-button').nth(1)
    const initialLabel = (await favoriteButton.textContent()) || ''

    await favoriteButton.click()
    if (initialLabel.includes(favoriteLabel)) {
      await expect(favoriteButton).toContainText(unfavoriteLabel)
      await favoriteButton.click()
      await expect(favoriteButton).toContainText(favoriteLabel)
    } else {
      await expect(favoriteButton).toContainText(favoriteLabel)
      await favoriteButton.click()
      await expect(favoriteButton).toContainText(unfavoriteLabel)
      await favoriteButton.click()
      await expect(favoriteButton).toContainText(favoriteLabel)
    }
  })

  test('admin can import a case file and see receipt', async ({ page }) => {
    await loginByApi(page, adminUser.username, adminUser.password)
    await page.goto('/admin/items')
    await page.locator('.hero-actions .el-button--success').click()

    const fixturePath = path.resolve(currentDir, 'fixtures', 'import-case.txt')
    await page.locator('.upload-area input[type="file"]').setInputFiles(fixturePath)
    await expect(page.locator('.preview-card')).toHaveCount(1)

    await page.locator('.import-submit .el-button--primary').click()
    await expect(page.locator('.import-result')).toBeVisible({ timeout: 60_000 })
    await expect(page.locator('.import-result .el-table__body-wrapper tbody tr').first()).toContainText(
      'import-case.txt'
    )

    const searchResponse = await page.request.get(`${apiBase}/item/page`, {
      params: {
        pageNum: 1,
        pageSize: 1,
        keyword: 'Codex E2E import case'
      }
    })
    expect(searchResponse.ok()).toBeTruthy()
    const searchPayload = await searchResponse.json()
    const importedId = searchPayload.data.records[0]?.id
    expect(importedId).toBeTruthy()
    await page.request.delete(`${apiBase}/item/${importedId}`)
  })
})

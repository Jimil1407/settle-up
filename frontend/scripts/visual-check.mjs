// Visual smoke check: renders the landing page in both themes and at mobile width,
// and fails loudly on any console error, missing section, or dead interaction.
import { chromium } from 'playwright'

const BASE = 'http://localhost:4173'
const OUT = 'shots'

const shots = [
  { name: 'landing-dark-top', theme: 'dark', width: 1440, height: 1000, scrollTo: 0 },
  { name: 'landing-dark-demo', theme: 'dark', width: 1440, height: 1000, anchor: '#demo' },
  { name: 'landing-dark-pricing', theme: 'dark', width: 1440, height: 1000, anchor: '#pricing' },
  { name: 'landing-light-top', theme: 'light', width: 1440, height: 1000, scrollTo: 0 },
  { name: 'landing-light-features', theme: 'light', width: 1440, height: 1000, anchor: '#features' },
  { name: 'landing-mobile', theme: 'dark', width: 390, height: 844, scrollTo: 0 },
  { name: 'app-login', theme: 'dark', width: 1440, height: 1000, path: '/app' },
]

const browser = await chromium.launch({ channel: 'chrome' })
const errors = []

for (const shot of shots) {
  const context = await browser.newContext({
    viewport: { width: shot.width, height: shot.height },
    deviceScaleFactor: 2,
  })
  const page = await context.newPage()

  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(`[${shot.name}] ${msg.text()}`)
  })
  page.on('pageerror', (err) => errors.push(`[${shot.name}] ${err.message}`))

  await page.addInitScript((theme) => {
    localStorage.setItem('settleup.theme', theme)
  }, shot.theme)

  await page.goto(BASE + (shot.path ?? '/'), { waitUntil: 'networkidle' })

  if (shot.anchor) {
    await page.locator(shot.anchor).scrollIntoViewIfNeeded()
  } else if (typeof shot.scrollTo === 'number') {
    await page.evaluate((y) => window.scrollTo(0, y), shot.scrollTo)
  }

  // Let scroll-triggered reveals settle before capturing.
  await page.waitForTimeout(1200)
  await page.screenshot({ path: `${OUT}/${shot.name}.png` })
  await context.close()
  console.log(`captured ${shot.name}`)
}

// Structural assertions on the landing page.
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const page = await context.newPage()
page.on('pageerror', (err) => errors.push(`[interaction] ${err.message}`))
await page.goto(BASE + '/', { waitUntil: 'networkidle' })

const required = ['#features', '#demo', '#pricing', '#faq', 'header nav', 'footer']
for (const selector of required) {
  if ((await page.locator(selector).count()) === 0) {
    errors.push(`missing required element: ${selector}`)
  }
}

console.log('h1:', JSON.stringify(await page.locator('h1').first().innerText()))

// The interactive demo must actually compute, not merely render.
await page.locator('#demo').scrollIntoViewIfNeeded()
await page.waitForTimeout(400)
const before = await page.locator('#demo').innerText()

await page.fill('#demo-label', 'Scooter rental')
await page.fill('#demo-amount', '4000')
await page.click('#demo button[type="submit"]')
await page.waitForTimeout(700)

const after = await page.locator('#demo').innerText()
if (before === after) errors.push('interactive demo did not recompute after adding an expense')
if (!after.includes('sum = ₹0.00')) errors.push('demo ledger check did not report a zero sum')
await page.screenshot({ path: `${OUT}/demo-after-input.png` })

// FAQ accordion should open.
await page.locator('#faq').scrollIntoViewIfNeeded()
await page.locator('#faq button').nth(2).click()
await page.waitForTimeout(500)
await page.screenshot({ path: `${OUT}/faq-open.png` })

await context.close()
await browser.close()

if (errors.length) {
  console.error('\nFAILURES:')
  for (const e of errors) console.error(' - ' + e)
  process.exit(1)
}
console.log('\nAll visual checks passed.')

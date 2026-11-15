// End-to-end scenario for ReportHook on a real Paper server.
//
// Two bot players (mineflayer) go through the whole report flow while the server console
// and a fake Discord API are checked along the way. Run by run.sh; exits non-zero on failure.
import fs from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const SERVER_LOG = process.env.SERVER_LOG
const CONSOLE_IN = process.env.CONSOLE_IN
const DISCORD_LOG = process.env.DISCORD_LOG
const BOT_VERSION = process.env.BOT_VERSION ?? '26.1'
const WEBHOOK_TOKEN = process.env.WEBHOOK_TOKEN ?? 'test_token_abcdefghijklmnopqrstuvwxyz'
const FIRST_MESSAGE_ID = '100000000000000001'

const results = []
function check (name, ok, detail = '') {
  results.push({ name, ok: Boolean(ok) })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  (${detail})` : ''}`)
}

const stripAnsi = (text) => text.replace(/\x1b\[[0-9;]*[A-Za-z]/g, '')
const logSize = () => fs.statSync(SERVER_LOG).size
const readLog = (from = 0) => stripAnsi(fs.readFileSync(SERVER_LOG).subarray(from).toString('utf8'))
const readDiscord = () => fs.readFileSync(DISCORD_LOG, 'utf8').split('\n').filter(Boolean).map((line) => JSON.parse(line))

async function waitFor (probe, timeout = 15000) {
  const end = Date.now() + timeout
  while (Date.now() < end) {
    const value = probe()
    if (value) return value
    await sleep(200)
  }
  return null
}

/** Types a command into the server console and waits until its output matches. */
async function consoleCommand (command, expected, timeout = 15000) {
  const from = logSize()
  fs.appendFileSync(CONSOLE_IN, command + '\n')
  return waitFor(() => {
    const output = readLog(from)
    return expected.test(output) ? output : null
  }, timeout)
}

function joinBot (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username, auth: 'offline', version: BOT_VERSION })
    bot.received = []
    bot.on('messagestr', (message) => {
      bot.received.push(message)
      console.log(`      [${username} sees] ${message.replace(/\n/g, ' | ')}`)
    })
    const timer = setTimeout(() => reject(new Error(`${username} did not spawn in time`)), 60000)
    bot.once('spawn', () => {
      clearTimeout(timer)
      // Anything that drops the bot later is worth seeing in the CI log
      bot.on('kicked', (reason) => console.log(`      [${username} kicked] ${JSON.stringify(reason)}`))
      bot.on('error', (error) => console.log(`      [${username} error] ${error?.stack ?? error}`))
      bot.on('end', (reason) => console.log(`      [${username} disconnected] ${reason}`))
      resolve(bot)
    })
    bot.once('kicked', (reason) => reject(new Error(`${username} was kicked: ${JSON.stringify(reason)}`)))
    bot.once('error', reject)
  })
}

/** Waits until the bot received a chat message matching the pattern. */
const received = (bot, pattern, timeout = 15000) => waitFor(() => bot.received.find((message) => pattern.test(message)), timeout)

/** Resolves with the next inventory window the bot opens, or null after the timeout. */
function nextWindow (bot, timeout = 15000) {
  return new Promise((resolve) => {
    const onOpen = (window) => { clearTimeout(timer); resolve(window) }
    const timer = setTimeout(() => { bot.removeListener('windowOpen', onOpen); resolve(null) }, timeout)
    bot.once('windowOpen', onOpen)
  })
}

async function main () {
  check('plugin enabled and logs the webhook masked',
    /Discord sync enabled \(https:\/\/discord\.com\/api\/webhooks\/123456789012345678\/\*{8}\)/.test(readLog()))

  const alex = await joinBot('Alex')
  const steve = await joinBot('Steve')
  await sleep(2000)

  // Alex is staff from here on (ops get reporthook.staff and reporthook.bypass.cooldown)
  check('console: op Alex', await consoleCommand('op Alex', /Made Alex a server operator/))
  await sleep(1500)

  // Chat that should end up in the report as evidence
  steve.chat('ez')
  await sleep(400)
  steve.chat('cant touch me')
  await sleep(1500)

  // --- filing reports
  alex.chat('/report Steve Kill aura')
  check('player: /report <player> <reason> files report #1', await received(alex, /Report #1 against Steve was sent/))
  check('staff: in-game alert with clickable buttons', await received(alex, /New report #1[\s\S]*Kill aura[\s\S]*\[Teleport\] \[Claim\] \[Info\]/))

  alex.chat('/report Steve once more')
  check('player: second open report on the same player is refused', await received(alex, /already reported Steve/))
  alex.chat('/report Alex test')
  check('player: reporting yourself is refused', await received(alex, /can't report yourself/))
  alex.chat('/report Nobody test')
  check('player: reporting an offline name is refused', await received(alex, /Nobody is not online/))

  // --- reason menu
  const reasonMenuOpened = nextWindow(steve)
  steve.chat('/report Alex')
  const reasonMenu = await reasonMenuOpened
  const reasonItems = reasonMenu ? reasonMenu.slots.slice(0, reasonMenu.inventoryStart).filter((item) => item && !item.name.endsWith('_pane')) : []
  const swordSlot = reasonItems.find((item) => item.name === 'diamond_sword')?.slot ?? -1
  check('menu: /report <player> opens the reason menu', swordSlot >= 0,
    reasonMenu ? reasonItems.map((item) => item.name).join(', ') : 'no window opened')
  if (swordSlot >= 0) {
    await steve.clickWindow(swordSlot, 0, 0)
    check('menu: picking a reason files report #2', await received(steve, /Report #2 against Alex was sent/))
    check('staff: alert for report #2', await received(alex, /New report #2/))
  }
  steve.chat('/report Alex spam')
  check('player: cooldown between reports', await received(steve, /Please wait \S+ before reporting again/))

  // --- staff menu
  const staffMenuOpened = nextWindow(alex)
  alex.chat('/reports')
  const staffMenu = await staffMenuOpened
  const heads = staffMenu ? staffMenu.slots.slice(0, 45).filter((item) => item?.name === 'player_head').length : 0
  check('staff menu: one player head per report', heads === 2, `${heads} heads`)
  if (staffMenu) {
    const refreshed = nextWindow(alex)
    await alex.clickWindow(1, 1, 0) // right click the second entry, report #1 (newest first)
    check('staff menu: right click claims the report', await received(alex, /Report #1 \(Steve\) is now Claimed/))
    check('staff menu: refreshes after the change', await refreshed)
    if (alex.currentWindow) alex.closeWindow(alex.currentWindow)
  }

  // --- console staff commands
  check('console: resolve with a note', await consoleCommand('reports resolve 1 Banned for testing', /Report #1 \(Steve\) is now Resolved/))
  check('player: reporter is told the report was reviewed', await received(alex, /Staff reviewed your report against Steve/))
  check('staff: other staff see who changed the status', await received(alex, /Console set report #1 \(Steve\) to Resolved/))

  const info = await consoleCommand('reports info 1', /Chat messages attached: 2/)
  check('console: info includes the chat evidence', info && /ez/.test(info) && /cant touch me/.test(info))
  const list = await consoleCommand('reports list all', /#1 Steve/)
  check('console: list shows every report', list && /#2 Alex/.test(list))
  check('console: history of a player', await consoleCommand('reports history Steve', /Reports against Steve/))
  check('console: /report is for players only', await consoleCommand('report Steve test', /Only players can do that/))
  check('console: reload', await consoleCommand('reports reload', /Configuration and messages reloaded/))

  alex.chat('/reports tp 1')
  check('staff: teleport to the reported player', await received(alex, /Teleported to Steve/))

  // --- Discord
  const requests = (await waitFor(() => {
    const all = readDiscord()
    return all.filter((request) => !request.rate_limited).length >= 4 ? all : null
  }, 20000)) ?? readDiscord()
  const answered = requests.filter((request) => !request.rate_limited)
  const posts = answered.filter((request) => request.method === 'POST')
  const patches = answered.filter((request) => request.method === 'PATCH')
  check('discord: waits and retries after HTTP 429', requests[0]?.rate_limited === true && posts.length === 2,
    `${requests.length} requests`)
  check('discord: one message per report, created with wait=true',
    posts.length === 2 && posts.every((request) => request.path.endsWith('?wait=true')))
  const created = posts[0]?.body
  check('discord: only the configured staff role is pinged',
    created?.content === '<@&424242424242>' && created?.allowed_mentions?.parse?.length === 0 &&
    JSON.stringify(created?.allowed_mentions?.roles) === '["424242424242"]')
  const embed = created?.embeds?.[0]
  check('discord: embed has the reason, location and chat evidence',
    embed?.title?.startsWith('Report #1') && embed?.description?.includes('Kill aura') &&
    embed?.fields?.some((field) => field.name === 'Location') &&
    embed?.fields?.some((field) => field.name === 'Recent chat' && field.value.includes('cant touch me')))
  check('discord: claim and resolve edit the original message',
    patches.length === 2 && patches.every((request) => request.path.endsWith(`/messages/${FIRST_MESSAGE_ID}`)),
    `${patches.length} edits`)
  const finalEmbed = patches.at(-1)?.body?.embeds?.[0]
  check('discord: final embed is green and shows the note',
    finalEmbed?.color === 0x2ECC71 &&
    finalEmbed?.fields?.some((field) => field.name === 'Status' && field.value.includes('Resolved by Console')) &&
    finalEmbed?.fields?.some((field) => field.name === 'Staff note' && field.value.includes('Banned for testing')))
  check('discord: edits never ping anyone', patches.every((request) => !request.body?.content))
  check('security: webhook token never shows up in the server log', !readLog().includes(WEBHOOK_TOKEN))

  alex.quit()
  steve.quit()
  await sleep(1000)
}

try {
  await main()
} catch (error) {
  check('scenario finished without errors', false, error?.stack ?? String(error))
}
const failed = results.filter((result) => !result.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)

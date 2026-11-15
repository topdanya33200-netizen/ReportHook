# ReportHook

[![Build](https://github.com/topdanya33200-netizen/ReportHook/actions/workflows/build.yml/badge.svg)](https://github.com/topdanya33200-netizen/ReportHook/actions/workflows/build.yml)
![Paper 26.x](https://img.shields.io/badge/Paper-26.x-blue)
![Folia](https://img.shields.io/badge/Folia-supported-brightgreen)
![Java 25](https://img.shields.io/badge/Java-25-orange)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Player reports for Paper and Folia servers.** Players get a clean `/report` menu, staff get a GUI to review
reports, and every report lands in Discord as an embed that updates itself when a moderator claims or closes it.

[Русская версия](README.ru.md)

## Features

- **`/report <player> [reason]`** opens a menu of configurable reasons when no reason is typed.
  Tab completion hides vanished staff, and reporting them behaves exactly like reporting an offline player.
- **Discord sync through a webhook.** Each report becomes an embed with the player's head, the reason, coordinates
  and their last chat messages. The same message is edited when the report is claimed, resolved or dismissed,
  and its colour follows the status. Forum channels are supported: every report gets its own thread.
- **Safe pings.** A staff role can be mentioned on new reports, while `allowed_mentions` and markdown escaping make
  sure nothing a player types can ping `@everyone`, format the embed or hide a link.
- **Staff GUI (`/reports`).** Player heads, filters (active, open, claimed, closed, all), pages, and one click
  to teleport, claim, resolve or dismiss.
- **Clickable staff alerts** in chat with a sound, a reminder about open reports on join, and a message to the
  reporter once their report has been reviewed.
- **Chat evidence.** The reported player's recent chat is attached automatically, which helps with chat reports.
- **Spam protection.** Cooldown, one active report per reporter and target, a reason length limit and an exempt
  permission.
- **Never blocks the server.** SQLite runs on its own thread, Discord requests are queued and retried on rate
  limits, and everything that touches players goes through Paper's region schedulers, so it runs on Folia.
- **Modern Paper API:** Brigadier commands, Adventure and MiniMessage, plus PlaceholderAPI placeholders.
- **English and Russian** messages out of the box. Every message is editable.

## Installation

1. Download `ReportHook-<version>.jar` from [Releases](https://github.com/topdanya33200-netizen/ReportHook/releases)
   and put it into `plugins/`.
2. Start the server once, then edit `plugins/ReportHook/config.yml`.
3. Run `/reports reload`.

Requires Paper or Folia 26.1+ and Java 25. PlaceholderAPI is optional.

### Connecting Discord

1. In Discord, open the channel settings, then *Integrations → Webhooks → New Webhook*, and copy the URL.
2. Paste it into `discord.webhook-url` and set `discord.enabled: true`.
3. Optional: put a role ID into `discord.mention-role-id` to ping your staff on new reports.
4. Optional: if the webhook belongs to a forum channel, set `discord.forum-threads: true`.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/report <player> [reason]` | `reporthook.report` (everyone) | Report a player; without a reason the menu opens |
| `/reports` | `reporthook.staff` | Open the staff menu (prints a list in the console) |
| `/reports list [active\|open\|claimed\|closed\|all] [page]` | `reporthook.staff` | List reports in chat |
| `/reports info <id>` | `reporthook.staff` | Details, including the attached chat |
| `/reports claim <id>` · `unclaim <id>` | `reporthook.staff` | Take a report or release it |
| `/reports resolve <id> [note]` · `dismiss <id> [note]` | `reporthook.staff` | Close a report |
| `/reports reopen <id>` | `reporthook.staff` | Open a closed report again |
| `/reports tp <id>` | `reporthook.staff` | Teleport to the player, or to where they were if they left |
| `/reports history <player>` | `reporthook.staff` | Last reports against a player |
| `/reports reload` | `reporthook.admin` | Reload config and messages |

| Permission | Default | Meaning |
|---|---|---|
| `reporthook.notify` | op | Receive alerts about new reports |
| `reporthook.bypass.cooldown` | op | Report without waiting for the cooldown |
| `reporthook.exempt` | false | Cannot be reported |

## Placeholders

With PlaceholderAPI installed: `%reporthook_open%`, `%reporthook_claimed%`, `%reporthook_active%`,
`%reporthook_resolved%`, `%reporthook_dismissed%`, `%reporthook_total%`. They are served from a cache, so a
scoreboard refreshing every tick never touches the database.

## What a Discord message looks like

<details>
<summary>Webhook payload sent for a new report</summary>

```json
{
  "username": "ReportHook",
  "content": "<@&987654321098765432>",
  "allowed_mentions": { "parse": [], "roles": ["987654321098765432"] },
  "embeds": [{
    "title": "Report #12 · Steve",
    "color": 15158332,
    "description": "**Reason**\n> Kill aura, hits through walls",
    "fields": [
      { "name": "Reported player", "value": "`Steve`\n`5e3f1c2a-8b7d-4c6e-9f01-2a3b4c5d6e7f`", "inline": true },
      { "name": "Reporter", "value": "`Alex`", "inline": true },
      { "name": "Server", "value": "Survival", "inline": true },
      { "name": "Location", "value": "`world` 120, 64, -31", "inline": true },
      { "name": "Status", "value": "🔴 Open", "inline": true },
      { "name": "Recent chat", "value": "```\n[19:29:20] ez\n[19:29:48] cant touch me\n```", "inline": false }
    ],
    "thumbnail": { "url": "https://mc-heads.net/avatar/5e3f1c2a-8b7d-4c6e-9f01-2a3b4c5d6e7f/64" },
    "footer": { "text": "ReportHook · Survival" },
    "timestamp": "2026-11-15T19:30:00Z"
  }]
}
```

When a moderator resolves the report, the same message is edited: the colour turns green and the status
reads `🟢 Resolved by <moderator>`.
</details>

## Building from source

```bash
./gradlew build       # plugin jar in build/libs/, runs the unit tests
./gradlew runServer   # starts a local Paper 26.3 test server with the plugin installed
```

JDK 25 is required; Gradle downloads it automatically if it is missing.

## How it is built

```
src/main/java/.../reporthook
├── command/    Brigadier command trees for /report and /reports
├── config/     typed config snapshot and MiniMessage language files
├── discord/    webhook client (queue, retries, 429 handling), embed builder, URL validation, escaping
├── gui/        tiny menu framework, reason menu, paginated staff menu
├── listener/   chat history and join/quit handling
├── model/      immutable records: Report, ReportStatus, TransitionResult...
├── service/    ReportService (all report logic), chat history, cooldowns
├── storage/    repository interface and its SQLite implementation
└── util/       Folia-safe scheduling, text cleaning, time formatting
```

- **Threading:** one database thread, one Discord thread, and the server threads only for Bukkit calls.
  Results go back to each player through their own entity scheduler, which is what makes Folia work.
- **Consistency:** status changes are compare-and-set updates (`WHERE status = ?`), so two moderators clicking
  at the same time can't overwrite each other.
- **Security:** player input is inserted with unparsed MiniMessage placeholders, `§` codes are stripped, the
  webhook token never shows up in logs, and every Discord payload restricts mentions.
- **Tests:** 50 JUnit tests cover the SQLite repository, Discord payloads, escaping, URL parsing, rate-limit
  handling, cooldowns and chat history. They run on every push in GitHub Actions.

## License

[MIT](LICENSE) © topdanya33200-netizen

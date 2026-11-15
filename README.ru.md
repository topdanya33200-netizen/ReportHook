# ReportHook

[![Build](https://github.com/topdanya33200-netizen/ReportHook/actions/workflows/build.yml/badge.svg)](https://github.com/topdanya33200-netizen/ReportHook/actions/workflows/build.yml)
[![Server test](https://github.com/topdanya33200-netizen/ReportHook/actions/workflows/server-test.yml/badge.svg)](https://github.com/topdanya33200-netizen/ReportHook/actions/workflows/server-test.yml)
![Paper 26.x](https://img.shields.io/badge/Paper-26.x-blue)
![Folia](https://img.shields.io/badge/Folia-supported-brightgreen)
![Java 25](https://img.shields.io/badge/Java-25-orange)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Система жалоб для серверов на Paper и Folia.** Игроки жалуются через удобное меню `/report`, модераторы
разбирают жалобы в GUI, а каждая жалоба приходит в Discord и сама обновляется, когда её берут в работу или закрывают.

[English version](README.md)

## Возможности

- **`/report <игрок> [причина]`**: если причину не написать, откроется меню с причинами из конфига.
  Автодополнение не показывает модераторов в ванише, а жалоба на них ведёт себя так же, как на игрока не в сети.
- **Синхронизация с Discord через вебхук.** Жалоба приходит embed-сообщением: голова игрока, причина, координаты
  и его последние сообщения в чате. Когда жалобу берут, решают или отклоняют, редактируется то же сообщение,
  а его цвет меняется по статусу. Поддерживаются форум-каналы: на каждую жалобу создаётся отдельная ветка.
- **Безопасные пинги.** На новые жалобы можно пинговать роль модераторов, а `allowed_mentions` и экранирование
  markdown не дают игроку пингануть `@everyone`, сломать оформление или спрятать ссылку.
- **GUI для модераторов (`/reports`)**: головы игроков, фильтры (активные, открытые, в работе, закрытые, все),
  страницы, телепорт, взятие, решение и отклонение в один клик.
- **Кликабельные уведомления** модераторам со звуком, напоминание об открытых жалобах при входе и сообщение
  автору, когда его жалобу рассмотрели.
- **Доказательства из чата.** Последние сообщения нарушителя прикрепляются к жалобе автоматически.
- **Защита от спама:** кулдаун, одна активная жалоба от игрока на одного нарушителя, лимит длины причины и право,
  при котором на игрока нельзя пожаловаться.
- **Не нагружает сервер.** SQLite работает в своём потоке, запросы в Discord идут очередью с повтором при
  rate limit, а всё, что касается игроков, выполняется через региональные планировщики Paper, поэтому плагин
  работает и на Folia.
- **Современный Paper API:** команды на Brigadier, Adventure и MiniMessage, плейсхолдеры PlaceholderAPI.
- **Русский и английский** языки из коробки, любое сообщение можно изменить.

## Установка

1. Скачайте `ReportHook-<версия>.jar` из [Releases](https://github.com/topdanya33200-netizen/ReportHook/releases)
   и положите в `plugins/`.
2. Запустите сервер один раз, поставьте `language: ru` в `plugins/ReportHook/config.yml`.
3. Выполните `/reports reload`.

Нужен Paper или Folia 26.1+ и Java 25. PlaceholderAPI необязателен.

### Подключение Discord

1. В Discord откройте настройки канала → *Интеграция → Вебхуки → Новый вебхук* и скопируйте URL.
2. Вставьте его в `discord.webhook-url` и поставьте `discord.enabled: true`.
3. По желанию: укажите ID роли в `discord.mention-role-id`, чтобы пинговать модераторов.
4. По желанию: если вебхук создан в форум-канале, поставьте `discord.forum-threads: true`.

## Команды

| Команда | Право | Что делает |
|---|---|---|
| `/report <игрок> [причина]` | `reporthook.report` (у всех) | Пожаловаться; без причины откроется меню |
| `/reports` | `reporthook.staff` | Меню модератора (в консоли выводит список) |
| `/reports list [active\|open\|claimed\|closed\|all] [страница]` | `reporthook.staff` | Список жалоб в чате |
| `/reports info <id>` | `reporthook.staff` | Подробности вместе с сообщениями из чата |
| `/reports claim <id>` · `unclaim <id>` | `reporthook.staff` | Взять жалобу или освободить её |
| `/reports resolve <id> [заметка]` · `dismiss <id> [заметка]` | `reporthook.staff` | Закрыть жалобу |
| `/reports reopen <id>` | `reporthook.staff` | Снова открыть закрытую жалобу |
| `/reports tp <id>` | `reporthook.staff` | Телепорт к игроку или туда, где он был |
| `/reports history <игрок>` | `reporthook.staff` | Последние жалобы на игрока |
| `/reports reload` | `reporthook.admin` | Перезагрузить конфиг и сообщения |

| Право | По умолчанию | Значение |
|---|---|---|
| `reporthook.notify` | op | Получать уведомления о новых жалобах |
| `reporthook.bypass.cooldown` | op | Жаловаться без кулдауна |
| `reporthook.exempt` | false | На игрока нельзя пожаловаться |

## Плейсхолдеры

С PlaceholderAPI: `%reporthook_open%`, `%reporthook_claimed%`, `%reporthook_active%`, `%reporthook_resolved%`,
`%reporthook_dismissed%`, `%reporthook_total%`. Значения берутся из кэша, поэтому скорборд, который обновляется
каждый тик, не трогает базу данных.

## Сборка из исходников

```bash
./gradlew build       # jar в build/libs/, заодно прогоняются тесты
./gradlew runServer   # локальный тестовый сервер Paper 26.3 с плагином
```

Нужен JDK 25; если его нет, Gradle скачает его сам.

## Как устроен код

- **Потоки:** один поток для базы, один для Discord, а серверные потоки только для вызовов Bukkit.
  Результаты возвращаются игрокам через их собственный планировщик, поэтому плагин работает на Folia.
- **Согласованность:** смена статуса сделана как compare-and-set (`WHERE status = ?`), два модератора,
  нажавшие одновременно, не перезапишут друг друга.
- **Безопасность:** ввод игроков подставляется как unparsed-плейсхолдеры MiniMessage, `§`-коды вырезаются,
  токен вебхука не попадает в логи, а каждый запрос в Discord ограничивает упоминания.
- **Юнит-тесты:** 50 тестов JUnit покрывают SQLite-хранилище, сообщения для Discord, экранирование, разбор URL,
  обработку rate limit, кулдауны и историю чата.
- **Сквозной тест на настоящих серверах:** при каждом пуше GitHub Actions запускает Paper 26.1 и 26.3, заводит
  двух ботов-игроков и проходит весь сценарий: жалобы с меню и без, защиту от спама, меню модератора, команды
  консоли и телепорт. Заглушка вместо Discord API проверяет каждый запрос вебхука, включая повтор после HTTP 429.
  Подробности в [`.github/server-test`](.github/server-test).

## Лицензия

[MIT](LICENSE) © topdanya33200-netizen

---
title: Introduction
description: What SakuraBans is and where to start.
navigation:
  icon: i-lucide-book-open
---

SakuraBans is a punishment plugin for Minecraft servers running **Paper 26.3** or newer, or the
**Velocity 4.3** proxy or newer. It handles bans, mutes, warnings and kicks for players,
addresses and address ranges, with scopes, templates, alt detection and network sync.

It replaces plugins such as LiteBans and LibertyBans, and can import their data.

## Where to start

- [Installation](/docs/getting-started/installation): requirements, proxy or backends, the first start.
- [Configuration](/docs/configuration/config): every file SakuraBans creates, key by key.
- [Commands](/docs/commands/punishing) and [permissions](/docs/reference/permissions).
- [Networks](/docs/guides/networks): one database for several servers.
- [Web editor](/docs/web-editor): editing network-wide settings, messages and templates in the browser.

## What it does

| Feature | Read more |
|---|---|
| Bans, mutes, warnings and kicks; player, address and range targets | [Punishing](/docs/commands/punishing) |
| Scopes: everywhere, one server, or a category of servers | [Scopes](/docs/guides/scopes) |
| Templates with ladders and shared groups | [Templates and ladders](/docs/guides/templates) |
| Alt detection with address strictness | [Alts](/docs/guides/alts) |
| Staff limits: maximum durations, cooldowns, required templates | [limits.yml](/docs/configuration/limits) |
| Exemption by permission or LuckPerms weight | [config.yml](/docs/configuration/config#exemption) |
| Discord webhooks | [webhooks.yml](/docs/configuration/webhooks) |
| Importers from LiteBans, LibertyBans, AdvancedBan, BanManager and vanilla | [Importing](/docs/guides/importing) |
| Lockdown, allow list, evidence, data export and erasure | [Administration](/docs/commands/administration) |

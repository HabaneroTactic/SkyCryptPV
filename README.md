# SkyCrypt PV

Lunar Client's `/pv`, but as a normal Fabric mod, so you can use it without Lunar. `/pv <player>` opens the actual [SkyCrypt](https://sky.shiiyu.moe) website inside Minecraft, so tabs, item hovers and everything else work the same as in your browser.

I made it because the profile viewers built into other mods kept breaking on me, and I just wanted SkyCrypt in-game.

**Powered by SkyCrypt.** This mod is unofficial and isn't affiliated with or endorsed by SkyCrypt or Hypixel. If something's wrong with the mod, please open an [issue](https://github.com/HabaneroTactic/SkyCryptPV/issues) here instead of bothering the SkyCrypt team.

## Download

Get it on [Modrinth](https://modrinth.com/mod/skycrypt-pv). It's for Minecraft 26.2 and needs Fabric API. Mod Menu is optional.

## How to use

- `/pv` opens your own profile, `/pv <player>` someone else's, and `/pv <player> <profile>` a specific one (e.g. `/pv HabaneroTactic Blueberry`). `/skycrypt` does the same thing.
- Right-click a player on Hypixel and there's an eye of ender button in their profile menu, like NEU's old one.
- Look at a player and press J (you can change the key in Options → Controls → Key Binds).
- Click someone's name in chat, or hold Cmd/Ctrl (also changeable) and click any name.

Settings are in `/pv settings` or Mod Menu.

## Good to know

- The first launch downloads Chromium (around 300 MB, only once).
- `/pv` takes over Skyblocker's `/pv`. If you want Skyblocker's back, change "/pv opens" in the settings and use `/skycrypt` instead.
- On Apple Silicon Macs, stick with the launcher's default Java. Native ARM Java can crash when Chromium loads.
- Backspace doesn't work in the website's own search box. Use the name box in the toolbar.

## Building

Needs JDK 25.

```bash
./gradlew build
```

The jar ends up in `build/libs/`.

## Credits

- [SkyCrypt](https://sky.shiiyu.moe) for the site itself. Support them on [Patreon](https://www.patreon.com/shiiyu).
- [MCEF Modern](https://github.com/DimasKama/mcef-modern) by DimasKama for the embedded browser (bundled, LGPL-2.1).
- Lunar Client's `/pv` and [NotEnoughUpdates](https://github.com/NotEnoughUpdates/NotEnoughUpdates)' profile viewer button for the ideas.

MIT licensed. Other licenses are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

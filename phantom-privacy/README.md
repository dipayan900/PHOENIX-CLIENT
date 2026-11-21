# Phantom Privacy

**Made by Brutal_XD aka DIPAYAN**

A client-side Fabric utility that filters selected mod entries from the **Mod Menu list only**.

## What it does
- Opens its configuration screen with the default plus-key shortcut (Shift + = on a standard US keyboard).
- Accepts comma-separated mod IDs.
- Always filters its own `phantomprivacy` entry from Mod Menu.
- Saves the selected IDs in `.minecraft/config/phantom-privacy.properties`.
- Does not disable, uninstall, unload, or alter any mod.

## Important limitation
This is a local display filter, not a security or anti-detection feature. It does not hide mod files, Fabric Loader metadata, logs, crash reports, server-side information, or other inspection tools. It only changes the Mod Menu list. It requires Fabric Loader, Fabric API, and Mod Menu.

## Build
Run `gradle build` with Java 21. The jar is generated in `build/libs/`.

## Current build target
Minecraft 1.21.11 / Fabric / Mod Menu 17.0.0. Older 1.21.x versions need separately compiled compatibility builds; this jar does not claim compatibility with every 1.21.x release.

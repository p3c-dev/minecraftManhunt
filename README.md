# Minecraft Manhunt Plugin

## Overview

This plugin provides a lightweight and customizable Minecraft Manhunt experience with two gameplay modes:

1. **Classic Manhunt** – Speedrunner vs Hunters.
2. **Hitmen Mode** – Survivalist vs Hitmen, where the goal is survival instead of completing the game.

The plugin is designed to be minimal, fast, and easy to use without unnecessary complexity.

---

## Core Gameplay

### Classic Manhunt

In this mode, one player is assigned as the **speedrunner**, while other players act as **hunters**.

The hunters attempt to track and eliminate the speedrunner using a lodestone compass that tracks the speedrunner’s last known location.

Key mechanics:

* Only one speedrunner can be set at a time.
* Hunters receive a compass used to track the speedrunner.
* The compass updates only when the hunter right-clicks it.
* If the speedrunner changes dimensions, the compass does not update until they return. This is how Dream described the compass worked in his Manhunt Revival videos! _(you can change this behaviour in config.yml)_
* Hunters automatically regain the compass upon death.
* No extra unnecessary features like blindness or slowness for hunters when manhunt starts, just a simple plugin to play with your friends!

---

## Hitmen Mode

Hitmen Mode changes the gameplay from a race into a survival challenge.

Instead of beating the game, the **survivalist** must stay alive for a specified amount of time.

### Objectives

**Survivalist:**

* Survive until the countdown timer reaches zero.

**Hitmen:**

* Kill the survivalist once before the timer ends.

### Features

* The survivalist receives a permanent glowing effect.
* The glowing effect is reapplied if removed.
* A global timer is shown to all players.
* The timer starts only when the survivalist moves or attacks after being ready.
* No start announcements are displayed.
* Win conditions are automatic.

---

## Commands

### Speedrunner Management

#### `/speedrunner add <player>`

Assigns a player as the speedrunner.

#### `/speedrunner remove <player>`

Removes the current speedrunner.

Rules:

* Only one speedrunner can exist.
* A player cannot be both a speedrunner and hunter.

---

### Hunter Management

#### `/hunter add <player>`

Adds a player as a hunter.

#### `/hunter remove <player>`

Removes a player from the hunters.

---

### Compass

#### `/compass`

Displays information about the current tracking target.

This command is only available to hunters.

---

## Hitmen Mode Commands

### `/hitmen-mode ON <time>`

Enables Hitmen Mode and sets the survival timer.

Parameters:

* `<time>` is provided in seconds.

Behavior:

* Displays the countdown timer to all players.
* The timer remains paused until the survivalist becomes active.

---

### `/hitmen-mode OFF`

Disables Hitmen Mode and clears any existing timer.

---

### `/hitmen-mode RESET`

Resets all Hitmen Mode settings and turns the mode off.

---

### `/survivalist-ready`

Marks the survivalist as ready.

The timer will begin when:

* The survivalist moves.
* The survivalist attacks a hitman.

Restrictions:

* Only the survivalist can use this command.

---

## Miscellaneous

### `/Minecraft-Manhunt reload`

Reloads the plugin and all settings.

---

### `/Minecraft-Manhunt version`

Displays the current server and plugin version.

---

## config.yml

Configuration for the Manhunt Plugin

Currently, there is only 1 option here:

`hunter-compass-reset-when-no-players-to-track: true` (default)
* If set to true, then if player is in a different dimension and the compass shows **"No players to track!"**, the compass will reset (lose its locked location) and it will just start spinning around.

This makes it more challenging for the hunters too if they die and the speedrunner is in a different dimension, or if they right-click the compass when speedrunner is in a different dimension

`hunter-compass-reset-when-no-players-to-track: false`
* If set to false, then it disables this feature entirely, and the compass always stays locked to the last position.

---

## Requirements

* Spigot, Paper or Purpur Minecraft server
* Compatible with modern Minecraft versions (native: 1.21.11)

---

## Credits

Developed by _p3c (@p3c-dev).

Inspired by Dream's Minecraft Manhunt Series.

---

## Usage

Feel free to do whatever you want with this code - use it in your own projects, tweak it or share it with friends!

The only things I ask:

* Keep the code open source so others modify it too

* Just drop a little credit somewhere if you publish it (doesn't have to be fancy!)
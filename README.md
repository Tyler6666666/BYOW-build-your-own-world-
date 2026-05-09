# BYOW - Build Your Own World

BYOW is a seed-based 2D exploration game. Each run generates a new tile world with rooms,
hallways, collectibles, enemies, save/load support, and a mouse-aware HUD.

The game is built in Java with a lightweight tile renderer. Worlds are deterministic from
their seed, so the same seed recreates the same layout while different seeds produce different
maps to explore.

## Features

- Procedurally generated rooms and hallways
- Keyboard movement with wall collision
- Coins, exits, scoring, health, and enemy behavior
- Save and load from the main menu
- Mouse hover HUD that names visible tiles
- Optional line-of-sight visual mode and difficulty switching
- One-click Windows launcher

## Run On Windows

Double-click `BYOW.bat`.

The launcher compiles `BYOW/src` into `BYOW/out/game` and starts the game. The required Java
library is included in `library-sp26`, so no separate dependency download is needed.

If you want to use a different library copy, set `BYOW_LIBRARY` to that folder before running
the launcher.

## Controls

- `N`: start a new world
- `L`: load the most recent save
- `Q`: quit from the main menu
- `WASD`: move in the world
- `:Q`: save and quit while playing
- `T`: change difficulty while playing
- `V`: toggle line-of-sight visuals
- Double-click a visible floor tile to follow a path

## Project Layout

- `BYOW/src`: Java source code
- `BYOW/screenshots`: sample generated worlds
- `library-sp26`: bundled Java library dependencies
- `BYOW.bat`: Windows launcher

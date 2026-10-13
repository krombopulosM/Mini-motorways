# Little Roads

A minimalist traffic-management game for Android phones, built to play like *Mini Motorways*:
draw roads so cars from coloured houses can reach destinations of the same colour before the
city grinds to a halt.

**Download:** [`release/LittleRoads.apk`](release/LittleRoads.apk). Allow installs from unknown
sources and open it on your phone. It needs Android 5.0 or newer and plays in landscape.

## Gameplay

- **Houses** appear near **destinations** of their colour. Each house has two cars.
- Destinations show **pins** when they need visitors. A car from a connected house of the same
  colour drives over, parks in one of the four bays, and drives home. Each trip scores a point.
- Drag a finger to **draw roads** in 8 directions. Roads cost tiles from a limited budget.
  The **eraser** refunds tiles, and **undo** reverts your last edit.
- If a destination holds 6 or more pins, a red ring starts to fill. When it closes, it's
  **Gridlock**: game over.
- Every **week** you get +20 road tiles and pick one of two upgrades: bridges, tunnels,
  motorways, roundabouts, traffic lights or extra roads.
  - **Bridges / tunnels**: drag a road straight across water or through a mountain.
  - **Motorway**: an elevated, fast link between any two points.
  - **Roundabout**: a junction several cars can use at once.
  - **Traffic lights**: alternate the flow at a busy junction.
- Junctions slow cars down and let one stream through at a time, so traffic builds up the way
  it does in the original. Cars re-route live when you change the road network.
- The city grows outward over time, and a new destination (often in a new colour) appears every
  few days.

**Cities:** Los Angeles, Tokyo (left-hand traffic), Munich, Istanbul, Zurich and Rio de Janeiro,
each with its own palette, water and mountains.

**Modes:** Normal, Expert (faster growth, more demand) and Endless (no game over).

**Settings:** sound effects, haptic feedback, grid, colour-blind symbols and night mode.
Best scores are saved for each city and mode.

**Controls:** one finger to build, two fingers to pinch-zoom and pan, plus pause, play and
fast-forward. You can keep building while paused.

All graphics are drawn in code and all sounds are synthesised at runtime, so the app has no
asset files and the APK is under 100 KB.

## Building

The project is plain Java against the Android framework, with no libraries. `build.sh` builds a
signed APK with the command-line SDK tools (aapt, javac, dx/d8, zipalign, apksigner):

```sh
# Debian/Ubuntu
sudo apt-get install openjdk-17-jdk android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner
./build.sh            # -> build/LittleRoads.apk
```

To use a regular Android SDK instead, point the script at it, for example:
`ANDROID_JAR=$ANDROID_HOME/platforms/android-34/android.jar BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0 ./build.sh`.

The APK is signed with the debug key in `keystore/` (password `android`). Use your own key for
anything you publish.

## Code layout

| File | Purpose |
|------|---------|
| `World.java` | Simulation: grid, roads, bridges, motorways, spawning, demand, path finding, traffic and junction rules, undo |
| `Car.java` | Smooth paths (lines plus Bezier corners), lane offset, pose |
| `MapDef.java` | City definitions and procedural terrain |
| `Renderer.java` | World drawing (terrain, roads, buildings, cars, pins) |
| `Hud.java` | HUD, overlays, menus, settings and help screens, icons |
| `GameView.java` | Game loop, input, camera, screen flow, preferences |
| `Sound.java` | Synthesised sound effects |

*Little Roads* is an independent fan project. It is not affiliated with Dinosaur Polo Club and
uses none of their code, art or audio.

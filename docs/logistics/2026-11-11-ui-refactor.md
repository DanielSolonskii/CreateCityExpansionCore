# Logistics workspace UI

The logistics workspace uses native Minecraft GUI rendering with a graphite palette,
mint interaction accents and brass attention states. The layout shares geometry
between rendering and hit testing and keeps native-size text at scaled resolutions.

## Entry and help

- Press **K** in the world to open logistics.
- The first successful load opens a six-page quick start guide. An unfinished guide
  resumes at its saved page; Skip or Escape dismisses automatic presentation.
- **Help** or **F1** opens the guide again. Tutorial progress is local, versioned and
  scoped to the player and server in `config/ccec-logistics.properties`.
- The guide offers reduced motion and keyboard page navigation. This preference
  applies to buttons, route diagrams, loading indicators and transitions.

## Workspace

The overview provides Buy, Send, Track and Network tasks. A compact icon rail replaces
the full sidebar in narrow windows. Every section remains reachable through scrolling,
keyboard focus and Ctrl+Up/Down. Wide windows show the selected object's details beside
the list; narrow windows use a dedicated detail screen.

Search, reverse order, problem filtering, pagination and refresh remain available in
object sections. Ctrl+F focuses search, Ctrl+R refreshes, and Page Up/Down scrolls lists.
Hover hints explain navigation, object states, form fields and available actions.
Forms keep their server-defined choices and explicit quote/confirmation path.

Network topology cards share measured bounds for drawing and clicks. Route connectors
animate direction while delivery totals and status remain authoritative server data.
Warehouse slots show stock counts and item tooltips. No visual control transfers items.

Enter/Space activation is latched across widget rebuilding and screen changes, so
holding a key cannot advance through a newly created confirmation button.

## Validation

Run from the repository root with JDK 21 and the populated Gradle cache:

```powershell
.\gradlew.bat --offline --no-configuration-cache --console=plain compileJava checkCargoCenter checkCargoCenterNative checkDispatchPresentation checkShipmentRoutes jar
```

`checkCargoCenter` includes responsive workspace bounds, first-visit persistence,
keyboard activation latching, equipment setup and the existing authorization,
correlation, route, escrow and replay checks. The geometry matrix includes GUI sizes
from 320×180 to 1280×720. Russian and English help texts are included in mod resources.

Minecraft startup, in-game viewing and runtime visual validation are intentionally
excluded from this task at the user's request. These checks establish compilation,
layout policies and state behavior, not pixel appearance in a running game.

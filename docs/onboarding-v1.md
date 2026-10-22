# First steps v1 — 2026-10-18

## Behaviour
Seven optional context cards cover the map, community/profile, City Pylon, city role, projects and territory. Both new and existing UUIDs without v1 progress receive the same first pass; no forced city creation. Seen/completed cards do not repeat after reconnect, death or server restart. Skip is persistent. Later defers locally for 45 seconds without consuming a hint.

First automatic hint waits 12 seconds after entering a world. One card at a time; 30-second spacing after acknowledgement. Cards pause around observed damage, low health, fire or low air and while paused/F1. Key labels resolve actual registered KeyMappings; unbound keys are explained. Unknown future contexts are not invented. Role/city come from server CityManager. The profile shortcut actually opens CommunityScreen and is described accordingly.

F8 (rebindable) and /ccec guide open a compact optional reference with all seven explanations. Existing map Help documents the command. The old session-only map intro banner is disabled in favour of persistent onboarding. Existing map Help and construction guides remain. Context cards use the atlas graphite/brass palette, icons and a short entrance slide, with shared render/hit geometry.

## State and trust
OnboardingProgress stores bounded seen/completed bitmasks and skipped. ccec_onboarding SavedData in Overworld keys records by server player UUID; additive save only. State/mutations are owner-scoped (request has no target UUID), fixed enum actions, bounded strings and eight requests/second. Client acknowledgements describe UI visits only and grant no reward or gameplay permission. No external Backend/Telegram storage.

## Client defaults
OneTimeClientDefaults runs a one-time local migration: backup existing options.txt to config/ccec-options-before-defaults-v1.txt, set menuBackgroundBlurriness=0 and guiScale=2 through Minecraft Options API, resize display, save, then create config/ccec-client-defaults-v1.done. Existing installations receive it too. Later manual changes are respected. Failed migration is logged without private content and retries next client launch. Launcher signed-file allowlist is untouched; options.txt is not distributed as a managed replacement. Vanilla may constrain the effective GUI scale on small windows.

## Files
New onboarding/{OnboardingProgress,OnboardingSavedData,OnboardingServer}; request/state networking payloads; client/{OnboardingClient,OnboardingKeys,OneTimeClientDefaults}; screen/{OnboardingLayout,OnboardingHelpScreen}. Existing registration adds packet handlers; ProjectPanel reports a visible project workspace; PolishedWorldMapScreen removes old transient intro; RU/EN strings and map-help command reference added.

## Validation
compileJava, checkOnboarding and jar --offline --no-configuration-cache passed. 235 headless checks cover per-UUID progress save/reload/skip, bounded hint IDs, layouts at several viewport sizes, options backup, one-time migration and preserving later manual settings, plus packet round trips. These are not an actual Minecraft GUI/FPS or Options API runtime test. Full check/build suite not repeated.

Runtime checklist: existing/new account; initial delay; actual key rebinding/unbound; map/community/pylon/project contexts; new city membership/role; Later/Skip; F8 and /ccec guide; restart and death; combat/F1; GUI scale 2 at 720p/1080p; verify blur=0/scale=2 once, change manually then restart and confirm preserved. Matching client/server update required. Not deployed.

Artifact: build/libs/createcityexpansioncore-1.0.2.2.jar (also retains preceding city land-access, claim-limit and boundary visuals).

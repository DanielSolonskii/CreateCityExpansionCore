package com.example.examplemod.client.screen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The World Map panel is created lazily, so this screen must stay usable while it
 * does not exist yet.
 *
 * <p><b>Why the panel is nullable.</b> The city pylon is decided by the server: the
 * client sends an empty request and the server answers with whichever city the
 * player belongs to. {@code CityMapPanel} needs that pylon and the dimension to be
 * constructed, so on the first open there is nothing to construct one from, and for
 * a player with no city there never will be. The panel is therefore null until the
 * snapshot arrives.
 *
 * <p><b>What broke.</b> The loading screen was already handled in {@code render},
 * and clicks and keys were already guarded. {@code init()} was not: it called
 * {@code applyMode()}, which set the panel's focus without a null check, so pressing
 * M with no city threw a NullPointerException on the very first frame and killed the
 * client. Drag, release and scroll had the same hole, one interaction away.
 *
 * <p><b>Why a source check.</b> The failing code is a GUI lifecycle, so it cannot be
 * exercised headless: constructing the screen needs a Minecraft instance and a font.
 * The check therefore verifies the structure that made it safe in the first place —
 * that every entry point Minecraft itself can call either guards the panel or is
 * reached only from a method that does — and names the exact hole so a regression
 * names its own cause.
 */
public final class WorldMapPanelLifecycleCheck {

    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    /**
     * Entry points Minecraft calls on a screen without the screen asking first.
     *
     * <p>Every one of these can run on the frame the screen appears, which is exactly
     * when the panel does not exist yet. Keys are method signatures as the parser
     * records them, that is name plus parameters, not the modifier.
     */
    private static final List<String> VANILLA_ENTRY_POINTS = List.of(
            "init()",
            "tick()",
            "render(",
            "mouseClicked(",
            "mouseDragged(",
            "mouseReleased(",
            "mouseScrolled(",
            "keyPressed(",
            "onClose()"
    );

    public static void main(String[] args) throws IOException {
        String source = Files.readString(
                sourceRoot().resolve("src/main/java/com/example/examplemod/"
                        + "client/screen/PolishedWorldMapScreen.java"),
                StandardCharsets.UTF_8);

        require(source.contains("private CityMapPanel map;"),
                "the panel field is nullable, which is why this check exists");

        List<Method> methods = parseMethods(source);
        require(methods.size() > 30,
                "the screen was parsed as Java, not truncated (methods=" + methods.size() + ")");

        vanillaEntryPointsAreGuarded(methods);
        panelIsRecreatedInTheRememberedMode(methods);
        unguardedDereferencesAreInternal(methods);
        onCloseAlwaysDismisses(methods);
        denialIsRenderedDistinctly(methods, source);

        System.out.println("PASS: " + checks + " World Map lazy-panel lifecycle checks");
    }

    /**
     * Every vanilla entry point must tolerate a null panel.
     *
     * <p>Either it checks the panel itself, or it delegates to a method that does.
     * Delegation is accepted deliberately: {@code render} draws the loading screen
     * itself and then returns, so it must not be made to "do nothing" instead.
     */
    private static void vanillaEntryPointsAreGuarded(List<Method> methods) {
        for (String signature : VANILLA_ENTRY_POINTS) {
            Method method = find(methods, signature);
            require(method != null, "entry point is present: " + signature);
            if (method == null) {
                continue;
            }
            require(method.guard != null,
                    signature + " must tolerate a null panel; it dereferences the panel with no "
                            + "guard and no delegation. Pressing M with no city reaches this on "
                            + "the first frame.");
        }
    }

    /**
     * The panel must appear in the mode the player is already looking at.
     *
     * <p>The mode is remembered by {@code applyMode} while the panel is absent. When
     * the panel is finally built, it has to be given that mode: there is no second
     * chance to call {@code applyMode}, because from {@code init()} the panel still
     * does not exist, and from {@code tick()} forcing CITIES would discard a tab the
     * player chose during loading.
     */
    private static void panelIsRecreatedInTheRememberedMode(List<Method> methods) {
        Method ensure = find(methods, "ensurePanel()");
        require(ensure != null, "ensurePanel is present");
        if (ensure != null) {
            require(ensure.body.contains("if (this.map != null) return;"),
                    "ensurePanel is idempotent");
            require(ensure.body.contains("if (this.data.getPylonPos() == null) return;"),
                    "ensurePanel waits for the server to name the city");
            require(ensure.body.contains("applyMode(this.mode);"),
                    "a freshly built panel is brought up in the remembered mode, not left "
                            + "unfocused and not forced back to CITIES");
        }

        Method apply = find(methods, "applyMode(");
        require(apply != null, "applyMode is present");
        if (apply != null) {
            require(apply.body.contains("this.mode = mode;"),
                    "applyMode remembers the mode even when it cannot apply it");
            require(apply.guard != null,
                    "applyMode must tolerate a null panel; init() calls it before the panel "
                            + "exists and that is the reported crash");
        }

        /*
         * The tick path must not hard-code CITIES any more. It used to, which meant a
         * tab chosen during loading was silently discarded the moment the pylon
         * arrived.
         */
        Method tick = find(methods, "tick()");
        require(tick != null, "tick is present");
        if (tick != null) {
            require(!tick.body.contains("applyMode(Mode.CITIES)"),
                    "tick no longer forces CITIES over the player's chosen tab");
        }
    }

    /**
     * Any remaining unguarded dereference must live in a method that is not a vanilla
     * entry point.
     *
     * <p>This is the part that generalises: if somebody later adds an override that
     * forgets the guard, the previous three checks pass and this one fails.
     */
    private static void unguardedDereferencesAreInternal(List<Method> methods) {
        List<String> offenders = new ArrayList<>();
        for (Method method : methods) {
            if (method.guard != null) {
                continue;
            }
            if (!method.body.contains("this.map.")) {
                continue;
            }
            if (VANILLA_ENTRY_POINTS.stream().anyMatch(method.signature::startsWith)) {
                offenders.add(method.signature);
            }
        }
        require(offenders.isEmpty(),
                "a vanilla entry point dereferences the panel without a guard: " + offenders);

        /*
         * And the reverse: the panel must actually be reachable, otherwise the screen
         * would be permanently stuck on the loading screen and every check above would
         * pass on dead code.
         */
        Method tick = find(methods, "tick()");
        require(tick != null && tick.body.contains("ensurePanel()"),
                "the panel is actually built while the screen is open");
        Method render = find(methods, "render(");
        require(render != null && render.body.contains("renderLoading("),
                "a missing panel shows the loading screen instead of an empty one");
    }

    /**
     * ESC must always leave the screen.
     *
     * <p>This is the reported "stuck on loading, ESC does nothing". {@code onClose}
     * used to end its read-only branch with {@code return} before reaching
     * {@code setScreen}, so the screen was never dismissed at all — and the session
     * had already been cleared, so reopening showed nothing either. The read-only
     * branch is exactly the one reached by the M key, which is why only that path
     * was broken.
     */
    private static void onCloseAlwaysDismisses(List<Method> methods) {
        Method close = find(methods, "onClose()");
        require(close != null, "onClose is present");
        if (close == null) {
            return;
        }
        require(close.body.contains("setScreen("),
                "onClose must change the screen; without it ESC cannot leave");

        /*
         * No early return may sit between the start of the method and setScreen.
         * That single `return` is the whole defect, so it is checked structurally
         * rather than by looking for one specific spelling.
         */
        int dismiss = close.body.indexOf("setScreen(");
        require(dismiss >= 0, "onClose reaches setScreen");
        String before = close.body.substring(0, Math.max(dismiss, 0));
        Matcher early = Pattern.compile("(?m)^\\s*return\\s*;").matcher(before);
        require(!early.find(),
                "onClose must not return before setScreen; that leaves the player "
                        + "stuck on the loading screen with a dead ESC");
    }

    /**
     * A denial must not be drawn as a loading state.
     *
     * <p>Showing "loading city territories" after the server has already said there
     * is no city is a lie the player cannot act on. It also hides the one thing
     * they can do, which is close the screen.
     */
    private static void denialIsRenderedDistinctly(List<Method> methods, String source) {
        Method loading = find(methods, "renderLoading(");
        require(loading != null, "renderLoading is present");
        if (loading == null) {
            return;
        }
        require(loading.body.contains("isDenied()"),
                "renderLoading distinguishes a denial from a pending load");
        // The message key is a string literal, and the parser blanks literals out.
        // It is therefore checked against the raw source, next to the method it
        // belongs to, rather than against the stripped body.
        int bodyStart = source.indexOf("private void renderLoading(");
        require(bodyStart >= 0, "renderLoading is locatable in the source");
        int bodyEnd = bodyStart < 0 ? -1 : source.indexOf("\n    }", bodyStart);
        String raw = bodyStart < 0 || bodyEnd < 0 ? "" : source.substring(bodyStart, bodyEnd);
        require(raw.contains("gui.createcityexpansioncore.world_map.no_city"),
                "a denial is reported in words rather than as a spinner");
        require(raw.contains("gui.createcityexpansioncore.world_map.no_city_hint"),
                "a denial tells the player how to leave");

        Method denied = find(methods, "isDenied()");
        require(denied != null, "the screen can read the session's denial");
        if (denied != null) {
            require(denied.body.contains("CityWorldMapSession"),
                    "the denial is read from the session, which outlives one opening");
        }

        // The keys must exist in both languages, or the player reads a raw key.
        for (String language : List.of("en_us", "ru_ru")) {
            String lang = read(sourceRoot().resolve(
                    "src/main/resources/assets/createcityexpansioncore/lang/"
                            + language + ".json"));
            require(lang.contains("\"gui.createcityexpansioncore.world_map.no_city\""),
                    language + " has the no-city message");
            require(lang.contains("\"gui.createcityexpansioncore.world_map.no_city_hint\""),
                    language + " has the no-city hint");
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + path);
        }
    }

    // ------------------------------------------------------------------ parsing

    private record Method(String signature, String body, String guard) {
    }

    private static Method find(List<Method> methods, String signatureStart) {
        for (Method method : methods) {
            if (method.signature.startsWith(signatureStart)) {
                return method;
            }
        }
        return null;
    }

    private static List<Method> parseMethods(String source) {
        // Strip comments and string literals first, so a guard mentioned in prose can
        // never satisfy the check and a signature split across lines still parses.
        String code = stripCommentsAndStrings(source);

        List<Method> methods = new ArrayList<>();
        Matcher matcher = Pattern.compile(
                "(?m)^\\s{4}(?:@\\w+\\s*)*(?:public|private|protected)[\\w<>\\[\\],. ?]*\\s"
                        + "(\\w+)\\(([^)]*)\\)\\s*\\{").matcher(code);
        while (matcher.find()) {
            int open = matcher.end() - 1;
            int close = matchBrace(code, open);
            if (close < 0) {
                continue;
            }
            String signature = matcher.group(1) + "(" + matcher.group(2).trim() + ")";
            String body = code.substring(open + 1, close);
            methods.add(new Method(signature, body, findGuard(body)));
        }
        return methods;
    }

    /**
     * The first statement that proves the panel may be absent.
     *
     * <p>Recognised shapes: an early return guarded on {@code this.map == null}, an
     * early return guarded on {@code this.map != null} (the "if it exists, tidy it
     * up" shape used by {@code onClose}), or a ternary that already tests for null.
     */
    private static String findGuard(String body) {
        Matcher matcher = Pattern.compile(
                "this\\.map\\s*(==|!=)\\s*null").matcher(body);
        return matcher.find() ? matcher.group() : null;
    }

    private static int matchBrace(String code, int openIndex) {
        int depth = 0;
        for (int index = openIndex; index < code.length(); index++) {
            char c = code.charAt(index);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static String stripCommentsAndStrings(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean lineComment = false;
        boolean blockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char c = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                    out.append(c);
                } else {
                    out.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    out.append("  ");
                    index++;
                } else {
                    out.append(c == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (!inString && !inChar && c == '/' && next == '/') {
                lineComment = true;
                out.append("  ");
                index++;
                continue;
            }
            if (!inString && !inChar && c == '/' && next == '*') {
                blockComment = true;
                out.append("  ");
                index++;
                continue;
            }
            if (escaped) {
                escaped = false;
                out.append(inString || inChar ? ' ' : c);
                continue;
            }
            if ((inString || inChar) && c == '\\') {
                escaped = true;
                out.append(' ');
                continue;
            }
            if (!inChar && c == '"') {
                inString = !inString;
                out.append(' ');
                continue;
            }
            if (!inString && c == '\'') {
                inChar = !inChar;
                out.append(' ');
                continue;
            }
            out.append(inString || inChar ? ' ' : c);
        }
        return out.toString();
    }

    private static Path sourceRoot() {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("src/main/java"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new AssertionError("cannot locate source tree");
        }
        return root;
    }
}

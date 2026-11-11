package com.example.examplemod.cutscene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class CutsceneJson {
    public static final int MAX_JSON_BYTES = 128 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> SCENE_FIELDS = Set.of("version", "dimension", "durationTicks", "camera", "sounds", "texts");
    private static final Set<String> CAMERA_FIELDS = Set.of("tick", "x", "y", "z", "yaw", "pitch", "easing");
    private static final Set<String> SOUND_FIELDS = Set.of("tick", "sound", "volume", "pitch");
    private static final Set<String> TEXT_FIELDS = Set.of("tick", "durationTicks", "text", "textColor", "backgroundColor");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{8}");

    private CutsceneJson() {
    }

    public static String encode(Cutscene scene) {
        JsonObject root = new JsonObject();
        root.addProperty("version", scene.version());
        root.addProperty("dimension", scene.dimension().toString());
        root.addProperty("durationTicks", scene.durationTicks());
        root.add("camera", writeTrack(scene.camera(), CutsceneJson::writeCamera));
        root.add("sounds", writeTrack(scene.sounds(), CutsceneJson::writeSound));
        root.add("texts", writeTrack(scene.texts(), CutsceneJson::writeText));
        String json = GSON.toJson(root) + "\n";
        checkSize(json);
        return json;
    }

    public static Cutscene decode(String json) {
        checkSize(json);
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            JsonElement value = GSON.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Unexpected content after the cutscene object");
            }
            JsonObject root = object(value, SCENE_FIELDS);
            return new Cutscene(integer(root, "version"), resourceId(root, "dimension"), integer(root, "durationTicks"),
                    readTrack(root, "camera", CAMERA_FIELDS, CutsceneJson::readCamera),
                    readTrack(root, "sounds", SOUND_FIELDS, CutsceneJson::readSound),
                    readTrack(root, "texts", TEXT_FIELDS, CutsceneJson::readText));
        } catch (IOException | JsonParseException exception) {
            throw new IllegalArgumentException("Malformed cutscene JSON", exception);
        }
    }

    private static void checkSize(String json) {
        if (json == null || json.length() > MAX_JSON_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Cutscene JSON must be at most " + MAX_JSON_BYTES + " UTF-8 bytes");
        }
    }

    private static <T> JsonArray writeTrack(List<T> items, Function<T, JsonObject> write) {
        JsonArray track = new JsonArray(items.size());
        for (T item : items) {
            track.add(write.apply(item));
        }
        return track;
    }

    private static JsonObject writeCamera(Cutscene.CameraKeyframe keyframe) {
        JsonObject object = new JsonObject();
        object.addProperty("tick", keyframe.tick());
        object.addProperty("x", keyframe.x());
        object.addProperty("y", keyframe.y());
        object.addProperty("z", keyframe.z());
        object.addProperty("yaw", keyframe.yaw());
        object.addProperty("pitch", keyframe.pitch());
        object.addProperty("easing", keyframe.easing().name().toLowerCase(Locale.ROOT));
        return object;
    }

    private static JsonObject writeSound(Cutscene.SoundCue cue) {
        JsonObject object = new JsonObject();
        object.addProperty("tick", cue.tick());
        object.addProperty("sound", cue.sound().toString());
        object.addProperty("volume", cue.volume());
        object.addProperty("pitch", cue.pitch());
        return object;
    }

    private static JsonObject writeText(Cutscene.TextCue cue) {
        JsonObject object = new JsonObject();
        object.addProperty("tick", cue.tick());
        object.addProperty("durationTicks", cue.durationTicks());
        object.addProperty("text", cue.text());
        object.addProperty("textColor", formatColor(cue.textColor()));
        object.addProperty("backgroundColor", formatColor(cue.backgroundColor()));
        return object;
    }

    private static <T> List<T> readTrack(JsonObject root, String name, Set<String> fields, Function<JsonObject, T> read) {
        if (!(root.get(name) instanceof JsonArray array) || array.size() > Cutscene.MAX_TRACK_ITEMS) {
            throw new IllegalArgumentException(name + " must be an array of at most " + Cutscene.MAX_TRACK_ITEMS + " entries");
        }
        List<T> items = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            try {
                items.add(read.apply(object(array.get(index), fields)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(name + "[" + index + "]: " + exception.getMessage(), exception);
            }
        }
        return items;
    }

    private static Cutscene.CameraKeyframe readCamera(JsonObject object) {
        Cutscene.Easing easing;
        try {
            easing = Cutscene.Easing.valueOf(string(object, "easing").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Expected easing linear, smooth, ease_in, ease_out or hold", exception);
        }
        return new Cutscene.CameraKeyframe(integer(object, "tick"), decimal(object, "x"), decimal(object, "y"),
                decimal(object, "z"), floating(object, "yaw"), floating(object, "pitch"), easing);
    }

    private static Cutscene.SoundCue readSound(JsonObject object) {
        return new Cutscene.SoundCue(integer(object, "tick"), resourceId(object, "sound"),
                floating(object, "volume"), floating(object, "pitch"));
    }

    private static Cutscene.TextCue readText(JsonObject object) {
        return new Cutscene.TextCue(integer(object, "tick"), integer(object, "durationTicks"), string(object, "text"),
                color(object, "textColor"), color(object, "backgroundColor"));
    }

    private static JsonObject object(JsonElement value, Set<String> fields) {
        if (!(value instanceof JsonObject object)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        for (String field : object.keySet()) {
            if (!fields.contains(field)) {
                throw new IllegalArgumentException("Unknown cutscene field: " + field);
            }
        }
        return object;
    }

    private static String string(JsonObject object, String name) {
        if (object.get(name) instanceof JsonPrimitive value && value.isString()) {
            return value.getAsString();
        }
        throw new IllegalArgumentException(name + " must be a JSON string");
    }

    private static String number(JsonObject object, String name) {
        if (object.get(name) instanceof JsonPrimitive value && value.isNumber()) {
            return value.getAsString();
        }
        throw new IllegalArgumentException(name + " must be a JSON number");
    }

    private static int integer(JsonObject object, String name) {
        try {
            return Integer.parseInt(number(object, name));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a whole number", exception);
        }
    }

    private static double decimal(JsonObject object, String name) {
        return Double.parseDouble(number(object, name));
    }

    private static float floating(JsonObject object, String name) {
        return Float.parseFloat(number(object, name));
    }

    private static ResourceLocation resourceId(JsonObject object, String name) {
        ResourceLocation id = ResourceLocation.tryParse(string(object, name));
        if (id == null) {
            throw new IllegalArgumentException(name + " must be a resource ID, e.g. minecraft:overworld");
        }
        return id;
    }

    private static String formatColor(int color) {
        return String.format(Locale.ROOT, "#%08X", color);
    }

    private static int color(JsonObject object, String name) {
        String color = string(object, name);
        if (!COLOR.matcher(color).matches()) {
            throw new IllegalArgumentException(name + " must use #AARRGGBB format");
        }
        return Integer.parseUnsignedInt(color, 1, color.length(), 16);
    }
}

package com.example.examplemod.cutscene;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

public record Cutscene(int version, ResourceLocation dimension, int durationTicks,
                       List<CameraKeyframe> camera, List<SoundCue> sounds, List<TextCue> texts) {
    public static final int CURRENT_VERSION = 1;
    public static final int MAX_DURATION_TICKS = 12_000;
    public static final int MAX_TRACK_ITEMS = 128;
    public static final int MAX_TEXT_LENGTH = 240;
    private static final int MAX_RESOURCE_ID_LENGTH = 256;
    private static final double MAX_COORDINATE = 30_000_000;

    public Cutscene {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported cutscene version: " + version);
        }
        requireResourceId(dimension, "dimension");
        requireDuration(durationTicks, "durationTicks");
        camera = orderedTrack(camera, CameraKeyframe::tick, "camera");
        sounds = orderedTrack(sounds, SoundCue::tick, "sounds");
        texts = orderedTrack(texts, TextCue::tick, "texts");

        int lastCameraTick = -1;
        for (CameraKeyframe keyframe : camera) {
            if (keyframe.tick() > durationTicks || keyframe.tick() == lastCameraTick) {
                throw new IllegalArgumentException("Camera ticks must be unique and at most durationTicks");
            }
            lastCameraTick = keyframe.tick();
        }
        for (SoundCue sound : sounds) {
            if (sound.tick() >= durationTicks) {
                throw new IllegalArgumentException("Sound cues must start before durationTicks");
            }
        }
        for (TextCue text : texts) {
            if (text.tick() + text.durationTicks() > durationTicks) {
                throw new IllegalArgumentException("Text cues must end at or before durationTicks");
            }
        }
    }

    private static <T> List<T> orderedTrack(List<T> track, ToIntFunction<T> tick, String name) {
        if (track == null || track.size() > MAX_TRACK_ITEMS || track.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " must contain at most " + MAX_TRACK_ITEMS + " entries");
        }
        return track.stream().sorted(Comparator.comparingInt(tick)).toList();
    }

    private static void requireResourceId(ResourceLocation value, String name) {
        if (value == null || value.getPath().isEmpty() || value.toString().length() > MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException(name + " must be a nonempty resource ID of at most "
                    + MAX_RESOURCE_ID_LENGTH + " characters");
        }
    }

    private static void requireDuration(int value, String name) {
        if (value < 1 || value > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException(name + " must be between 1 and " + MAX_DURATION_TICKS);
        }
    }

    private static void requireTick(int tick) {
        if (tick < 0 || tick > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("tick must be between 0 and " + MAX_DURATION_TICKS);
        }
    }

    private static void requireRange(double value, double min, double max, String name) {
        if (!(value >= min && value <= max)) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }

    public enum Easing {
        LINEAR, SMOOTH, EASE_IN, EASE_OUT, HOLD
    }

    /** Easing applies from this keyframe to the next one. */
    public record CameraKeyframe(int tick, double x, double y, double z, float yaw, float pitch, Easing easing) {
        public CameraKeyframe {
            requireTick(tick);
            requireRange(x, -MAX_COORDINATE, MAX_COORDINATE, "x");
            requireRange(y, -MAX_COORDINATE, MAX_COORDINATE, "y");
            requireRange(z, -MAX_COORDINATE, MAX_COORDINATE, "z");
            requireRange(pitch, -90, 90, "pitch");
            if (!Float.isFinite(yaw) || easing == null) {
                throw new IllegalArgumentException("Camera keyframes require a finite yaw and an easing mode");
            }
            yaw %= 360;
            if (yaw >= 180) {
                yaw -= 360;
            } else if (yaw < -180) {
                yaw += 360;
            }
        }
    }

    public record SoundCue(int tick, ResourceLocation sound, float volume, float pitch) {
        public SoundCue {
            requireTick(tick);
            requireResourceId(sound, "sound");
            requireRange(volume, 0, 4, "volume");
            requireRange(pitch, 0.5, 2, "pitch");
        }
    }

    public record TextCue(int tick, int durationTicks, String text, int textColor, int backgroundColor) {
        public TextCue {
            requireTick(tick);
            requireDuration(durationTicks, "text durationTicks");
            if (text == null || text.isBlank() || text.length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("Text must contain 1–" + MAX_TEXT_LENGTH + " characters");
            }
            if (text.codePoints().anyMatch(TextCue::invalidCodePoint)) {
                throw new IllegalArgumentException("Text contains an invalid character");
            }
        }

        private static boolean invalidCodePoint(int codePoint) {
            return (Character.isISOControl(codePoint) && codePoint != '\n')
                    || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
        }
    }
}

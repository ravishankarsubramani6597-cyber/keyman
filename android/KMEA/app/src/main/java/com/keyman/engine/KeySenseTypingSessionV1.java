package com.keyman.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KEYSENSE v1 canonical Android handoff record, matching the iOS
 * KeySenseTypingSessionV1 field names and frozen KEYSENSE schema.
 *
 * This is an aggregate-only, immutable DATA OBJECT, not a network endpoint.
 * Does not contain message text, emoji identity, individual key events or
 * per-event timings. App identifiers are deliberately null in v1.
 *
 * Only create from a finalized KeySenseSessionManager.CompletedSession.
 * It does not modify sessionization, counting, or existing debug behavior.
 */
public final class KeySenseTypingSessionV1 {
    public static final int SCHEMA_VERSION = 1;
    public static final long PAUSE_THRESHOLD_MS = 2_000L;
    public static final long SESSION_TIMEOUT_MS = 30_000L;

    public final int schemaVersion;
    public final String participantId;
    public final String sessionId;
    public final String platform;
    public final String keyboardVersion;
    public final long sessionStartUtcMs;
    public final long sessionEndUtcMs;
    public final int timezoneOffsetMinutes;
    public final long sessionDurationMs;
    public final String sessionEndReason;
    public final long pauseThresholdMs;
    public final long sessionTimeoutMs;
    public final String appIdentifier;
    public final String appCategory;
    public final long totalKeyCount;
    public final long characterCount;
    public final long lowercaseCount;
    public final long uppercaseCount;
    public final long numericCount;
    public final long specialCharCount;
    public final long spaceCount;
    public final long newlineCount;
    public final long backspaceCount;
    public final long wordCount;
    public final long emojiCount;
    public final long uniqueEmojiCount;
    public final long emojiBurstCount;
    public final long maxEmojiRunLength;
    public final long repeatedEmojiCount;
    public final long activeIkiCount;
    public final Double interKeyIntervalMeanMs;
    public final Double interKeyIntervalMedianMs;
    public final Double interKeyIntervalSdMs;
    public final Double interKeyIntervalMinMs;
    public final Double interKeyIntervalMaxMs;
    public final long pauseCount;
    public final long totalPauseDurationMs;
    public final Double meanPauseDurationMs;
    public final Double maxPauseDurationMs;
    public final Double pauseTimeRatio;
    public final long typingBurstCount;
    public final Double meanBurstLength;
    public final long maxBurstLength;
    public final Double deletionRate;
    public final Double charactersPerWord;
    public final Double newlinesPerWord;
    public final Double keysPerSecond;
    public final Double charactersPerSecond;
    public final Double wordsPerMinute;
    public final long activeTypingDurationMs;
    public final Double activeTypingRate;
    public final Double typingIntervalCv;
    public final Double emojiRate;
    public final Double emojiDiversityRatio;
    public final String orientation;
    public final long predictionSelectionCount;

    private KeySenseTypingSessionV1(KeySenseSessionManager.CompletedSession s,
            String participantId, String keyboardVersion) {
        this.schemaVersion = 1;
        this.participantId = participantId;
        this.sessionId = s.sessionId;
        this.platform = "android";
        this.keyboardVersion = keyboardVersion;
        this.sessionStartUtcMs = s.startUtcMs;
        this.sessionEndUtcMs = s.endUtcMs;
        this.timezoneOffsetMinutes = s.timezoneOffsetMinutes;
        this.sessionDurationMs = s.durationMs;
        this.sessionEndReason = s.endReason.serializedValue;
        this.pauseThresholdMs = 2000L;
        this.sessionTimeoutMs = 30000L;
        this.appIdentifier = null;
        this.appCategory = null;
        this.totalKeyCount = s.counts.totalKeyCount;
        this.characterCount = s.counts.characterCount;
        this.lowercaseCount = s.counts.lowercaseCount;
        this.uppercaseCount = s.counts.uppercaseCount;
        this.numericCount = s.counts.numericCount;
        this.specialCharCount = s.counts.specialCharCount;
        this.spaceCount = s.counts.spaceCount;
        this.newlineCount = s.counts.newlineCount;
        this.backspaceCount = s.counts.backspaceCount;
        this.wordCount = s.counts.wordCount;
        this.emojiCount = s.counts.emojiCount;
        this.uniqueEmojiCount = s.counts.uniqueEmojiCount;
        this.emojiBurstCount = s.counts.emojiBurstCount;
        this.maxEmojiRunLength = s.counts.maxEmojiRunLength;
        this.repeatedEmojiCount = s.counts.repeatedEmojiCount;
        this.activeIkiCount = s.timing.activeIkiCount;
        this.interKeyIntervalMeanMs = s.timing.activeIkiMeanMs;
        this.interKeyIntervalMedianMs = s.timing.activeIkiMedianMs;
        this.interKeyIntervalSdMs = s.timing.interKeyIntervalSdMs;
        this.interKeyIntervalMinMs = s.timing.activeIkiMinMs;
        this.interKeyIntervalMaxMs = s.timing.activeIkiMaxMs;
        this.pauseCount = s.timing.pauseCount;
        this.totalPauseDurationMs = Math.max(0L, Math.round(s.timing.totalPauseDurationMs));
        this.meanPauseDurationMs = s.derived.meanPauseDurationMs;
        this.maxPauseDurationMs = s.timing.maxPauseDurationMs;
        this.pauseTimeRatio = s.derived.pauseTimeRatio;
        this.typingBurstCount = s.timing.typingBurstCount;
        this.meanBurstLength = s.derived.meanBurstLength;
        this.maxBurstLength = s.timing.maxBurstLength;
        this.deletionRate = s.derived.deletionRate;
        this.charactersPerWord = s.derived.charactersPerWord;
        this.newlinesPerWord = s.derived.newlinesPerWord;
        this.keysPerSecond = s.derived.keysPerSecond;
        this.charactersPerSecond = s.derived.charactersPerSecond;
        this.wordsPerMinute = s.derived.wordsPerMinute;
        this.activeTypingDurationMs = s.derived.activeTypingDurationMs;
        this.activeTypingRate = s.derived.activeTypingRate;
        this.typingIntervalCv = s.derived.typingIntervalCv;
        this.emojiRate = s.derived.emojiRate;
        this.emojiDiversityRatio = s.derived.emojiDiversityRatio;
        this.orientation = orientationLabel(s.orientation);
        this.predictionSelectionCount = s.counts.predictionSelectionCount;
    }

    public static KeySenseTypingSessionV1 fromCompleted(
            KeySenseSessionManager.CompletedSession completed,
            String participantId,
            String keyboardVersion) {
        if (completed == null) throw new IllegalArgumentException("Completed session required");
        if (completed.counts == null || completed.timing == null || completed.derived == null) {
            throw new IllegalArgumentException("Missing finalized aggregates");
        }
        if (participantId == null || participantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Participant pseudonym required");
        }
        if (keyboardVersion == null || keyboardVersion.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyboard version required");
        }
        KeySenseTypingSessionV1 result = new KeySenseTypingSessionV1(
                completed, participantId, keyboardVersion);
        result.validate();
        return result;
    }

    private static String orientationLabel(KeySenseSessionManager.Orientation o) {
        if (o == null) return "unknown";
        switch (o) {
            case PORTRAIT: return "portrait";
            case PORTRAIT_UPSIDE_DOWN: return "portrait_upside_down";
            case LANDSCAPE_LEFT: return "landscape_left";
            case LANDSCAPE_RIGHT: return "landscape_right";
            default: return "unknown";
        }
    }

    /** Reject corrupt records; do not silently modify measured aggregates. */
    public void validate() {
        if (sessionId == null || sessionId.isEmpty()) throw new IllegalStateException("No sessionId");
        if (sessionStartUtcMs > sessionEndUtcMs) throw new IllegalStateException("Negative UTC window");
        if (sessionDurationMs < 0) throw new IllegalStateException("Negative duration");
        if (characterCount != lowercaseCount + uppercaseCount + numericCount
                + specialCharCount + spaceCount + newlineCount + emojiCount) {
            throw new IllegalStateException("Character count mismatch");
        }
        if (totalKeyCount != characterCount + backspaceCount) {
            throw new IllegalStateException("Key count mismatch");
        }
        if (uniqueEmojiCount > emojiCount || repeatedEmojiCount > emojiCount) {
            throw new IllegalStateException("Inconsistent emoji aggregates");
        }
        if (pauseThresholdMs != PAUSE_THRESHOLD_MS || sessionTimeoutMs != SESSION_TIMEOUT_MS) {
            throw new IllegalStateException("Unexpected timing thresholds");
        }
    }

    /**
     * Stable canonical key/value map for JSON/PROSIT adapters. Nullable
     * measurements remain Java null and must become JSON null, not zero.
     * The returned map cannot be changed by callers.
     */
    public Map<String, Object> toFieldMap() {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", schemaVersion);
        data.put("participantId", participantId);
        data.put("sessionId", sessionId);
        data.put("platform", platform);
        data.put("keyboardVersion", keyboardVersion);
        data.put("sessionStartUtcMs", sessionStartUtcMs);
        data.put("sessionEndUtcMs", sessionEndUtcMs);
        data.put("timezoneOffsetMinutes", timezoneOffsetMinutes);
        data.put("sessionDurationMs", sessionDurationMs);
        data.put("sessionEndReason", sessionEndReason);
        data.put("pauseThresholdMs", pauseThresholdMs);
        data.put("sessionTimeoutMs", sessionTimeoutMs);
        data.put("appIdentifier", appIdentifier);
        data.put("appCategory", appCategory);
        data.put("totalKeyCount", totalKeyCount);
        data.put("characterCount", characterCount);
        data.put("lowercaseCount", lowercaseCount);
        data.put("uppercaseCount", uppercaseCount);
        data.put("numericCount", numericCount);
        data.put("specialCharCount", specialCharCount);
        data.put("spaceCount", spaceCount);
        data.put("newlineCount", newlineCount);
        data.put("backspaceCount", backspaceCount);
        data.put("wordCount", wordCount);
        data.put("emojiCount", emojiCount);
        data.put("uniqueEmojiCount", uniqueEmojiCount);
        data.put("emojiBurstCount", emojiBurstCount);
        data.put("maxEmojiRunLength", maxEmojiRunLength);
        data.put("repeatedEmojiCount", repeatedEmojiCount);
        data.put("activeIkiCount", activeIkiCount);
        data.put("interKeyIntervalMeanMs", interKeyIntervalMeanMs);
        data.put("interKeyIntervalMedianMs", interKeyIntervalMedianMs);
        data.put("interKeyIntervalSdMs", interKeyIntervalSdMs);
        data.put("interKeyIntervalMinMs", interKeyIntervalMinMs);
        data.put("interKeyIntervalMaxMs", interKeyIntervalMaxMs);
        data.put("pauseCount", pauseCount);
        data.put("totalPauseDurationMs", totalPauseDurationMs);
        data.put("meanPauseDurationMs", meanPauseDurationMs);
        data.put("maxPauseDurationMs", maxPauseDurationMs);
        data.put("pauseTimeRatio", pauseTimeRatio);
        data.put("typingBurstCount", typingBurstCount);
        data.put("meanBurstLength", meanBurstLength);
        data.put("maxBurstLength", maxBurstLength);
        data.put("deletionRate", deletionRate);
        data.put("charactersPerWord", charactersPerWord);
        data.put("newlinesPerWord", newlinesPerWord);
        data.put("keysPerSecond", keysPerSecond);
        data.put("charactersPerSecond", charactersPerSecond);
        data.put("wordsPerMinute", wordsPerMinute);
        data.put("activeTypingDurationMs", activeTypingDurationMs);
        data.put("activeTypingRate", activeTypingRate);
        data.put("typingIntervalCv", typingIntervalCv);
        data.put("emojiRate", emojiRate);
        data.put("emojiDiversityRatio", emojiDiversityRatio);
        data.put("orientation", orientation);
        data.put("predictionSelectionCount", predictionSelectionCount);
        return Collections.unmodifiableMap(data);
    }
}

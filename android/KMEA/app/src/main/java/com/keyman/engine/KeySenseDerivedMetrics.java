package com.keyman.engine;

/**
 * KEYSENSE: immutable, privacy-preserving metrics derived from completed
 * session-level aggregates. No raw text or individual keystrokes are accepted.
 *
 * Formulas and null semantics follow the frozen KEYSENSE v1 specification.
 * Emoji diversity uses only aggregate unique/total counts computed in RAM.
 * Neither emoji identity nor typed content is retained here.
 */
public final class KeySenseDerivedMetrics {
    public final Double meanPauseDurationMs;
    public final Double pauseTimeRatio;
    public final Double meanBurstLength;
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

    private KeySenseDerivedMetrics(
            Double meanPauseDurationMs, Double pauseTimeRatio,
            Double meanBurstLength, Double deletionRate,
            Double charactersPerWord, Double newlinesPerWord,
            Double keysPerSecond, Double charactersPerSecond,
            Double wordsPerMinute, long activeTypingDurationMs,
            Double activeTypingRate, Double typingIntervalCv, Double emojiRate,
            Double emojiDiversityRatio) {
        this.meanPauseDurationMs = meanPauseDurationMs;
        this.pauseTimeRatio = pauseTimeRatio;
        this.meanBurstLength = meanBurstLength;
        this.deletionRate = deletionRate;
        this.charactersPerWord = charactersPerWord;
        this.newlinesPerWord = newlinesPerWord;
        this.keysPerSecond = keysPerSecond;
        this.charactersPerSecond = charactersPerSecond;
        this.wordsPerMinute = wordsPerMinute;
        this.activeTypingDurationMs = activeTypingDurationMs;
        this.activeTypingRate = activeTypingRate;
        this.typingIntervalCv = typingIntervalCv;
        this.emojiRate = emojiRate;
        this.emojiDiversityRatio = emojiDiversityRatio;
    }

    /** Calculate metrics only after word-count finalization. */
    public static KeySenseDerivedMetrics compute(
            KeySenseSessionAccumulator.Counts counts,
            KeySenseSessionAccumulator.TimingSnapshot timing,
            long sessionDurationMs) {
        if (counts == null || timing == null) {
            throw new IllegalArgumentException("Completed counts and timing are required");
        }
        if (sessionDurationMs < 0) {
            throw new IllegalArgumentException("Session duration cannot be negative");
        }

        // The schema defines pause duration as integer milliseconds; the
        // accumulator preserves fractional milliseconds during measurement.
        // Round only when calculating duration-based canonical metrics.
        long pauseMs = Math.max(0L, Math.round(timing.totalPauseDurationMs));
        // Allow for sub-ms rounding at the session boundary without producing
        // a negative active duration. Final record validation remains separate.
        long activeMs = Math.max(0L, sessionDurationMs - pauseMs);

        double chars = counts.characterCount;
        double keys = counts.totalKeyCount;
        double words = counts.wordCount;
        double typedWordChars = counts.lowercaseCount + counts.uppercaseCount
                + counts.numericCount + counts.specialCharCount;

        Double cv = (timing.activeIkiCount >= 2
                && timing.interKeyIntervalSdMs != null
                && timing.activeIkiMeanMs != null
                && timing.activeIkiMeanMs > 0)
                ? timing.interKeyIntervalSdMs / timing.activeIkiMeanMs : null;

        return new KeySenseDerivedMetrics(
                timing.pauseCount > 0 ? timing.totalPauseDurationMs / timing.pauseCount : null,
                sessionDurationMs > 0 ? Math.min(1.0, (double) pauseMs / sessionDurationMs) : null,
                timing.typingBurstCount > 0 ? keys / timing.typingBurstCount : null,
                chars > 0 ? counts.backspaceCount / chars : null,
                words > 0 ? typedWordChars / words : null,
                words > 0 ? counts.newlineCount / words : null,
                sessionDurationMs > 0 ? 1000.0 * keys / sessionDurationMs : null,
                sessionDurationMs > 0 ? 1000.0 * chars / sessionDurationMs : null,
                sessionDurationMs > 0 ? 60000.0 * words / sessionDurationMs : null,
                activeMs,
                activeMs > 0 ? 1000.0 * keys / activeMs : null,
                cv,
                chars > 0 ? counts.emojiCount / chars : null,
                counts.emojiCount > 0
                        ? (double) counts.uniqueEmojiCount / counts.emojiCount : null);
    }
}

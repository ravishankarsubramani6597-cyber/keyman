package com.keyman.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * KEYSENSE Android: RAM-only statistics for one eligible typing session.
 *
 * This is an isolated metrics component, NOT an input-event detector.
 * Call process() only for one validated user typing event; Keyman's
 * insertText() bridge may report transformations, not original key actions.
 *
 * The session manager (to be implemented) decides which monotonic gaps
 * qualify as active IKIs versus pauses, and calls registerActiveIki() or
 * registerPause() accordingly. This class does not inspect or store text.
 */
public final class KeySenseSessionAccumulator {

    private long lowercaseCount;
    private long uppercaseCount;
    private long numericCount;
    private long specialCharCount;
    private long spaceCount;
    private long newlineCount;
    private long backspaceCount;
    private long emojiCount;
    private long wordCount;
    private long predictionSelectionCount;

    // Identity-bearing state is strictly transient and cleared at finish().
    // Completed Counts contains aggregate statistics only, never emoji strings.
    private final Set<String> transientEmojiIdentities = new HashSet<>();
    private String previousEmojiIdentity;
    private long currentEmojiRunLength;
    private long emojiBurstCount;
    private long maxEmojiRunLength;
    private long repeatedEmojiCount;
    private Counts finalizedCounts;

    private boolean insideWord;
    private boolean finished;

    // Timing: Welford's algorithm, retaining only transient IKI durations for
    // the exact median. None of the individual IKI values is in TimingSnapshot.
    private long activeIkiCount;
    private double ikiMeanMs;
    private double ikiM2;
    private Double ikiMinMs;
    private Double ikiMaxMs;
    private final ArrayList<Double> activeIkiValuesMs = new ArrayList<>();

    private long pauseCount;
    private double totalPauseDurationMs;
    private Double maxPauseDurationMs;

    // The iOS accumulator starts the first burst at 1. Empty sessions must not
    // be persisted by the manager. process() includes backspace and emoji.
    private long typingBurstCount = 1;
    private long currentBurstLength;
    private long maxBurstLength;
    private long typingEventCount;

    /** Process exactly one independently validated typing event. */
    public synchronized void process(KeySenseCharacterClassifier.EventKind kind) {
        ensureActive();
        if (kind == null) {
            throw new IllegalArgumentException("Event kind must not be null");
        }
        if (kind == KeySenseCharacterClassifier.EventKind.EMOJI) {
            throw new IllegalArgumentException("Use processEmoji() for an emoji selection");
        }

        switch (kind) {
            case LOWERCASE:
                lowercaseCount++;
                insideWord = true;
                break;
            case UPPERCASE:
                uppercaseCount++;
                insideWord = true;
                break;
            case NUMBER:
                numericCount++;
                insideWord = true;
                break;
            case SPECIAL_WORD_INTERNAL:
                specialCharCount++;
                break;
            case SPECIAL_OTHER:
                specialCharCount++;
                finalizePendingWord();
                break;
            case SPACE:
                finalizePendingWord();
                spaceCount++;
                break;
            case NEWLINE:
                finalizePendingWord();
                newlineCount++;
                break;
            case BACKSPACE:
                backspaceCount++;
                break;
            default:
                throw new IllegalArgumentException("Unsupported event kind");
        }

        // Any non-emoji typing event terminates the current consecutive emoji run.
        resetEmojiRun();
        typingEventCount++;
        currentBurstLength++;
    }

    /**
     * Record one verified native emoji selection. The identity is used solely
     * to calculate diversity and adjacent repetition inside this active session.
     * It is neither logged nor included in any completed record.
     */
    public synchronized void processEmoji(String emojiIdentity) {
        ensureActive();
        if (emojiIdentity == null || emojiIdentity.isEmpty()) {
            throw new IllegalArgumentException("Nonempty emoji identity required");
        }
        finalizePendingWord();
        emojiCount++;
        transientEmojiIdentities.add(emojiIdentity);
        if (currentEmojiRunLength == 0) {
            emojiBurstCount++;
        } else if (emojiIdentity.equals(previousEmojiIdentity)) {
            repeatedEmojiCount++;
        }
        currentEmojiRunLength++;
        if (currentEmojiRunLength > maxEmojiRunLength) {
            maxEmojiRunLength = currentEmojiRunLength;
        }
        previousEmojiIdentity = emojiIdentity;
        typingEventCount++;
        currentBurstLength++;
    }

    private void resetEmojiRun() {
        currentEmojiRunLength = 0;
        previousEmojiIdentity = null;
    }

    /** One verified explicit prediction-banner selection; not a typing key. */
    public synchronized void registerPredictionSelection() {
        ensureActive();
        predictionSelectionCount++;
    }

    /**
     * Record an active inter-keystroke interval, in milliseconds. The manager
     * must choose the threshold using monotonic time, not wall-clock time.
     * Matches iOS: only strictly positive intervals are counted.
     */
    public synchronized void registerActiveIki(double milliseconds) {
        ensureActive();
        if ((Double.isNaN(milliseconds) || Double.isInfinite(milliseconds)) || milliseconds <= 0) {
            return;
        }

        activeIkiCount++;
        double delta = milliseconds - ikiMeanMs;
        ikiMeanMs += delta / activeIkiCount;
        double delta2 = milliseconds - ikiMeanMs;
        ikiM2 += delta * delta2;
        if (ikiMinMs == null || milliseconds < ikiMinMs) ikiMinMs = milliseconds;
        if (ikiMaxMs == null || milliseconds > ikiMaxMs) ikiMaxMs = milliseconds;
        activeIkiValuesMs.add(milliseconds);
    }

    /**
     * Register one manager-confirmed pause interval in milliseconds. A pause
     * closes the previous burst and starts the next one. Call exactly once
     * for each real inter-event pause; do not call on dismiss/timeout alone.
     */
    public synchronized void registerPause(double milliseconds) {
        ensureActive();
        if ((Double.isNaN(milliseconds) || Double.isInfinite(milliseconds)) || milliseconds < 0 || typingEventCount == 0) {
            return;
        }

        pauseCount++;
        totalPauseDurationMs += milliseconds;
        if (maxPauseDurationMs == null || milliseconds > maxPauseDurationMs) {
            maxPauseDurationMs = milliseconds;
        }
        finalizeCurrentBurst();
        typingBurstCount++;
        currentBurstLength = 0;
        resetEmojiRun();
    }

    /** Complete the session exactly once, including any unfinished word. */
    public synchronized Counts finish() {
        if (!finished) {
            finalizePendingWord();
            finalizeCurrentBurst();
            finalizedCounts = snapshot();
            finished = true;
            // Discard transient content-sensitive state after reduction to counts.
            transientEmojiIdentities.clear();
            resetEmojiRun();
        }
        return finalizedCounts;
    }

    /** Before finish(), an unfinished word is not yet included in wordCount. */
    public synchronized Counts snapshot() {
        if (finished) return finalizedCounts;
        return new Counts(lowercaseCount, uppercaseCount, numericCount,
                specialCharCount, spaceCount, newlineCount, backspaceCount,
                wordCount, emojiCount, predictionSelectionCount,
                transientEmojiIdentities.size(), emojiBurstCount,
                maxEmojiRunLength, repeatedEmojiCount);
    }

    /** Statistical aggregates only; no text or per-keystroke timestamp values. */
    public synchronized TimingSnapshot timingSnapshot() {
        Double medianMs = null;
        if (!activeIkiValuesMs.isEmpty()) {
            ArrayList<Double> sorted = new ArrayList<>(activeIkiValuesMs);
            Collections.sort(sorted);
            int n = sorted.size();
            medianMs = n % 2 == 1
                    ? sorted.get(n / 2)
                    : sorted.get(n / 2 - 1) / 2.0 + sorted.get(n / 2) / 2.0;
        }

        // KEYSENSE v1: population SD, defined only when at least two active IKIs exist.
        // Welford's ikiM2 accumulates the sum of squared deviations from the mean.
        Double populationIkiSdMs = activeIkiCount > 1
                ? Math.sqrt(Math.max(0.0, ikiM2 / activeIkiCount)) : null;

        return new TimingSnapshot(activeIkiCount,
                activeIkiCount > 0 ? ikiMeanMs : null,
                medianMs, populationIkiSdMs, ikiMinMs, ikiMaxMs,
                pauseCount, totalPauseDurationMs, maxPauseDurationMs,
                typingEventCount > 0 ? typingBurstCount : 0,
                typingEventCount > 0 ? Math.max(maxBurstLength, currentBurstLength) : 0,
                typingEventCount > 0 ? ((double) typingEventCount / typingBurstCount) : null);
    }

    private void finalizePendingWord() {
        if (insideWord) {
            wordCount++;
            insideWord = false;
        }
    }

    private void finalizeCurrentBurst() {
        if (currentBurstLength > maxBurstLength) {
            maxBurstLength = currentBurstLength;
        }
    }

    private void ensureActive() {
        if (finished) {
            throw new IllegalStateException("Session accumulator already finished");
        }
    }

    /** Aggregate metadata only. Includes no raw text or emoji identities. */
    public static final class Counts {
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
        public final long predictionSelectionCount;
        public final long characterCount;
        public final long totalKeyCount;

        private Counts(long lowercaseCount, long uppercaseCount, long numericCount,
                long specialCharCount, long spaceCount, long newlineCount,
                long backspaceCount, long wordCount, long emojiCount,
                long predictionSelectionCount, long uniqueEmojiCount,
                long emojiBurstCount, long maxEmojiRunLength,
                long repeatedEmojiCount) {
            this.lowercaseCount = lowercaseCount;
            this.uppercaseCount = uppercaseCount;
            this.numericCount = numericCount;
            this.specialCharCount = specialCharCount;
            this.spaceCount = spaceCount;
            this.newlineCount = newlineCount;
            this.backspaceCount = backspaceCount;
            this.wordCount = wordCount;
            this.emojiCount = emojiCount;
            this.uniqueEmojiCount = uniqueEmojiCount;
            this.emojiBurstCount = emojiBurstCount;
            this.maxEmojiRunLength = maxEmojiRunLength;
            this.repeatedEmojiCount = repeatedEmojiCount;
            this.predictionSelectionCount = predictionSelectionCount;
            this.characterCount = lowercaseCount + uppercaseCount + numericCount
                    + specialCharCount + spaceCount + newlineCount + emojiCount;
            this.totalKeyCount = characterCount + backspaceCount;
        }
    }

    /** Immutable scalar timing/burst aggregates. Absent values are null. */
    public static final class TimingSnapshot {
        public final long activeIkiCount;
        public final Double activeIkiMeanMs;
        public final Double activeIkiMedianMs;
        public final Double interKeyIntervalSdMs;
        public final Double activeIkiMinMs;
        public final Double activeIkiMaxMs;
        public final long pauseCount;
        public final double totalPauseDurationMs;
        public final Double maxPauseDurationMs;
        public final long typingBurstCount;
        public final long maxBurstLength;
        public final Double averageBurstLength;

        private TimingSnapshot(long activeIkiCount, Double activeIkiMeanMs,
                Double activeIkiMedianMs, Double interKeyIntervalSdMs,
                Double activeIkiMinMs, Double activeIkiMaxMs,
                long pauseCount, double totalPauseDurationMs, Double maxPauseDurationMs,
                long typingBurstCount, long maxBurstLength, Double averageBurstLength) {
            this.activeIkiCount = activeIkiCount;
            this.activeIkiMeanMs = activeIkiMeanMs;
            this.activeIkiMedianMs = activeIkiMedianMs;
            this.interKeyIntervalSdMs = interKeyIntervalSdMs;
            this.activeIkiMinMs = activeIkiMinMs;
            this.activeIkiMaxMs = activeIkiMaxMs;
            this.pauseCount = pauseCount;
            this.totalPauseDurationMs = totalPauseDurationMs;
            this.maxPauseDurationMs = maxPauseDurationMs;
            this.typingBurstCount = typingBurstCount;
            this.maxBurstLength = maxBurstLength;
            this.averageBurstLength = averageBurstLength;
        }
    }
}

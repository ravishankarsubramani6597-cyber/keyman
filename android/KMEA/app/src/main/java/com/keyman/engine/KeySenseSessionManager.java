package com.keyman.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

/**
 * KEYSENSE Android: in-memory session lifecycle coordinator.
 *
 * IMPORTANT: This class is not a detector of original user actions. Only send
 * validated, privacy-eligible typing events to onTypingEvent(). In particular,
 * Keyman's insertText() calls are text transformations and MUST NOT be sent
 * directly to this manager as if they were keystrokes.
 *
 * No raw text, app identifiers, or editor identifiers enter this class.
 * Native emoji identities pass through RAM transiently and are reduced to
 * aggregate counts before completion. No identity enters CompletedSession.
 * Completed sessions remain in RAM until drainCompleted() is called.
 */
public final class KeySenseSessionManager {

    public enum Orientation {
        PORTRAIT, PORTRAIT_UPSIDE_DOWN, LANDSCAPE_LEFT, LANDSCAPE_RIGHT, UNKNOWN
    }

    public enum EndReason {
        INACTIVITY_TIMEOUT("inactivity_timeout"),
        KEYBOARD_DISMISSED("keyboard_dismissed"),
        INPUT_CONTEXT_CHANGED("input_context_changed"),
        APP_CONTEXT_CHANGED("app_context_changed"),
        ORIENTATION_CHANGED("orientation_changed"),
        EXTENSION_TERMINATED("extension_terminated"),
        OTHER("other");

        public final String serializedValue;
        EndReason(String serializedValue) { this.serializedValue = serializedValue; }
    }

    /** A clock event with independent UTC and monotonic times. */
    public static final class EventTime {
        public final long utcMs;
        public final long monotonicNs;
        public final int timezoneOffsetMinutes;

        public EventTime(long utcMs, long monotonicNs, int timezoneOffsetMinutes) {
            this.utcMs = utcMs;
            this.monotonicNs = monotonicNs;
            this.timezoneOffsetMinutes = timezoneOffsetMinutes;
        }
    }

    /** Caller-supplied clock: inject a fake clock in tests or Android SystemClock in production. */
    public interface Clock {
        EventTime capture();
    }

    /**
     * JVM-compatible fallback. Android production should instead inject a Clock
     * using android.os.SystemClock.elapsedRealtimeNanos() for suspend-aware time.
     */
    public static final class JavaClock implements Clock {
        @Override public EventTime capture() {
            long now = System.currentTimeMillis();
            int offset = TimeZone.getDefault().getOffset(now) / 60000;
            return new EventTime(now, System.nanoTime(), offset);
        }
    }

    /** Immutable aggregate-only record. Never contains content or per-key timings. */
    public static final class CompletedSession {
        public final String sessionId;
        public final long startUtcMs;
        public final long endUtcMs;
        public final long durationMs;
        public final int timezoneOffsetMinutes;
        public final Orientation orientation;
        public final EndReason endReason;
        public final KeySenseSessionAccumulator.Counts counts;
        public final KeySenseSessionAccumulator.TimingSnapshot timing;
        public final KeySenseDerivedMetrics derived;

        private CompletedSession(String sessionId, EventTime start, EventTime last,
                Orientation orientation, EndReason endReason,
                KeySenseSessionAccumulator.Counts counts,
                KeySenseSessionAccumulator.TimingSnapshot timing) {
            this.sessionId = sessionId;
            this.startUtcMs = start.utcMs;
            this.endUtcMs = last.utcMs;
            this.durationMs = Math.max(0L, Math.round((last.monotonicNs - start.monotonicNs) / 1_000_000.0));
            this.timezoneOffsetMinutes = start.timezoneOffsetMinutes;
            this.orientation = orientation;
            this.endReason = endReason;
            this.counts = counts;
            this.timing = timing;
            this.derived = KeySenseDerivedMetrics.compute(counts, timing, this.durationMs);
        }
    }

    private static final class ActiveSession {
        final String id = UUID.randomUUID().toString();
        final EventTime start;
        EventTime last;
        final Orientation orientation;
        final KeySenseSessionAccumulator accumulator = new KeySenseSessionAccumulator();
        // Only validated typing events advance the last-event time and
        // inactivity timeout. Prediction selections never modify this flag.
        boolean lastActionWasTyping;

        ActiveSession(EventTime first, Orientation orientation) {
            start = first;
            last = first;
            this.orientation = orientation;
        }
    }

    private final long pauseThresholdNs;
    private final long inactivityTimeoutNs;
    private final Clock clock;
    private final ArrayDeque<CompletedSession> completed = new ArrayDeque<>();
    private ActiveSession active;

    /**
     * Thresholds are configurable: confirm final values against the iOS manager.
     * A gap >= pauseThresholdMs and < inactivityTimeoutMs is a pause; a gap
     * >= inactivityTimeoutMs starts a new session upon the next eligible input.
     */
    public KeySenseSessionManager(long pauseThresholdMs, long inactivityTimeoutMs, Clock clock) {
        if (pauseThresholdMs <= 0 || inactivityTimeoutMs <= pauseThresholdMs
                || inactivityTimeoutMs > Long.MAX_VALUE / 1_000_000L) {
            throw new IllegalArgumentException("Require 0 < pause threshold < inactivity timeout");
        }
        if (clock == null) throw new IllegalArgumentException("Clock must not be null");
        this.pauseThresholdNs = pauseThresholdMs * 1_000_000L;
        this.inactivityTimeoutNs = inactivityTimeoutMs * 1_000_000L;
        this.clock = clock;
    }

    /**
     * Process exactly one verified user typing event. Fail closed on ineligible
     * fields. This method neither receives nor derives original typed text.
     */
    public synchronized void onTypingEvent(KeySenseCharacterClassifier.EventKind kind,
            boolean eligible, Orientation orientation) {
        if (kind == null) throw new IllegalArgumentException("Event kind must not be null");
        if (kind == KeySenseCharacterClassifier.EventKind.EMOJI) {
            throw new IllegalArgumentException("Use onEmojiEvent() for emoji selections");
        }
        processValidatedInput(kind, null, eligible, orientation);
    }

    /** Native emoji identity is allowed in RAM only, never in completed records. */
    public synchronized void onEmojiEvent(String emojiIdentity,
            boolean eligible, Orientation orientation) {
        if (emojiIdentity == null || emojiIdentity.isEmpty()) {
            throw new IllegalArgumentException("Nonempty emoji identity required");
        }
        processValidatedInput(KeySenseCharacterClassifier.EventKind.EMOJI,
                emojiIdentity, eligible, orientation);
    }

    private void processValidatedInput(KeySenseCharacterClassifier.EventKind kind,
            String emojiIdentity, boolean eligible, Orientation orientation) {
        EventTime now = checkedTime();
        if (!eligible) {
            closeActive(EndReason.INPUT_CONTEXT_CHANGED);
            return;
        }
        Orientation selectedOrientation = orientation == null ? Orientation.UNKNOWN : orientation;
        if (active != null) {
            long gap = now.monotonicNs - active.last.monotonicNs;
            if (gap < 0) {
                // Clock source changed/reset: never emit a negative IKI.
                closeActive(EndReason.OTHER);
            } else if (gap >= inactivityTimeoutNs) {
                closeActive(EndReason.INACTIVITY_TIMEOUT);
            } else if (active.orientation != selectedOrientation) {
                closeActive(EndReason.ORIENTATION_CHANGED);
            } else if (active.lastActionWasTyping && gap >= pauseThresholdNs) {
                active.accumulator.registerPause(gap / 1_000_000.0);
            } else if (active.lastActionWasTyping && gap > 0) {
                active.accumulator.registerActiveIki(gap / 1_000_000.0);
            }
        }
        if (active == null) active = new ActiveSession(now, selectedOrientation);
        if (kind == KeySenseCharacterClassifier.EventKind.EMOJI) {
            active.accumulator.processEmoji(emojiIdentity);
        } else {
            active.accumulator.process(kind);
        }
        active.last = now;
        active.lastActionWasTyping = true;
    }

    /**
     * Count an explicit user-selected suggestion only inside an active typing
     * session. This auxiliary action must not start a session or change the
     * last typing-event timestamp, IKI, pauses, bursts or timeout deadline.
     * Auto-corrections and synthetic replacements must not reach this method.
     */
    public synchronized void onPredictionSelection(boolean eligible, Orientation orientation) {
        if (!eligible) {
            closeActive(EndReason.INPUT_CONTEXT_CHANGED);
            return;
        }
        // No prediction-only sessions: a typing event must already have started one.
        if (active == null) return;

        EventTime now = checkedTime();
        long gap = now.monotonicNs - active.last.monotonicNs;
        if (gap < 0) {
            closeActive(EndReason.OTHER);
            return;
        }
        if (gap >= inactivityTimeoutNs) {
            closeActive(EndReason.INACTIVITY_TIMEOUT);
            return;
        }
        Orientation selectedOrientation = orientation == null ? Orientation.UNKNOWN : orientation;
        if (active.orientation != selectedOrientation) {
            closeActive(EndReason.ORIENTATION_CHANGED);
            return;
        }
        active.accumulator.registerPredictionSelection();
        // Deliberately do NOT touch active.last, lastActionWasTyping, or timer.
    }

    /** Called by a real timeout callback; early/stale callbacks are harmless. */
    public synchronized void onInactivityTimeout() {
        if (active == null) return;
        EventTime now = checkedTime();
        if (now.monotonicNs < active.last.monotonicNs) {
            closeActive(EndReason.OTHER);
        } else if (now.monotonicNs - active.last.monotonicNs >= inactivityTimeoutNs) {
            closeActive(EndReason.INACTIVITY_TIMEOUT);
        }
    }

    /** Call only for a genuine keyboard dismissal, not repeated view show/hide cycles. */
    public synchronized void onKeyboardDismissed() { closeActive(EndReason.KEYBOARD_DISMISSED); }
    /** Call only for a genuine editor field change, not an IME restarting=true event. */
    public synchronized void onInputContextChanged() { closeActive(EndReason.INPUT_CONTEXT_CHANGED); }
    public synchronized void onAppContextChanged() { closeActive(EndReason.APP_CONTEXT_CHANGED); }
    public synchronized void onOrientationChanged() { closeActive(EndReason.ORIENTATION_CHANGED); }
    public synchronized void onEligibilityLost() { closeActive(EndReason.INPUT_CONTEXT_CHANGED); }
    public synchronized void onExtensionTerminated() { closeActive(EndReason.EXTENSION_TERMINATED); }

    public synchronized boolean hasActiveSession() { return active != null; }
    public synchronized int pendingCompletedCount() { return completed.size(); }

    /** Returns completed aggregate records once, clearing the RAM queue. */
    public synchronized List<CompletedSession> drainCompleted() {
        List<CompletedSession> result = new ArrayList<>(completed);
        completed.clear();
        return result;
    }

    private EventTime checkedTime() {
        EventTime time = clock.capture();
        if (time == null) throw new IllegalStateException("Clock returned null");
        return time;
    }

    private void closeActive(EndReason reason) {
        if (active == null) return;
        ActiveSession s = active;
        active = null;
        KeySenseSessionAccumulator.Counts counts = s.accumulator.finish();
        // Every completed session has at least one validated user action;
        // prediction-only sessions have zero typed characters and zero IKIs.
        completed.add(new CompletedSession(s.id, s.start, s.last, s.orientation,
                reason, counts, s.accumulator.timingSnapshot()));
    }
}

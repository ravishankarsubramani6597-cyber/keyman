package com.keyman.engine;

import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.List;

/**
 * DEBUG-ONLY, RAM-only integration of verified OSK actions with the session manager.
 *
 * Receives validated original OSK actions and temporary native emoji identity.
 * Emits aggregate-only diagnostics. No typed text, per-key codes, editor/app
 * identifiers, or emoji identities are logged or stored. Not a production collector.
 */
public final class KeySenseLiveDebug {
  private static final String TAG = "KeySenseLiveDebug";
  private static final long TIMEOUT_MS = 30_000L;
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final KeySenseSessionManager MANAGER =
      new KeySenseSessionManager(2_000L, TIMEOUT_MS, new KeySenseAndroidClock());

  // KEYSENSE v1: completed aggregates only. RAM-only debug handoff.
  // This repository is intentionally NOT durable storage or an uploader.
  private static final KeySenseDebugRepositoryV1 V1_DEBUG_REPOSITORY = new KeySenseDebugRepositoryV1();
  public static java.util.List<KeySenseTypingSessionV1> v1DebugSessionsSnapshot() {
    return V1_DEBUG_REPOSITORY.snapshot();
  }
  
  private static final Runnable TIMEOUT_CHECK = new Runnable() {
    @Override public void run() {
      MANAGER.onInactivityTimeout();
      logCompleted();
    }
  };

  private KeySenseLiveDebug() { }

  // The service supplies display orientation when it is available; an UNKNOWN
  // observation never overwrites a previously verified orientation.
  private static KeySenseSessionManager.Orientation currentOrientation =
      KeySenseSessionManager.Orientation.UNKNOWN;

  /**
   * Called by SystemKeyboard when it observes a display-orientation change.
   * A real change is an immediate session boundary, even without a subsequent
   * typing event. Repeated layout/insets callbacks with the same value are safe.
   */
  public static void onOrientationObserved(KeySenseSessionManager.Orientation next) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      throw new IllegalStateException("KEYSENSE orientation requires main thread");
    }
    if (next == null || next == KeySenseSessionManager.Orientation.UNKNOWN) {
      return; // Ignore untrusted/transient readings instead of inventing a turn.
    }
    if (next == currentOrientation) return;

    KeySenseSessionManager.Orientation old = currentOrientation;
    currentOrientation = next;
    if (old != KeySenseSessionManager.Orientation.UNKNOWN) {
      MAIN.removeCallbacks(TIMEOUT_CHECK);
      MANAGER.onOrientationChanged();
      logCompleted();
    }
    Log.d(TAG, "[KEYSENSE ORIENTATION] current="
        + KeySenseAndroidOrientation.toSchemaValue(next));
  }


  /**
   * KEYSENSE DEBUG:
   * Close an active session when Android reports
   * that the current input has finished.
   */
  public static void onInputFinished() {
      if (Looper.myLooper() != Looper.getMainLooper()) {
          throw new IllegalStateException(
              "KEYSENSE requires the main thread");
      }

      MAIN.removeCallbacks(TIMEOUT_CHECK);

      MANAGER.onInputContextChanged();
      logCompleted();
  }

  /**
   * Native emoji-panel callback. The emoji string exists only transiently in
   * RAM; it is never logged, persisted, or included in completed sessions.
   */
  public static void onValidatedEmoji(String emojiIdentity) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      throw new IllegalStateException("KEYSENSE debug integration requires main thread");
    }
    if (emojiIdentity == null || emojiIdentity.isEmpty()) return;

    if (KMManager.IMService == null ||
        (KMManager.IMService.getApplicationInfo().flags
            & ApplicationInfo.FLAG_DEBUGGABLE) == 0 ||
        !KeySenseInputEligibility.isEligible(
            KMManager.IMService.getCurrentInputEditorInfo())) {
      MAIN.removeCallbacks(TIMEOUT_CHECK);
      MANAGER.onEligibilityLost();
      logCompleted();
      return;
    }

    MANAGER.onEmojiEvent(emojiIdentity, true, currentOrientation);
    logCompleted();
    MAIN.removeCallbacks(TIMEOUT_CHECK);
    if (MANAGER.hasActiveSession()) {
      MAIN.postDelayed(TIMEOUT_CHECK, TIMEOUT_MS);
    }
  }

  /** Call on the main thread, after the JS bridge's debug and privacy checks. */
  public static void onValidatedCategory(String category) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      throw new IllegalStateException("KEYSENSE debug integration requires main thread");
    }

    boolean isPredictionSelection =
    "PREDICTION_SELECTION".equals(category);

    KeySenseCharacterClassifier.EventKind kind = mapCategory(category);

    if (kind == null && !isPredictionSelection) {
        return;
    }

    // Recheck privacy at the final collection boundary, not only in the JS handler.
    if (KMManager.IMService == null ||
        (KMManager.IMService.getApplicationInfo().flags
            & ApplicationInfo.FLAG_DEBUGGABLE) == 0 ||
        !KeySenseInputEligibility.isEligible(
            KMManager.IMService.getCurrentInputEditorInfo())) {
      MAIN.removeCallbacks(TIMEOUT_CHECK);
      MANAGER.onEligibilityLost();
      logCompleted();
      return;
    }

    if (isPredictionSelection) {
        MANAGER.onPredictionSelection(true, currentOrientation);

        logCompleted();
        // A prediction selection does not reset the inactivity deadline.
        // Preserve the already scheduled timeout callback unchanged.
        return;
    }

    MANAGER.onTypingEvent(kind, true, currentOrientation);
    logCompleted();

    MAIN.removeCallbacks(TIMEOUT_CHECK);
    MAIN.postDelayed(TIMEOUT_CHECK, TIMEOUT_MS);
  }

  private static KeySenseCharacterClassifier.EventKind mapCategory(String category) {
    if (category == null) return null;
    switch (category) {
      case "LETTER_BASE_STATE":
        return KeySenseCharacterClassifier.EventKind.LOWERCASE;
      case "LETTER_SHIFT_STATE":
      case "LETTER_CAPS_STATE":
        return KeySenseCharacterClassifier.EventKind.UPPERCASE;
      case "SPACE":
        return KeySenseCharacterClassifier.EventKind.SPACE;
      case "BACKSPACE":
        return KeySenseCharacterClassifier.EventKind.BACKSPACE;
      case "NUMBER":
        return KeySenseCharacterClassifier.EventKind.NUMBER;
      case "SPECIAL_WORD_INTERNAL":
        return KeySenseCharacterClassifier.EventKind.SPECIAL_WORD_INTERNAL;
      case "SPECIAL_OTHER":
        return KeySenseCharacterClassifier.EventKind.SPECIAL_OTHER;
      case "NEWLINE":
        return KeySenseCharacterClassifier.EventKind.NEWLINE;
      // Identity-free EMOJI categories must not bypass onValidatedEmoji(),
      // since they cannot provide accurate diversity and repetition metrics.
      default:
        // E.g. SHIFT+CAPS, emoji, custom layout output, and prediction
        // selection intentionally remain uncounted pending validation.
        return null;
    }
  }

  private static void logCompleted() {
    List<KeySenseSessionManager.CompletedSession> finished = MANAGER.drainCompleted();
    for (KeySenseSessionManager.CompletedSession s : finished) {
      try {
        KeySenseTypingSessionV1 record = KeySenseTypingSessionV1.fromCompleted(
            s, "KEYSENSE_ANDROID_DEBUG", "KEYMAN_ANDROID_DEBUG");
        V1_DEBUG_REPOSITORY.add(record);
        // This diagnostic contains no typed content or emoji identity.
        Log.d(TAG, "[KEYSENSE V1] finalized record queued"
            + " schema=" + record.schemaVersion
            + " count=" + V1_DEBUG_REPOSITORY.size());
      } catch (IllegalArgumentException | IllegalStateException error) {
        // A malformed debug record must never interrupt the IME.
        Log.w(TAG, "[KEYSENSE V1] invalid finalized record", error);
      }
      Log.d(TAG, "[KEYSENSE LIVE] sessionClosed"
          + " reason=" + s.endReason.serializedValue
          + " orientation=" + KeySenseAndroidOrientation.toSchemaValue(s.orientation)
          + " durationMs=" + s.durationMs
          + " lowercase=" + s.counts.lowercaseCount
          + " uppercase=" + s.counts.uppercaseCount
          + " space=" + s.counts.spaceCount
          + " backspace=" + s.counts.backspaceCount
          + " characterCount=" + s.counts.characterCount
          + " totalKeyCount=" + s.counts.totalKeyCount
          + " activeIkiCount=" + s.timing.activeIkiCount
          + " pauses=" + s.timing.pauseCount
          + " bursts=" + s.timing.typingBurstCount
          + " numeric=" + s.counts.numericCount
          + " special=" + s.counts.specialCharCount
          + " newline=" + s.counts.newlineCount
          + " words=" + s.counts.wordCount
          + " predictions=" + s.counts.predictionSelectionCount
          + " emoji=" + s.counts.emojiCount
          + " uniqueEmojiCount=" + s.counts.uniqueEmojiCount
          + " emojiBurstCount=" + s.counts.emojiBurstCount
          + " maxEmojiRunLength=" + s.counts.maxEmojiRunLength
          + " repeatedEmojiCount=" + s.counts.repeatedEmojiCount
          );
      // Separate diagnostic line to avoid making sessionClosed excessively long.
      // Null means undefined, not a measured zero.
      Log.d(TAG, "[KEYSENSE METRICS]"
          + " deletionRate=" + s.derived.deletionRate
          + " charactersPerWord=" + s.derived.charactersPerWord
          + " newlinesPerWord=" + s.derived.newlinesPerWord
          + " keysPerSecond=" + s.derived.keysPerSecond
          + " charactersPerSecond=" + s.derived.charactersPerSecond
          + " wordsPerMinute=" + s.derived.wordsPerMinute
          + " activeTypingDurationMs=" + s.derived.activeTypingDurationMs
          + " activeTypingRate=" + s.derived.activeTypingRate
          + " meanPauseDurationMs=" + s.derived.meanPauseDurationMs
          + " pauseTimeRatio=" + s.derived.pauseTimeRatio
          + " meanBurstLength=" + s.derived.meanBurstLength
          + " typingIntervalCv=" + s.derived.typingIntervalCv
          + " emojiRate=" + s.derived.emojiRate
          + " emojiDiversityRatio=" + s.derived.emojiDiversityRatio);
    }
  }
}

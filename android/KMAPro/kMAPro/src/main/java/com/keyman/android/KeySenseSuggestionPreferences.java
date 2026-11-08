package com.keyman.android;

import android.content.Context;
import android.content.SharedPreferences;
import com.keyman.engine.KMManager;
import com.keyman.engine.KMManager.KeyboardType;
import com.keyman.engine.KMManager.SuggestionType;
import com.keyman.engine.R;
import com.keyman.engine.data.Keyboard;
import java.util.HashMap;

/**
 * KEYSENSE's three-option wrapper around Keyman's native language-model modes.
 * No typing text, predictions, or keyboard events are collected in this class.
 *
 * Mode values follow KMManager.SuggestionType:
 *   0 = disabled; 2 = correction suggestions; 3 = autocorrection.
 * Keyman's engine uses the KMAPreferences file to configure the lexical model.
 */
public final class KeySenseSuggestionPreferences {
  public static final int DISABLED = 0;
  public static final int CORRECTION_SUGGESTIONS = 2;
  public static final int AUTO_CORRECTION = 3;
  public static final int DEFAULT = CORRECTION_SUGGESTIONS;

  // KMManager.KMEngine_PrefsKey is private in the Keyman version used by KEYSENSE.
  // Keep the value aligned with KMManager.java: KMEngine_PrefsKey = "KMAPreferences".
  private static final String ENGINE_PREFS_NAME = "KMAPreferences";
  private static final String LEGACY_INITIALIZED_PREFIX =
      "keysense_typing_assistance_v1_initialized_";
  private static final String MIGRATED_PREFIX =
      "keysense_typing_assistance_v2_engine_initialized_";

  private KeySenseSuggestionPreferences() { }

  private static SharedPreferences enginePreferences(Context context) {
    return context.getApplicationContext().getSharedPreferences(
        ENGINE_PREFS_NAME, Context.MODE_PRIVATE);
  }

  /** Previous KEYSENSE UI and SystemKeyboard.onStartInput() use this resource. */
  private static SharedPreferences uiPreferences(Context context) {
    Context app = context.getApplicationContext();
    return app.getSharedPreferences(
        app.getString(R.string.kma_prefs_name), Context.MODE_PRIVATE);
  }

  private static String languageId(Context context) {
    Keyboard kb = KMManager.getCurrentKeyboardInfo(context.getApplicationContext());
    String id = kb == null ? null : kb.getLanguageID();
    return id == null || id.trim().isEmpty() ? "en" : id;
  }

  private static String modeKey(String languageId) {
    return KMManager.getLanguageAutoCorrectionPreferenceKey(languageId);
  }

  private static boolean isSupported(int value) {
    return value == DISABLED || value == CORRECTION_SUGGESTIONS
        || value == AUTO_CORRECTION;
  }

  /**
   * Migrate the mode already selected in the previous KEYSENSE Settings UI.
   * If no selection was made, initialize to correction suggestions (mode 2).
   * Only migrate once: later changes made in the original Keyman app should
   * remain visible to KEYSENSE and must not be overwritten on each launch.
   * Must be called after KMManager.initialize(), as it uses setMaySuggest().
   */
  public static synchronized void ensureDefault(Context context) {
    String language = languageId(context);
    SharedPreferences engine = enginePreferences(context);
    String marker = MIGRATED_PREFIX + language;
    if (engine.getBoolean(marker, false)) return;

    SharedPreferences priorUi = uiPreferences(context);
    String key = modeKey(language);
    int priorMode = priorUi.getInt(key, DEFAULT);
    boolean hadPriorSelection = priorUi.getBoolean(
        LEGACY_INITIALIZED_PREFIX + language, false);
    int selected = hadPriorSelection && isSupported(priorMode)
        ? priorMode : DEFAULT;

    KMManager.setMaySuggest(language, SuggestionType.fromInt(selected));
    // Retain compatibility with the existing SystemKeyboard.onStartInput().
    priorUi.edit().putInt(key, selected).apply();
    engine.edit().putBoolean(marker, true).apply();
  }

  /** Reads the actual Keyman engine preference, not just the highlighted UI mode. */
  public static synchronized int getMode(Context context) {
    ensureDefault(context);
    String language = languageId(context);
    String key = modeKey(language);
    int value = enginePreferences(context).getInt(key, DEFAULT);
    int selected = isSupported(value) ? value : DEFAULT;

    // Keep old SystemKeyboard's banner-visibility check synchronized even if
    // the user also changes this preference in the original Keyman host app.
    SharedPreferences ui = uiPreferences(context);
    if (ui.getInt(key, -1) != selected) {
      ui.edit().putInt(key, selected).apply();
    }
    return selected;
  }

  /** Save and apply all three modes, not just the banner's on/off visibility. */
  public static synchronized void setMode(Context context, int mode) {
    if (!isSupported(mode)) {
      throw new IllegalArgumentException("Unsupported KEYSENSE suggestion mode");
    }
    ensureDefault(context);
    String language = languageId(context);

    // Keyman's own method writes into the engine's canonical KMAPreferences.
    KMManager.setMaySuggest(language, SuggestionType.fromInt(mode));
    uiPreferences(context).edit().putInt(modeKey(language), mode).apply();

    boolean enabled = mode != DISABLED &&
        !KMManager.getPredictionsSuspended(KeyboardType.KEYBOARD_TYPE_SYSTEM);
    KMManager.setBannerOptions(enabled, KeyboardType.KEYBOARD_TYPE_SYSTEM);

    // Unlike setBannerOptions(boolean), registerLexicalModel supplies the full
    // mode (0, 2, or 3) to KeymanWeb via enableSuggestions(model, mode).
    // Do not call registerAssociatedLexicalModel(): it can skip an already
    // registered model even when its configuration has changed.
    HashMap<String, String> model = KMManager.getAssociatedLexicalModel(language);
    if (model != null) {
      KMManager.registerLexicalModel(model);
    }
  }
}

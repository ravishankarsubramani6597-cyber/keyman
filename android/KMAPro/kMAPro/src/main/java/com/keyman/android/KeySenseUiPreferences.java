package com.keyman.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** UI preferences only: never collects typing content or events. */
public final class KeySenseUiPreferences {
  private static final String PREFS = "keysense_keyboard_ui";
  private static final String APPEARANCE = "appearance";
  public static final String SYSTEM = "system";
  public static final String LIGHT = "light";
  public static final String DARK = "dark";

  private KeySenseUiPreferences() { }

  public static String appearance(Context context) {
    String value = prefs(context).getString(APPEARANCE, SYSTEM);
    return LIGHT.equals(value) || DARK.equals(value) ? value : SYSTEM;
  }

  public static void setAppearance(Context context, String value) {
    if (!SYSTEM.equals(value) && !LIGHT.equals(value) && !DARK.equals(value)) {
      throw new IllegalArgumentException("Invalid appearance preference");
    }
    prefs(context).edit().putString(APPEARANCE, value).apply();
  }

  public static boolean isDarkMode(Context context) {
    String choice = appearance(context);
    if (DARK.equals(choice)) return true;
    if (LIGHT.equals(choice)) return false;
    return (context.getResources().getConfiguration().uiMode
      & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
  }

  private static SharedPreferences prefs(Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
}

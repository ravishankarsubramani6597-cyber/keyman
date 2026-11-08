package com.keyman.engine;

import android.content.res.Configuration;
import android.view.Surface;

/**
 * KEYSENSE: Convert Android's current display orientation and rotation into
 * canonical study categories. Never infer a physical orientation from an
 * unsupported or inconsistent display configuration.
 *
 * The four specific mappings below apply to portrait-natural phones, as used
 * in the Galaxy/Pixel regression tests. For landscape-natural devices and
 * foldables where these assumptions do not hold, return UNKNOWN until the
 * platform-specific mapping has been separately validated.
 */
public final class KeySenseAndroidOrientation {
  private KeySenseAndroidOrientation() { }

  public static KeySenseSessionManager.Orientation fromConfigurationAndRotation(
      int configurationOrientation, int displayRotation) {
    if (configurationOrientation == Configuration.ORIENTATION_PORTRAIT) {
      if (displayRotation == Surface.ROTATION_0) {
        return KeySenseSessionManager.Orientation.PORTRAIT;
      }
      if (displayRotation == Surface.ROTATION_180) {
        return KeySenseSessionManager.Orientation.PORTRAIT_UPSIDE_DOWN;
      }
    } else if (configurationOrientation == Configuration.ORIENTATION_LANDSCAPE) {
      // Display rotation compensates for physical device rotation.
      // A counter-clockwise physical turn yields display ROTATION_90.
      if (displayRotation == Surface.ROTATION_90) {
        return KeySenseSessionManager.Orientation.LANDSCAPE_LEFT;
      }
      if (displayRotation == Surface.ROTATION_270) {
        return KeySenseSessionManager.Orientation.LANDSCAPE_RIGHT;
      }
    }
    return KeySenseSessionManager.Orientation.UNKNOWN;
  }

  public static String toSchemaValue(KeySenseSessionManager.Orientation orientation) {
    if (orientation == null) return "unknown";
    switch (orientation) {
      case PORTRAIT: return "portrait";
      case PORTRAIT_UPSIDE_DOWN: return "portraitUpsideDown";
      case LANDSCAPE_LEFT: return "landscapeLeft";
      case LANDSCAPE_RIGHT: return "landscapeRight";
      default: return "unknown";
    }
  }
}

package com.keyman.engine;

/** Implemented by the Android IME host. UI-only actions carry no typed content. */
public interface KeySenseUiActionTarget {
  void onKeySenseUiAction(String action);
}

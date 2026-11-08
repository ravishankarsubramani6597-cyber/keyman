package com.keyman.android;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Private process-memory MRU cache for display only; never writes to disk or logs identities. */
public final class KeySenseEmojiHistory {
  private static final int MAX_RECENT = 30;
  private final ArrayDeque<String> recent = new ArrayDeque<>();

  public synchronized void record(String emoji) {
    if (emoji == null || emoji.isEmpty()) return;
    recent.remove(emoji);
    recent.addFirst(emoji);
    while (recent.size() > MAX_RECENT) recent.removeLast();
  }

  public synchronized List<String> snapshot() { return new ArrayList<>(recent); }
  public synchronized void clear() { recent.clear(); }
}

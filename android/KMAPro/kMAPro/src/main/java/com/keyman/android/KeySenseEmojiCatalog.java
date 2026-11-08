package com.keyman.android;

import android.content.Context;
import android.graphics.Paint;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime-filtered, display-only emoji catalog. Never log or persist selections. */
public final class KeySenseEmojiCatalog {
  public static final String ASSET_PATH = "keysense/EmojiCatalog.json";
  private final LinkedHashMap<String, List<String>> byGroup = new LinkedHashMap<>();
  private final LinkedHashMap<String, LinkedHashMap<String, List<String>>> bySubgroup = new LinkedHashMap<>();
  private int accepted;
  private int excluded;

  private KeySenseEmojiCatalog() { }

  public static KeySenseEmojiCatalog load(Context context) throws IOException, JSONException {
    KeySenseEmojiCatalog catalog = new KeySenseEmojiCatalog();
    final String json;
    try (InputStream stream = context.getAssets().open(ASSET_PATH)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int n;
      while ((n = stream.read(chunk)) != -1) out.write(chunk, 0, n);
      json = out.toString("UTF-8");
    }
    // The iOS catalog uses records with {value, group, subgroup}.
    // Accept a root array or an object containing an array under "emojis" or "entries".
    String trimmed = json.trim();
    final JSONArray entries;
    if (trimmed.startsWith("[")) {
      entries = new JSONArray(trimmed);
    } else {
      JSONObject root = new JSONObject(trimmed);
      entries = root.optJSONArray("emojis") != null ? root.optJSONArray("emojis") : root.optJSONArray("entries");
      if (entries == null) throw new JSONException("Expected 'emojis' or 'entries' array");
    }
    Paint glyphProbe = new Paint();
    glyphProbe.setTextSize(32f * context.getResources().getDisplayMetrics().scaledDensity);
    for (int i = 0; i < entries.length(); i++) {
      JSONObject item = entries.optJSONObject(i);
      if (item == null) continue;
      String emoji = item.optString("value", "");
      String group = item.optString("group", "");
      if (emoji.isEmpty() || group.isEmpty()) continue;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !glyphProbe.hasGlyph(emoji)) {
        catalog.excluded++;
        continue;
      }
      List<String> list = catalog.byGroup.get(group);
      if (list == null) {
        list = new ArrayList<>();
        catalog.byGroup.put(group, list);
      }
      list.add(emoji);
      String subgroup = item.optString("subgroup", "Other");
      LinkedHashMap<String, List<String>> subgroups = catalog.bySubgroup.get(group);
      if (subgroups == null) {
        subgroups = new LinkedHashMap<>();
        catalog.bySubgroup.put(group, subgroups);
      }
      List<String> subgroupItems = subgroups.get(subgroup);
      if (subgroupItems == null) {
        subgroupItems = new ArrayList<>();
        subgroups.put(subgroup, subgroupItems);
      }
      subgroupItems.add(emoji);
      catalog.accepted++;
    }
    if (catalog.accepted == 0) throw new JSONException("Emoji catalog parsed but no usable records found");
    return catalog;
  }

  public List<String> groups() { return Collections.unmodifiableList(new ArrayList<>(byGroup.keySet())); }
  public List<String> emojiInGroup(String group) {
    List<String> value = byGroup.get(group);
    return value == null ? Collections.<String>emptyList() : Collections.unmodifiableList(value);
  }
  public List<String> subgroups(String group) {
    Map<String, List<String>> subgroups = bySubgroup.get(group);
    return subgroups == null ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<>(subgroups.keySet()));
  }
  public List<String> emojiInSubgroup(String group, String subgroup) {
    Map<String, List<String>> subgroups = bySubgroup.get(group);
    if (subgroups == null) return Collections.emptyList();
    List<String> value = subgroups.get(subgroup);
    return value == null ? Collections.<String>emptyList() : Collections.unmodifiableList(value);
  }
  public int acceptedCount() { return accepted; }
  public int excludedCount() { return excluded; }
}

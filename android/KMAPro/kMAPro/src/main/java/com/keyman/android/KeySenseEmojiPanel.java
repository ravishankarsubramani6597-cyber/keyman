package com.keyman.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** A native panel, separate from Keyman's WebView so existing OSK instrumentation stays untouched. */
public final class KeySenseEmojiPanel extends LinearLayout {
  public interface OnEmojiTap { void onEmojiTapped(String emoji); }
  private static final String RECENT = "\u23f1 Recent";
  private final KeySenseEmojiCatalog catalog;
  private final KeySenseEmojiHistory history;
  private final LinearLayout emojiSections;
  private final OnEmojiTap emojiTap;

  private final LinearLayout bottomControls;
  private Runnable onABCRequested;
  private Runnable onSpaceRequested;
  private Runnable onBackspaceRequested;
  private String currentGroup = "";
  private LinearLayout categoryRow;

  public KeySenseEmojiPanel(Context context, KeySenseEmojiCatalog catalog,
      KeySenseEmojiHistory history, OnEmojiTap callback) {
    super(context);
    this.catalog = catalog;
    this.history = history;
    this.emojiTap = callback;
    setOrientation(VERTICAL);
    setBackgroundColor(Color.rgb(238, 240, 244));

    ScrollView emojiScroll = new ScrollView(context);
    emojiScroll.setFillViewport(false);
    emojiScroll.setVerticalScrollBarEnabled(false);

    emojiSections = new LinearLayout(context);
    emojiSections.setOrientation(VERTICAL);
    emojiSections.setPadding(dp(6), dp(4), dp(6), dp(4));

    emojiScroll.addView(emojiSections);

    addView(emojiScroll,
        new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

    HorizontalScrollView scroll = new HorizontalScrollView(context);
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setFillViewport(true);

    categoryRow = new LinearLayout(context);
    categoryRow.setOrientation(HORIZONTAL);

    addCategoryButton(categoryRow, "\u23f1", "Recent emojis", RECENT);

    for (String group : catalog.groups()) {
        addCategoryButton(categoryRow, iconForGroup(group), group, group);
    }

    scroll.addView(categoryRow);

    // Distribute icons evenly across the available width.
    scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
        int count = categoryRow.getChildCount();
        if (count == 0) return;

        int tabWidth = Math.max(dp(44), (r - l) / count);

        for (int i = 0; i < count; i++) {
            View tab = categoryRow.getChildAt(i);
            LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) tab.getLayoutParams();

            if (params.width != tabWidth) {
                params.width = tabWidth;
                tab.setLayoutParams(params);
            }
        }
    });

  addView(scroll, 0,
    new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
    LinearLayout bottom = new LinearLayout(context);

    bottom.setOrientation(HORIZONTAL);
    bottom.setGravity(Gravity.CENTER_VERTICAL);
    bottom.setPadding(dp(7), dp(2), dp(7), dp(2));

    Button abc = new Button(context);
    abc.setText("ABC");
    abc.setAllCaps(false);
    abc.setOnClickListener(v -> {
        if (onABCRequested != null) onABCRequested.run();
    });
    bottom.addView(abc, new LayoutParams(dp(72), dp(42)));

    Button space = new Button(context);
    space.setText("");
    space.setContentDescription("Space");
    space.setAllCaps(false);
    space.setOnClickListener(v -> {
        if (onSpaceRequested != null) onSpaceRequested.run();
    });

    LayoutParams spaceParams = new LayoutParams(0, dp(42), 1f);
    spaceParams.leftMargin = dp(7);
    spaceParams.rightMargin = dp(7);
    bottom.addView(space, spaceParams);

    Button backspace = new Button(context);
    backspace.setText("⌫");
    backspace.setOnClickListener(v -> {
        if (onBackspaceRequested != null) onBackspaceRequested.run();
    });
    bottom.addView(backspace, new LayoutParams(dp(72), dp(42)));

    addView(bottom, new LayoutParams(LayoutParams.MATCH_PARENT, dp(50)));
    bottomControls = bottom;
    if (!catalog.groups().isEmpty()) showGroup(catalog.groups().get(0));
    refreshAppearance();
  }

  public void setOnABCRequested(Runnable callback) {
    onABCRequested = callback;
  }

  public void setOnSpaceRequested(Runnable callback) {
      onSpaceRequested = callback;
  }

  public void setOnBackspaceRequested(Runnable callback) {
      onBackspaceRequested = callback;
  }

  public void refreshRecent() { if (RECENT.equals(currentGroup)) showGroup(RECENT); }

  /** Apply the same persisted System/Light/Dark preference used by Settings. */
  public void refreshAppearance() {
    boolean dark = KeySenseUiPreferences.isDarkMode(getContext());
    int background = dark ? Color.rgb(30, 30, 34) : Color.rgb(238, 240, 244);
    int foreground = dark ? Color.WHITE : Color.rgb(25, 27, 30);
    int buttonBackground = dark ? Color.rgb(66, 67, 73) : Color.rgb(216, 221, 229);
    setBackgroundColor(background);
    bottomControls.setBackgroundColor(
    dark ? Color.rgb(27, 27, 29) : Color.rgb(226, 230, 236)
    );
    applyChildColors(this, background, foreground, buttonBackground);
    refreshCategoryTabs();
  }

  private static void applyChildColors(View view, int background, int foreground,
                                       int buttonBackground) {
    if (view instanceof Button) {
      Button button = (Button) view;
      button.setTextColor(foreground);
      button.setBackgroundTintList(ColorStateList.valueOf(buttonBackground));
    } else if (view instanceof TextView) {
    ((TextView) view).setTextColor(foreground);
    } else if (view instanceof ScrollView || view instanceof HorizontalScrollView) {
        view.setBackgroundColor(background);
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        applyChildColors(group.getChildAt(i), background, foreground, buttonBackground);
      }
    }
  }

  private void styleButton(Button button) {
    boolean dark = KeySenseUiPreferences.isDarkMode(getContext());
    button.setTextColor(dark ? Color.WHITE : Color.rgb(25, 27, 30));
    button.setBackgroundTintList(ColorStateList.valueOf(
        dark ? Color.rgb(66, 67, 73) : Color.rgb(216, 221, 229)));
  }

  private void addCategoryButton(
        LinearLayout row, String label, String accessibility, String group) {

    TextView tab = new TextView(getContext());

    tab.setText(label);
    tab.setTextSize(22);
    tab.setGravity(Gravity.CENTER);
    tab.setContentDescription(accessibility);
    tab.setFocusable(false);
    tab.setTag(group);
    tab.setOnClickListener(v -> showGroup(group));

    row.addView(tab, new LayoutParams(dp(52), dp(46)));
  }

  private void refreshCategoryTabs() {
      if (categoryRow == null) return;

      boolean dark = KeySenseUiPreferences.isDarkMode(getContext());

      for (int i = 0; i < categoryRow.getChildCount(); i++) {
          TextView tab = (TextView) categoryRow.getChildAt(i);

          boolean selected = currentGroup.equals(tab.getTag());

          tab.setTextColor(dark ? Color.WHITE : Color.BLACK);

          android.graphics.drawable.GradientDrawable background =
              new android.graphics.drawable.GradientDrawable();

          background.setColor(
              selected
                  ? (dark ? Color.rgb(72, 73, 80) : Color.rgb(216, 221, 229))
                  : Color.TRANSPARENT
          );

          background.setCornerRadius(dp(12));
          tab.setBackground(background);
      }
  }

  private void showGroup(String group) {
    currentGroup = group;
    refreshCategoryTabs();
    emojiSections.removeAllViews();

    if (RECENT.equals(group)) {
        addEmojiSection("Recent", history.snapshot());
    } else {
        for (String subgroup : catalog.subgroups(group)) {
            addEmojiSection(
                readableSubgroup(subgroup),
                catalog.emojiInSubgroup(group, subgroup)
            );
        }
    }
  }

  private void addEmojiSection(String title, List<String> emojis) {
      if (emojis.isEmpty()) return;

      TextView heading = new TextView(getContext());
      heading.setText(title);
      heading.setTextSize(14);
      heading.setTypeface(null, android.graphics.Typeface.BOLD);
      heading.setPadding(dp(8), dp(10), 0, dp(6));
      heading.setTextColor(
          KeySenseUiPreferences.isDarkMode(getContext())
              ? Color.WHITE : Color.rgb(25, 27, 30)
      );

      emojiSections.addView(heading);

      for (int start = 0; start < emojis.size(); start += 8) {
          LinearLayout row = new LinearLayout(getContext());
          row.setOrientation(HORIZONTAL);

          for (int col = 0; col < 8; col++) {
              int index = start + col;

              TextView cell = new TextView(getContext());
              cell.setGravity(Gravity.CENTER);
              cell.setTextSize(27);

              if (index < emojis.size()) {
                  String emoji = emojis.get(index);
                  cell.setText(emoji);
                  cell.setOnClickListener(
                      v -> emojiTap.onEmojiTapped(emoji)
                  );
              }

              row.addView(cell,
                  new LayoutParams(0, dp(48), 1f));
          }

          emojiSections.addView(row,
              new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
      }
  }

  private String readableSubgroup(String subgroup) {
      String[] words = subgroup.replace('-', ' ').split(" ");
      StringBuilder result = new StringBuilder();

      for (String word : words) {
          if (word.isEmpty()) continue;
          if (result.length() > 0) result.append(" ");
          result.append(Character.toUpperCase(word.charAt(0)));
          result.append(word.substring(1));
      }

      return result.toString();
  }

  private static String iconForGroup(String name) {
    if (name.startsWith("Smileys")) return "\ud83d\ude00";
    if (name.startsWith("People")) return "\ud83d\udc4b";
    if (name.startsWith("Animals")) return "\ud83d\udc3b";
    if (name.startsWith("Food")) return "\ud83c\udf4e";
    if (name.startsWith("Travel")) return "\ud83d\ude97";
    if (name.startsWith("Activities")) return "\u26bd";
    if (name.startsWith("Objects")) return "\ud83d\udca1";
    if (name.startsWith("Symbols")) return "\u2764\ufe0f";
    if (name.startsWith("Flags")) return "\ud83c\udff3\ufe0f";
    return "\u2022";
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}

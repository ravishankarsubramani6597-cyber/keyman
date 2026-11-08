package com.keyman.android;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.keyman.engine.KMManager;

/** Settings panel modelled on the iOS KEYSENSE settings controls. */
public final class KeySenseSettingsPanel extends LinearLayout {
  public interface OnClose { void onClose(int keyboardHeightDp); }

  private final Context context;
  private final OnClose onClose;
  private final SeekBar slider;
  private final Button[] appearanceButtons = new Button[3];
  private final Button[] suggestionButtons = new Button[3];
  private final Button abcButton;
  private final Button resetButton;
  private final LinearLayout heightCard;
  private final LinearLayout appearanceCard;
  private final LinearLayout assistanceCard;
  private final int minDp;
  private final int maxDp;
  private int pendingHeightDp;

  public KeySenseSettingsPanel(Context context, OnClose onClose) {
    super(context);
    this.context = context;
    this.onClose = onClose;
    setOrientation(VERTICAL);
    setFocusable(false);

    int originalMin = KMManager.getKeyboardHeightMin(context);

    boolean landscape =
        context.getResources().getConfiguration().orientation
        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

    minDp = landscape
        ? Math.min(KMManager.getDefaultKeyboardHeight(context), originalMin + 30)
        : originalMin;

    maxDp = Math.max(minDp + 1, KMManager.getKeyboardHeightMax(context));

    pendingHeightDp = clamp(KMManager.getKeyboardHeight(context));

    LinearLayout header = new LinearLayout(context);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(8), dp(4), dp(12), dp(4));
    addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, dp(50)));
    
    abcButton = button("ABC");
    abcButton.setContentDescription("Return to alphabet keyboard");
    abcButton.setOnClickListener(v -> onClose.onClose(pendingHeightDp));
    header.addView(abcButton, new LayoutParams(dp(68), dp(42)));

    TextView title = label("Keyboard Settings", 17, true);
    title.setGravity(Gravity.CENTER);
    header.addView(title, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    View spacer = new View(context);
    header.addView(spacer, new LayoutParams(dp(68), 1));

    ScrollView scroll = new ScrollView(context);
    scroll.setFillViewport(false);
    addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    LinearLayout content = new LinearLayout(context);
    content.setOrientation(VERTICAL);
    content.setPadding(dp(12), dp(10), dp(12), dp(12));
    scroll.addView(content);

    heightCard = card();
    content.addView(heightCard, cardParams());
    heightCard.addView(label("Keyboard Height", 16, true));
    heightCard.addView(label("Choose a comfortable height. Changes apply when you return to ABC.", 12, false));
    slider = new SeekBar(context);
    slider.setMax(maxDp - minDp);
    slider.setProgress(pendingHeightDp - minDp);
    slider.setContentDescription("Keyboard height");
    slider.setEnabled(true);

    slider.setOnTouchListener((view, event) -> {
      if (event.getActionMasked()
        == android.view.MotionEvent.ACTION_DOWN) {
        view.getParent().requestDisallowInterceptTouchEvent(true);
      }
      return false;
    });
    heightCard.addView(slider, new LayoutParams(LayoutParams.MATCH_PARENT, dp(42)));
    slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        if (fromUser) pendingHeightDp = minDp + progress;
      }
      @Override public void onStartTrackingTouch(SeekBar bar) { }
      @Override public void onStopTrackingTouch(SeekBar bar) { }
    });
    LinearLayout extremes = new LinearLayout(context);
    TextView small = label("Small", 11, false);
    TextView large = label("Large", 11, false);
    large.setGravity(Gravity.RIGHT);
    extremes.addView(small, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    extremes.addView(large, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    heightCard.addView(extremes);
    
    resetButton = button("Reset to Default");
    resetButton.setContentDescription("Reset keyboard height to default");

    resetButton.setOnClickListener(v -> {
        pendingHeightDp = KMManager.KeyboardHeight_Reset;
        slider.setProgress(
            clamp(KMManager.getDefaultKeyboardHeight(context)) - minDp
        );
    });

    LinearLayout resetRow = new LinearLayout(context);
    resetRow.setGravity(Gravity.CENTER);
    resetRow.addView(
        resetButton,
        new LayoutParams(dp(185), dp(45))
    );
    heightCard.addView(resetRow);

    appearanceCard = card();
    content.addView(appearanceCard, cardParams());
    appearanceCard.addView(label("Appearance", 16, true));
    appearanceCard.addView(label("Choose how the keyboard should appear.", 12, false));
    LinearLayout options = new LinearLayout(context);
    String[] names = {"System", "Light", "Dark"};
    String[] values = {KeySenseUiPreferences.SYSTEM, KeySenseUiPreferences.LIGHT,
      KeySenseUiPreferences.DARK};
    for (int i = 0; i < names.length; i++) {
      final String mode = values[i];
      Button option = button(names[i]);
      appearanceButtons[i] = option;
      option.setOnClickListener(v -> {
        KeySenseUiPreferences.setAppearance(context, mode);
        renderColors();
      });
      options.addView(option, new LayoutParams(0, dp(42), 1f));
    }
    appearanceCard.addView(options);

    // KEYSENSE: Three research-friendly typing assistance modes. The middle
    // option is deliberately the default, not automatic correction.
    assistanceCard = card();
    content.addView(assistanceCard, cardParams());
    assistanceCard.addView(label("Typing Assistance", 16, true));
    assistanceCard.addView(label(
        "Choose how the keyboard offers suggested words for you.", 12, false));

    String[] suggestionLabels = {
        "Disable suggestions",
        "Enable suggestions",
        "Enable suggestions with auto-correction"
    };
    int[] suggestionModes = {
        KeySenseSuggestionPreferences.DISABLED,
        KeySenseSuggestionPreferences.CORRECTION_SUGGESTIONS,
        KeySenseSuggestionPreferences.AUTO_CORRECTION
    };
    for (int i = 0; i < suggestionModes.length; i++) {
      final int mode = suggestionModes[i];
      Button option = button(suggestionLabels[i]);
      option.setContentDescription(suggestionLabels[i]);
      suggestionButtons[i] = option;
      option.setOnClickListener(v -> {
        KeySenseSuggestionPreferences.setMode(context, mode);
        renderColors();
      });
      LinearLayout.LayoutParams optionParams =
          new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(49));
      optionParams.bottomMargin = dp(6);
      assistanceCard.addView(option, optionParams);
    }
    
    renderColors();
  }

  /** Recompute current normalized slider state when settings are re-opened. */
  public void refresh() {
    pendingHeightDp = clamp(KMManager.getKeyboardHeight(context));
    slider.setProgress(pendingHeightDp - minDp);
    renderColors();
  }

  private void renderColors() {
    boolean dark = KeySenseUiPreferences.isDarkMode(context);
    int bg = dark ? Color.BLACK : Color.rgb(245, 246, 248);
    int fg = dark ? Color.WHITE : Color.BLACK;
    setBackgroundColor(bg);
    heightCard.setBackgroundColor(dark ? Color.rgb(30, 30, 32) : Color.WHITE);
    appearanceCard.setBackgroundColor(dark ? Color.rgb(30, 30, 32) : Color.WHITE);
    assistanceCard.setBackgroundColor(dark ? Color.rgb(30, 30, 32) : Color.WHITE);
    updateTextColors(this, fg, dark ? Color.LTGRAY : Color.DKGRAY);

    int buttonTint = dark
        ? Color.rgb(58, 58, 60)
        : Color.rgb(220, 223, 227);

    android.content.res.ColorStateList tint =
        android.content.res.ColorStateList.valueOf(buttonTint);

    abcButton.setBackgroundTintList(tint);
    resetButton.setBackgroundTintList(tint);

    abcButton.setTextColor(fg);
    resetButton.setTextColor(fg);

    String choice = KeySenseUiPreferences.appearance(context);
    String[] choices = {KeySenseUiPreferences.SYSTEM, KeySenseUiPreferences.LIGHT,
      KeySenseUiPreferences.DARK};
    for (int i = 0; i < choices.length; i++) {
      boolean selected = choices[i].equals(choice);
      appearanceButtons[i].setTextColor(selected ? Color.WHITE : fg);
      appearanceButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(
        selected ? Color.rgb(72, 120, 187) : (dark ? Color.rgb(58, 58, 60) : Color.rgb(220, 223, 227))));
    }

    int suggestionMode = KeySenseSuggestionPreferences.getMode(context);
    int[] modes = {
        KeySenseSuggestionPreferences.DISABLED,
        KeySenseSuggestionPreferences.CORRECTION_SUGGESTIONS,
        KeySenseSuggestionPreferences.AUTO_CORRECTION
    };
    for (int i = 0; i < modes.length; i++) {
      boolean selected = suggestionMode == modes[i];
      suggestionButtons[i].setTextColor(selected ? Color.WHITE : fg);
      suggestionButtons[i].setBackgroundTintList(
          android.content.res.ColorStateList.valueOf(
              selected ? Color.rgb(72, 120, 187) : buttonTint));
    }
  }
  
  private void updateTextColors(View view, int primary, int secondary) {
      if (view instanceof TextView) {
          ((TextView) view).setTextColor(primary);
      }

      if (view instanceof android.view.ViewGroup) {
          android.view.ViewGroup group = (android.view.ViewGroup) view;

          for (int i = 0; i < group.getChildCount(); i++) {
              updateTextColors(group.getChildAt(i), primary, secondary);
          }
      }
  }

  private LinearLayout card() {
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(VERTICAL);
    card.setPadding(dp(16), dp(13), dp(16), dp(13));
    return card;
  }

  private LayoutParams cardParams() {
    LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(12);
    return params;
  }

  private Button button(String title) {
    Button b = new Button(context);
    b.setText(title);
    b.setAllCaps(false);
    b.setTextSize(13);
    b.setFocusable(false);
    return b;
  }

  private TextView label(String value, int sp, boolean bold) {
    TextView text = new TextView(context);
    text.setText(value);
    text.setTextSize(sp);
    text.setPadding(0, dp(5), 0, dp(5));
    if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return text;
  }

  private int clamp(int value) {
    return Math.max(minDp, Math.min(maxDp, value));
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}

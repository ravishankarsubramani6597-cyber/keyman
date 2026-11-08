/**
 * Copyright (C) 2017 SIL International. All rights reserved.
 */

package com.keyman.android;

import com.keyman.engine.util.DownloadFileUtils;
import com.tavultesoft.kmapro.BuildConfig;
import com.tavultesoft.kmapro.DefaultLanguageResource;
import com.tavultesoft.kmapro.KeymanSettingsKeys;
import com.tavultesoft.kmapro.PreferencesManager;
import com.keyman.engine.KMManager;
import com.keyman.engine.KMManager.KeyboardType;
import com.keyman.engine.KMHardwareKeyboardInterpreter;
import com.keyman.engine.KeySenseUiActionTarget;
import com.keyman.engine.KeySenseAndroidOrientation;
import com.keyman.engine.KeySenseLiveDebug;
import com.keyman.engine.KeySenseSessionManager;
import com.keyman.engine.KMManager.SuggestionType;
import com.keyman.engine.KeyboardEventHandler.OnKeyboardEventListener;
import com.keyman.engine.R;
import com.keyman.engine.data.Keyboard;
import com.keyman.engine.util.DependencyUtil;
import com.keyman.engine.util.DependencyUtil.LibraryType;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.content.Intent;

import io.sentry.android.core.SentryAndroid;
import io.sentry.Sentry;

public class SystemKeyboard extends InputMethodService implements OnKeyboardEventListener, KeySenseUiActionTarget {

  private static View inputView = null;
  private KMHardwareKeyboardInterpreter interpreter = null;
  private int inputType = InputType.TYPE_NULL;
  private int lastOrientation = Configuration.ORIENTATION_UNDEFINED;

  private static final String TAG = "SystemKeyboard";

  /**
   * Observe the display's orientation, without modifying Keyman's view.
   * Prefer the IME window display; use the default display before attachment.
   */
  private void observeKeySenseOrientation(Configuration configuration) {
    if (!BuildConfig.DEBUG || configuration == null) return;

    Display display = null;
    if (getWindow() != null && getWindow().getWindow() != null) {
      View decor = getWindow().getWindow().getDecorView();
      if (decor != null) display = decor.getDisplay();
    }
    if (display == null) {
      WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
      if (manager != null) display = manager.getDefaultDisplay();
    }
    if (display == null) return;

    KeySenseSessionManager.Orientation orientation =
        KeySenseAndroidOrientation.fromConfigurationAndRotation(
            configuration.orientation, display.getRotation());
    KeySenseLiveDebug.onOrientationObserved(orientation);
  }

  private void observeKeySenseOrientation() {
    observeKeySenseOrientation(getResources().getConfiguration());
  }


  /**
   * Main initialization of the input method component. Be sure to call
   * to super class.
   */
  @Override
  public void onCreate() {
    super.onCreate();

    if (DependencyUtil.libraryExists(LibraryType.SENTRY) && !Sentry.isEnabled()) {
      Log.d(TAG, "Initializing Sentry");
      SentryAndroid.init(getApplicationContext(), options -> {
        options.setEnableAutoSessionTracking(false);
        options.setRelease(com.tavultesoft.kmapro.BuildConfig.KEYMAN_VERSION_GIT_TAG);
        options.setEnvironment(com.tavultesoft.kmapro.BuildConfig.KEYMAN_VERSION_ENVIRONMENT);
      });
    }
    if (BuildConfig.DEBUG) {
      KMManager.setDebugMode(true);
    }

    KMManager.addKeyboardEventListener(this);
    Context context = getApplicationContext();

    KMManager.initialize(
        context,
        KeyboardType.KEYBOARD_TYPE_SYSTEM
    );

    // Install and configure the KEYSENSE keyboard
    DefaultLanguageResource.install(context);
    // One-time default: correction suggestions, without auto-correction.
    KeySenseSuggestionPreferences.ensureDefault(context);

    // KEYSENSE: Use only our configured default layout
    // within the Android system keyboard.
    // KMManager.setShouldAllowSetKeyboard(false);

    interpreter = new KMHardwareKeyboardInterpreter(
        context,
        KeyboardType.KEYBOARD_TYPE_SYSTEM
    );

    KMManager.setInputMethodService(this); // for HW interface

    SharedPreferences prefs = context.getSharedPreferences(context.getString(R.string.kma_prefs_name), Context.MODE_PRIVATE);
    KMManager.setSpacebarText(KMManager.SpacebarText.BLANK);

    // Set the system keyboard HTML banner
    BannerController.setHTMLBanner(this, KeyboardType.KEYBOARD_TYPE_SYSTEM, isDarkMode());

    boolean mayHaveHapticFeedback = prefs.getBoolean(KeymanSettingsKeys.HAPTIC_FEEDBACK, false);
    KMManager.setHapticFeedback(mayHaveHapticFeedback);

    // Checking for updates should never be allowed to crash the keyboard.
    // Just silently fail if this occurs.
    if(DownloadFileUtils.getDownloadManager(this) != null) {
      // Will try to emit a toast if it fails - i.e., is not silent.
      KMManager.executeResourceUpdate(this);
    }
    observeKeySenseOrientation();
  }

  @Override
  public void onDestroy() {
    inputView = null;
    KMManager.removeKeyboardEventListener(this);
    interpreter = null; // Throw it away, since we're losing our application's context.
    KMManager.onDestroy();
    super.onDestroy();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    // Detect rotation before the framework may recreate the input view.
    observeKeySenseOrientation(newConfig);
    super.onConfigurationChanged(newConfig);
    // Android may update the display's rotation during the framework callback.
    observeKeySenseOrientation(newConfig);
  }

  /**
   * This is the point where you can do all of your UI initialization. It
   * is called after creation and any configuration change.
   */
  @Override
  public void onInitializeInterface() {
    super.onInitializeInterface();
  }

  /**
   * Called by the framework when your view for creating input needs to
   * be generated. This will be called the first time your input method
   * is displayed, and every time it needs to be re-created such as due to
   * a configuration change.
   */
  @Override
  public View onCreateInputView() {
    if (inputView == null) {
        inputView = KMManager.createInputView(this);
    }

    ViewGroup parent = (ViewGroup) inputView.getParent();
    if (parent != null) {
        parent.removeView(inputView);
    }

    return KeySenseEmojiHost.wrap(
    this,
    inputView,
    emoji -> com.keyman.engine.KeySenseLiveDebug
        .onValidatedEmoji(emoji)
    );

  }

  /**
   * Deal with the editor reporting movement of its cursor.
   */
  @Override
  public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    KMManager.updateSelectionRange(KMManager.KeyboardType.KEYBOARD_TYPE_SYSTEM);
  }

  private void sendCurrentFontName() {
    Keyboard keyboardInfo = KMManager.getCurrentKeyboardInfo(this);
    if (keyboardInfo != null) {
      String fontName = keyboardInfo.getFont();

      Intent intent;
      if  (BuildConfig.DEBUG) {
        intent = new Intent("com.tavultesoft.kmapro.debug.keyboard_changed");
      } else {
        intent = new Intent("com.tavultesoft.kmapro.keyboard_changed");
      }
      intent.putExtra("fontName", fontName);
      sendBroadcast(intent);
    }
  }

  /**
   * This is the main point where we do our initialization of the input method
   * to begin operating on an application.  At this point we have been
   * bound to the client, and are now receiving all of the detailed information
   * about the target of our edits.
   */
  @Override
  public void onStartInput(EditorInfo attribute, boolean restarting) {
    attribute.imeOptions |= EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;
    super.onStartInput(attribute, restarting);
    observeKeySenseOrientation();

    // KEYSENSE DEBUG: Input field activated
    if (BuildConfig.DEBUG) {
      Log.d(TAG, "[KEYSENSE DEBUG] onStartInput | restarting=" + restarting);
    }

    KMManager.onStartInput(attribute, restarting);
    KMManager.resetContext(KeyboardType.KEYBOARD_TYPE_SYSTEM);

    Context appContext = getApplicationContext();
    // Temporarily disable predictions on certain fields (e.g. hidden password field or numeric)
    inputType = attribute.inputType;
    KMManager.setPredictionsSuspended(inputType, KeyboardType.KEYBOARD_TYPE_SYSTEM);
    if (KMManager.getPredictionsSuspended(KeyboardType.KEYBOARD_TYPE_SYSTEM)) {
      KMManager.setBannerOptions(false);
      // Set the system keyboard HTML banner
      BannerController.setHTMLBanner(this, KeyboardType.KEYBOARD_TYPE_SYSTEM, isDarkMode());
    } else if (KMManager.isKeyboardLoaded(KeyboardType.KEYBOARD_TYPE_SYSTEM)){
      // Check if predictions needs to be re-enabled per Settings preference
      Keyboard kbInfo = KMManager.getCurrentKeyboardInfo(appContext);
      if (kbInfo != null) {
        String langId = kbInfo.getLanguageID();
        SharedPreferences prefs = appContext.getSharedPreferences(appContext.getString(R.string.kma_prefs_name), Context.MODE_PRIVATE);
        int maySuggest = prefs.getInt(KMManager.getLanguageAutoCorrectionPreferenceKey(langId),
            KeySenseSuggestionPreferences.DEFAULT);
        // Enable banner if maySuggest is not SuggestionType.SUGGESTIONS_DISABLED (0)
        KMManager.setBannerOptions(maySuggest != SuggestionType.SUGGESTIONS_DISABLED.toInt());
      } else {
        KMManager.setBannerOptions(false);
      }
    }

    // Determine special handling for ENTER key
    KMManager.setEnterMode(attribute.imeOptions, inputType);

    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      ExtractedText icText = ic.getExtractedText(new ExtractedTextRequest(), 0);
      /*
        We do sometimes receive null `icText.text`, even though
        getExtractedText() docs does not list this as a possible
        return value, so we test for that as well (#11479)
      */
      if (icText != null && icText.text != null) {
        // Update the text selection but ignore the returned statuses
        KMManager.updateText(KeyboardType.KEYBOARD_TYPE_SYSTEM, icText.text.toString());
        KMManager.updateSelectionRange(KeyboardType.KEYBOARD_TYPE_SYSTEM);
      }
    }

    // Select numeric layer if applicable
    if (KMManager.isNumericField(inputType)) {
      KMManager.setNumericLayer(KeyboardType.KEYBOARD_TYPE_SYSTEM);
    }

    if (KMManager.isKeyboardLoaded(KeyboardType.KEYBOARD_TYPE_SYSTEM)) {
      sendCurrentFontName();
    }
  }

  // KEYSENSE DEBUG: Keyboard input view finished
  @Override
  public void onFinishInputView(boolean finishingInput) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, "[KEYSENSE DEBUG] onFinishInputView | finishingInput=" + finishingInput);
    }

    observeKeySenseOrientation();
    super.onFinishInputView(finishingInput);
  }

  // KEYSENSE DEBUG: Input field session finished
  @Override
  public void onFinishInput() {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, "[KEYSENSE DEBUG] onFinishInput");
    }

    // Capture a rotation boundary before finalizing the editor context.
    observeKeySenseOrientation();
    super.onFinishInput();
    if ((getApplicationInfo().flags
        & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {

    com.keyman.engine.KeySenseLiveDebug.onInputFinished();
  }
  }

  /*
  @Override
  public void onStartInputView(EditorInfo attribute, boolean restarting) {
      super.onStartInputView(attribute, restarting);

      if (BuildConfig.DEBUG) {
          Log.d(TAG, "[KEYSENSE DEBUG] onStartInputView");
      }

      // KEYSENSE: Explicitly select our keyboard
      // whenever the Android system keyboard opens.
      Context context = getApplicationContext();

      int keysenseIndex = KMManager.getKeyboardIndex(
          context,
          "keysense_eurolatin",
          "en"
      );

      if (keysenseIndex >= 0) {
          KMManager.setKeyboard(context, keysenseIndex);
      } else {
          Log.e(TAG, "KEYSENSE keyboard not registered");
      }

      setInputView(onCreateInputView());

      // Update the input type
      inputType = attribute.inputType;
  }
  */


  @Override
  public void onStartInputView(EditorInfo attribute, boolean restarting) {
      super.onStartInputView(attribute, restarting);
      observeKeySenseOrientation();

      if (BuildConfig.DEBUG) {
          Log.d(TAG, "[KEYSENSE DEBUG] onStartInputView");
      }

      // The Android framework creates the input view.
      // Do not recreate or reattach it every time the keyboard opens.

      inputType = attribute.inputType;
  }



  @Override
  public void onUpdateExtractingVisibility(EditorInfo ei) {
    super.onUpdateExtractingVisibility(ei);
  }

  @Override
  public void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly) {
    super.onConfigureWindow(win, isFullscreen, isCandidatesOnly);

    // We don't currently use isFullscreen or isCandidatesOnly; we always want to MATCH_PARENT,
    // unlike the default for height which is WRAP_CONTENT. We then adjust the touchable area
    // in `onCalculateInsets`
    win.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);

    // KEYSENSE: This full-height IME must not consume touches outside its
    // touchable region.  Let editor taps and selection-menu actions pass
    // through to the application while the keyboard remains visible.
    win.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
  }

  @Override
  public void onComputeInsets(InputMethodService.Insets outInsets) {
    super.onComputeInsets(outInsets);
    observeKeySenseOrientation();

    // This method (likely) includes the IME equivalent to `onResume` for `Activity`-based classes,
    // making it an important time to detect orientation changes.
    Context appContext = getApplicationContext();
    int newOrientation = KMManager.getOrientation(appContext);
    if(newOrientation != lastOrientation) {
      lastOrientation = newOrientation;
      Configuration newConfig = this.getResources().getConfiguration();
      KMManager.onConfigurationChanged(newConfig);
    }

    // KEYSENSE: This IME intentionally uses a full-height window so its native
    // Settings/Emoji overlays can coexist with the Keyman WebView.  The touch
    // region MUST still be limited to the visible keyboard area; otherwise
    // the transparent part of the IME steals taps on the application's editor,
    // preventing cursor placement while the keyboard is visible.
    //
    // Keep the prior content-inset behavior unchanged; this fix changes only
    // touch pass-through.  The separate app-resizing insets can be reviewed
    // later if a specific host app has a layout issue.
    int inputViewHeight = inputView != null ? inputView.getHeight() : 0;
    int originalNavigationHeightPx = KMManager.getNavigationBarHeight(
        this, KeyboardType.KEYBOARD_TYPE_SYSTEM);
    outInsets.contentTopInsets = inputViewHeight
        - KMManager.getBannerHeight(this)
        - KMManager.getKeyboardHeight(this)
        - originalNavigationHeightPx;
    outInsets.visibleTopInsets = outInsets.contentTopInsets;

    // Compute from the actual window dimensions.  inputView.getHeight() is
    // only the embedded Keyman view's height, NOT the full-height IME window.
    // KMManager.getKeyboardHeight() returns dp, but banner/navigation heights
    // are already pixels.  Keep all calculations in pixels.
    Window imeWindow = getWindow() != null ? getWindow().getWindow() : null;
    View decor = imeWindow != null ? imeWindow.getDecorView() : null;
    int windowWidthPx = decor != null ? decor.getWidth() : 0;
    int windowHeightPx = decor != null ? decor.getHeight() : 0;

    if (windowWidthPx > 0 && windowHeightPx > 0) {
      float density = getResources().getDisplayMetrics().density;
      int navigationHeightPx = Math.max(0,
          KMManager.getNavigationBarHeight(this, KeyboardType.KEYBOARD_TYPE_SYSTEM));
      int bannerHeightPx = Math.max(0, KMManager.getBannerHeight(this));
      // In this Keyman build, KMManager uses its height value directly in
      // Android LayoutParams, so it is already in screen pixels.  Scaling
      // again by density makes the invisible touch region grow too tall when
      // users increase the keyboard height.
      int keyboardHeightPx = Math.max(0, KMManager.getKeyboardHeight(this));

      // Only the visible ABC keyboard and its banner should intercept editor
      // touches.  The previous 265dp minimum reserved space for the native
      // panels even while they were HIDDEN, blocking cursor taps above a
      // smaller keyboard (notably Galaxy landscape Small/Default heights).
      // Visible Settings/Emoji panels are handled separately below using
      // their measured on-screen top edge.
      int panelHeightPx = keyboardHeightPx + bannerHeightPx;
      int panelTopPx = Math.max(0,
          windowHeightPx - panelHeightPx - navigationHeightPx);

      // The suggestions banner belongs to Keyman's ABC WebView. Its actual
      // laid-out top may differ from a height-based estimate on some phones
      // (notably Galaxy). Include the real WebView top in the touch region,
      // just as we already do for the native Settings/Emoji overlays.
      // Do not reserve a fixed extra strip: that would block editor taps.
      View abcView = KMManager.getKMKeyboard(KeyboardType.KEYBOARD_TYPE_SYSTEM);
      if (abcView != null
          && abcView.isAttachedToWindow()
          && abcView.getVisibility() == View.VISIBLE
          && abcView.getHeight() > 0) {
        int[] abcLocation = new int[2];
        abcView.getLocationInWindow(abcLocation);
        int abcTopPx = abcLocation[1];
        // Ignore zero/out-of-window positions during layout or teardown.
        if (abcTopPx > 0 && abcTopPx < windowHeightPx) {
          int edgePaddingPx = Math.round(2f * density);
          panelTopPx = Math.min(panelTopPx,
              Math.max(0, abcTopPx - edgePaddingPx));
        }
      }

      // Native overlays may be positioned above the keyboard-height estimate
      // on certain devices. Use their measured window-coordinate top edge,
      // not another density or navigation-bar approximation.
      int overlayTopPx = KeySenseEmojiHost.visiblePanelTopInWindow(this);
      if (overlayTopPx >= 0) {
        int edgePaddingPx = Math.round(4f * density);
        panelTopPx = Math.min(panelTopPx,
            Math.max(0, overlayTopPx - edgePaddingPx));
      }

      // Preserve the existing content/visible inset calculations (which
      // govern app resize behavior) and correct ONLY the touch hit region.
      outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;
      outInsets.touchableRegion.set(
          0, panelTopPx, windowWidthPx, windowHeightPx);
    }
    // When the decor view is not yet laid out, leave Android's default
    // super.onComputeInsets() touch behavior instead of capturing the screen.

  }

  @Override
  public void onKeyboardLoaded(KeyboardType keyboardType) {
    // Do nothing
  }

  // KEYSENSE DEBUG: Android keyboard window becomes visible
  @Override
  public void onWindowShown() {
    super.onWindowShown();
    observeKeySenseOrientation();

    if (BuildConfig.DEBUG) {
      Log.d(TAG, "[KEYSENSE DEBUG] onWindowShown");
    }
  }

  // KEYSENSE DEBUG: Android keyboard window becomes hidden
  @Override
  public void onWindowHidden() {
    super.onWindowHidden();

    if (BuildConfig.DEBUG) {
      Log.d(TAG, "[KEYSENSE DEBUG] onWindowHidden");
    }
  }

  @Override
  public void onKeyboardChanged(String newKeyboard) {
    // Refresh banner theme
    BannerController.setHTMLBanner(this, KeyboardType.KEYBOARD_TYPE_SYSTEM, isDarkMode());
    KMManager.showSystemKeyboard();
    sendCurrentFontName();
  }

  @Override
  public void onKeyboardShown() {
    // Refresh banner theme
    BannerController.setHTMLBanner(this, KeyboardType.KEYBOARD_TYPE_SYSTEM, isDarkMode());
  }

  @Override
  public void onKeyboardDismissed() {
    // Do nothing
  }

  @Override
  public boolean onEvaluateInputViewShown() {
    // On Android API 36+, the OSK defaults to not appearing when a physical keyboard is connected.
    // If the default implementation returns true, recommend honoring it
    // Reference: https://android.googlesource.com/platform/frameworks/base/+/7b739a8%5E%21/
    if (super.onEvaluateInputViewShown()) {
      return true;
    };

    Context context = getApplicationContext();
    SharedPreferences prefs = context.getSharedPreferences(PreferencesManager.kma_prefs_name, Context.MODE_PRIVATE);
    boolean showOSK = prefs.getBoolean(KeymanSettingsKeys.OSK_WITH_PHYSICAL_KEYBOARD, false);
    return showOSK;
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    // Determine if Physical keystroke should be passed off
    if (inputType == InputType.TYPE_NULL) {
      return false; // Revert to default handling
    }

    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
          // Dismiss the keyboard if currently shown
          if (isInputViewShown()) {
            KMManager.hideSystemKeyboard();
            return true;
          }
          break;
      }
    }

    return interpreter.onKeyDown(keyCode, event);  // if false, will revert to default handling.
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    return interpreter.onKeyUp(keyCode, event);
  }

  @Override
  public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
    return interpreter.onKeyMultiple(keyCode, count, event);
  }

  @Override
  public boolean onKeyLongPress(int keyCode, KeyEvent event) {
    return interpreter.onKeyLongPress(keyCode, event);
  }
  @Override
  public void onKeySenseUiAction(String action) {
    KeySenseEmojiHost.showPanel(this, action);
  }

  // KEYSENSE: Native Settings appearance preference for the Keyman banner.
  // The WebView's keyboard theme still requires a separate integration check.
  public void refreshKeySenseBanner() {
    BannerController.setHTMLBanner(this, KeyboardType.KEYBOARD_TYPE_SYSTEM, isDarkMode());
  }

  // KEYSENSE: appearance refresh only. Keyman's applyKeyboardHeight() already
  // handles OSK height; calling setOskHeight() here caused competing refreshes.
  public void refreshKeySenseKeyboardVisuals() {
    refreshKeySenseBanner();
    if (inputView != null) {
      evaluateKeySenseAppearance(inputView);
    }
    if (getWindow() != null && getWindow().getWindow() != null) {
      getWindow().getWindow().getDecorView().requestLayout();
    }
  }

  /**
   * Android's WebView can host keyboard.html inside a same-origin iframe.
   * Evaluate in each accessible frame instead of assuming the main window is
   * the one where android-host.js / Keyman Web runs.
   * Logs contain only frame/theme diagnostic metadata, never typed content.
   */
  private void evaluateKeySenseAppearance(View view) {
    if (view instanceof android.webkit.WebView) {
      final String js =
          "(function(){" +
          "var result=[];" +
          "function visit(w,depth){" +
          "if(depth>3)return;" +
          "try{" +
          "var applied=false;" +
          "if(typeof w.keySenseInstallAppearanceStyles==='function')" +
          "w.keySenseInstallAppearanceStyles();" +
          "if(typeof w.keySenseApplyAppearance==='function'){" +
          "w.keySenseApplyAppearance();applied=true;}" +
          "var count=w.document.querySelectorAll('.kmw-key').length;" +
          "result.push('depth='+depth+',applied='+applied+',keys='+count);" +
          "var frames=w.document.querySelectorAll('iframe');" +
          "for(var i=0;i<frames.length;i++){" +
          "try{if(frames[i].contentWindow)visit(frames[i].contentWindow,depth+1);}" +
          "catch(ignore){result.push('frame-inaccessible');}" +
          "}" +
          "}catch(e){result.push('frame-error:'+e.name);}" +
          "}" +
          "visit(window,0);return result.join(';');" +
          "})()";
      ((android.webkit.WebView) view).evaluateJavascript(js,
          result -> Log.d(TAG, "KEYSENSE appearance refresh " + result));
    } else if (view instanceof ViewGroup) {
      ViewGroup children = (ViewGroup) view;
      for (int i = 0; i < children.getChildCount(); i++) {
        evaluateKeySenseAppearance(children.getChildAt(i));
      }
    }
  }

  private boolean isDarkMode() {
    return KeySenseUiPreferences.isDarkMode(this);
  }
}
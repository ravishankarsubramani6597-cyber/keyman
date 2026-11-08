package com.keyman.android;

import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import com.keyman.engine.KMManager;
import com.keyman.engine.KMManager.KeyboardType;
import com.keyman.engine.KeySenseLiveDebug;
import org.json.JSONException;
import java.io.IOException;

/**
 * KEYSENSE panels over Keyman's full-height IME view.
 * Settings and Emoji actions originate in the dedicated touch-layout keys.
 *
 * Does not alter keyboard event instrumentation or pass typed content
 * to the KEYSENSE collector.
 */
public final class KeySenseEmojiHost {
  private static final String TAG = "KeySenseEmojiHost";
  private static final KeySenseEmojiHistory HISTORY = new KeySenseEmojiHistory();

  private KeySenseEmojiHost() { }

  public interface EmojiCommittedCallback {
    void onEmojiCommitted(String emoji);
  }

  public static View wrap(InputMethodService service, View keymanView,
                          EmojiCommittedCallback onCommittedEmoji) {
    if (keymanView == null) return null;
    if (onCommittedEmoji == null) throw new IllegalArgumentException("onCommittedEmoji required");

    final KeySenseEmojiCatalog catalog;
    try {
      catalog = KeySenseEmojiCatalog.load(service);
    } catch (IOException | JSONException error) {
      Log.e(TAG, "Unable to load emoji catalog; retaining standard keyboard", error);
      return keymanView;
    }

    ViewGroup parent = (ViewGroup) keymanView.getParent();
    if (parent != null) parent.removeView(keymanView);

    FrameLayout root = new FrameLayout(service);
    root.addView(keymanView, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    final int navPx = Math.max(0,
        KMManager.getNavigationBarHeight(service, KeyboardType.KEYBOARD_TYPE_SYSTEM));
    // Keyman reports keyboard/banner heights in dp; Android LayoutParams require px.
    final int bannerDp = Math.max(0, KMManager.getBannerHeight(service));
    final int keyboardDp = Math.max(0, KMManager.getKeyboardHeight(service));
    int panelPx = Math.max(
      dp(service, 200),
      keyboardDp + bannerDp
    );

    KeySenseEmojiPanel panel = new KeySenseEmojiPanel(service, catalog, HISTORY, emoji -> {
      InputConnection ic = service.getCurrentInputConnection();
      if (ic == null) return;
      if (ic.commitText(emoji, 1)) {
        HISTORY.record(emoji); // The emoji itself stays in the UI history only.
        onCommittedEmoji.onEmojiCommitted(emoji); // RAM-only; debug collector rechecks privacy.
      }
    });
    panel.setVisibility(View.GONE);
    FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, panelPx, Gravity.BOTTOM);
    panelParams.bottomMargin = 0;
    root.addView(panel, panelParams);

    FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, dp(service, 360), Gravity.BOTTOM);
    settingsParams.bottomMargin = 0;

    final KeySenseSettingsPanel[] settingsRef = new KeySenseSettingsPanel[1];
    KeySenseSettingsPanel settings = new KeySenseSettingsPanel(service, selectedHeightDp -> {

      // Safe diagnostic: no text or key identities are logged.
      Log.d(TAG, "[KEYSENSE SETTINGS] ABC requested");
      // Close Settings first.
      settingsRef[0].setVisibility(View.GONE);
      keymanView.setVisibility(View.VISIBLE);
      panel.setVisibility(View.GONE);

      // Let Keyman manage the keyboard height.
      int currentHeight = KMManager.getKeyboardHeight(service);

      if (selectedHeightDp == KMManager.KeyboardHeight_Reset
        || selectedHeightDp != currentHeight) {
        KMManager.applyKeyboardHeight(service, selectedHeightDp);
      }

      // Update the Emoji panel height, but keep Settings fixed.
      int newHeight = KMManager.getKeyboardHeight(service);

      panelParams.height = Math.max(
        dp(service, 200),
        newHeight + bannerDp
      );
      panel.setLayoutParams(panelParams);

      // Refresh appearance without changing the keyboard layout.
      panel.refreshAppearance();

      if (service instanceof SystemKeyboard) {
        ((SystemKeyboard) service).refreshKeySenseKeyboardVisuals();
      }

      root.requestLayout();
      root.requestApplyInsets();
    });
    settingsRef[0] = settings;
    settings.setVisibility(View.GONE);
    root.addView(settings, settingsParams);



    // Dedicated touch keys are dispatched by keySenseUiAction -> SystemKeyboard.
    // No floating native buttons and no hard-coded Pixel width offsets.
    panel.setOnABCRequested(() -> {
      keymanView.setVisibility(View.VISIBLE);
      panel.setVisibility(View.GONE);
      settings.setVisibility(View.GONE);
      root.requestLayout();
      root.requestApplyInsets();
      Log.d(TAG, "emojiPanelVisible=false");
    });

    Controller controller = new Controller(service, panel, settings);
    root.setTag(controller); // keep the controller alive while root is attached
    currentRoot = new java.lang.ref.WeakReference<>(root);

    // Native emoji-panel Space and Backspace bypass Keyman Web's keyevent
    // listener, so report one metadata event after the editor accepts the action.
    panel.setOnSpaceRequested(() -> {
      InputConnection ic = service.getCurrentInputConnection();
      if (ic != null && ic.commitText(" ", 1)) {
        KeySenseLiveDebug.onValidatedCategory("SPACE");
      }
    });
    panel.setOnBackspaceRequested(() -> {
      InputConnection ic = service.getCurrentInputConnection();
      if (ic == null) return;
      boolean down = ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
      boolean up = ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
      if (down && up) {
        KeySenseLiveDebug.onValidatedCategory("BACKSPACE");
      }
    });

    return root;
  }


  /**
   * Top of the currently visible KEYSENSE overlay, in IME window coordinates.
   * Returns -1 when no native overlay is visible or has been laid out.
   * Keeps the system editor touchable above the keyboard while making
   * the full Settings header (including ABC) reachable on all densities.
   */
  static int visiblePanelTopInWindow(InputMethodService service) {
    View root = currentRoot.get();
    if (root == null || !root.isAttachedToWindow()
        || !(root.getTag() instanceof Controller)) return -1;

    Controller controller = (Controller) root.getTag();
    if (controller.service != service) return -1;

    View visible = controller.settings.getVisibility() == View.VISIBLE
        ? controller.settings
        : (controller.emoji.getVisibility() == View.VISIBLE
            ? controller.emoji : null);
    if (visible == null || visible.getHeight() <= 0) return -1;

    int[] position = new int[2];
    visible.getLocationInWindow(position);
    return Math.max(0, position[1]);
  }

  private static java.lang.ref.WeakReference<View> currentRoot =
      new java.lang.ref.WeakReference<>(null);

  private static final class Controller {
    final InputMethodService service;
    final KeySenseEmojiPanel emoji;
    final KeySenseSettingsPanel settings;

    Controller(InputMethodService service, KeySenseEmojiPanel emoji,
               KeySenseSettingsPanel settings) {
      this.service = service;
      this.emoji = emoji;
      this.settings = settings;
    }
  }

  
  public static void showPanel(InputMethodService service, String action) {
      View root = currentRoot.get();
      if (root == null || !(root.getTag() instanceof Controller)) return;

      Controller c = (Controller) root.getTag();
      if (c.service != service || !root.isAttachedToWindow()) return;
      
      int safeBottomPx = Math.max(
          navigationOverlapPx(service, root),
          KMManager.getNavigationBarHeight(
              service,
              KeyboardType.KEYBOARD_TYPE_SYSTEM
          )
      );

      if ("SETTINGS".equals(action)) {
          c.emoji.setVisibility(View.GONE);

          // Hide the underlying ABC keyboard while Settings is open.
          // INVISIBLE preserves its layout for restoration.
          ((ViewGroup) root).getChildAt(0).setVisibility(View.INVISIBLE);

          // Match SystemKeyboard.onComputeInsets() in the installed Fix 3:
          // the lower max(265dp, keyboard + banner) region, plus navigation.
          // getKeyboardHeight() / getBannerHeight() are already pixel sizes
          // in this Keyman build; convert only the fixed 265dp minimum.
          int keyboardHeightPx = Math.max(0,
              KMManager.getKeyboardHeight(service)
              + KMManager.getBannerHeight(service));

          FrameLayout.LayoutParams layout =
              (FrameLayout.LayoutParams) c.settings.getLayoutParams();

          int navigationHeightPx = Math.max(0,
              KMManager.getNavigationBarHeight(
                  service, KeyboardType.KEYBOARD_TYPE_SYSTEM));
          int touchPanelHeightPx = Math.max(
              dp(service, 265), keyboardHeightPx);

          // The Settings bottom margin is part of the total overlay extent.
          // Subtract it from the available touch-region height so the ABC
          // header never appears above the area accepting IME touches.
          int touchableHeightPx = touchPanelHeightPx + navigationHeightPx;
          int availableHeightPx = Math.max(0,
              touchableHeightPx - safeBottomPx);
          int rootAvailableHeightPx = root.getHeight() > 0
              ? Math.max(0, root.getHeight() - safeBottomPx)
              : availableHeightPx;

          layout.height = Math.min(availableHeightPx, rootAvailableHeightPx);
          layout.bottomMargin = safeBottomPx;

          c.settings.setLayoutParams(layout);
          c.settings.refresh();
          c.settings.setVisibility(View.VISIBLE);
          // The panel's top depends on device density and navigation insets.
          // Recompute the IME touch region from its actual laid-out position.
          root.requestLayout();
          root.post(root::requestApplyInsets);

          Log.d(TAG, "settingsPanelVisible=true");

      } else if ("EMOJI".equals(action)) {
          c.settings.setVisibility(View.GONE);

          ((ViewGroup) root).getChildAt(0)
              .setVisibility(View.INVISIBLE);

          android.view.WindowInsets insets = root.getRootWindowInsets();

          if (insets != null) {
            FrameLayout.LayoutParams params =
            (FrameLayout.LayoutParams) c.emoji.getLayoutParams();

            params.bottomMargin = safeBottomPx;
            c.emoji.setLayoutParams(params);
          }

          c.emoji.refreshAppearance();
          c.emoji.refreshRecent();
          c.emoji.setVisibility(View.VISIBLE);
          root.requestLayout();
          root.post(root::requestApplyInsets);

          Log.d(TAG, "emojiPanelVisible=true");
      }
  }

  private static int navigationOverlapPx(
      InputMethodService service, View root) {

      if (android.os.Build.VERSION.SDK_INT < 23) return 0;

      android.view.WindowInsets insets =
          root.getRootWindowInsets();

      if (insets == null) return 0;

      int navigationPx;

      if (android.os.Build.VERSION.SDK_INT >= 30) {
          navigationPx = insets.getInsets(
              android.view.WindowInsets.Type.navigationBars()
          ).bottom;
      } else {
          navigationPx = insets.getStableInsetBottom();
      }

      if (navigationPx <= 0 || root.getHeight() <= 0) return 0;

      android.view.WindowManager wm =
          (android.view.WindowManager) service.getSystemService(
              android.content.Context.WINDOW_SERVICE
          );

      if (wm == null) return 0;

      android.graphics.Point display = new android.graphics.Point();
      wm.getDefaultDisplay().getRealSize(display);

      int[] position = new int[2];
      root.getLocationOnScreen(position);

      int rootBottom = position[1] + root.getHeight();
      int navigationTop = display.y - navigationPx;

      return Math.max(
          0,
          Math.min(navigationPx, rootBottom - navigationTop)
      );
  }

  private static int dp(InputMethodService service, int value) {
    return Math.round(value * service.getResources().getDisplayMetrics().density);
  }
}

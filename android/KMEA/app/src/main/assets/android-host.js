var _debug = false;

// Android harness attachment
if(window.parent && window.parent.jsInterface && !window.jsInterface) {
  window.jsInterface = window.parent.jsInterface;
}

var device = window.jsInterface.getDeviceType();
var oskHeight = Math.ceil(window.jsInterface.getKeyboardHeight() / window.devicePixelRatio);
var oskWidth = 0;
var bannerHeight = 0;
var bannerImagePath = '';
var bannerHTMLContents = '';
var fragmentToggle = 0;
var deferredBannerCall;
var longpressDelay = null;

/* KEYSENSE UI: apply native appearance preference to the Keyman WebView.
 * Keyman Web keyboard CSS owns its own colors; changing only the banner is insufficient.
 * These styles touch appearance only; never key positions or key-event logic.
 * Limit this test to the bundled EuroLatin keyboard before making it production-wide.
 */
function keySenseApplyAppearance() {
  var theme = 'system';
  try {
    if (window.jsInterface && typeof window.jsInterface.getKeySenseTheme === 'function') {
      theme = window.jsInterface.getKeySenseTheme();
    }
  } catch (ignore) { }
  var html = document.documentElement;
  html.classList.remove('keysense-force-light', 'keysense-force-dark');
  if (theme === 'light') html.classList.add('keysense-force-light');
  if (theme === 'dark') html.classList.add('keysense-force-dark');
}

function keySenseInstallAppearanceStyles() {
  var id = 'keysense-osk-appearance';
  if (document.getElementById(id)) return;
  var s = document.createElement('style');
  s.id = id;
  s.textContent = [
    'html.keysense-force-light body, html.keysense-force-light .kmw-osk-frame, html.keysense-force-light .kmw-osk-inner-frame, html.keysense-force-light .kmw-key-layer-group, html.keysense-force-light .kmw-key-layer { background-color: #e3e5e9 !important; }',
    'html.keysense-force-light .kmw-key:not(.kmw-key-hidden) { background: #fafbfc !important; color: #17191d !important; }',
    'html.keysense-force-light .kmw-key-text, html.keysense-force-light .kmw-key-popup-icon { color: #17191d !important; }',
    'html.keysense-force-light [class*="banner"] { background-color: #e3e5e9 !important; color: #17191d !important; }',
    'html.keysense-force-light [class*="banner"] * { color: #17191d !important; }',
    'html.keysense-force-dark .kmw-osk-frame, html.keysense-force-dark .kmw-osk-inner-frame { background: #252529 !important; }',
    'html.keysense-force-dark .kmw-key:not(.kmw-key-hidden) { background: #66666a !important; color: #ffffff !important; }',
    'html.keysense-force-dark .kmw-key-text, html.keysense-force-dark .kmw-key-popup-icon { color: #ffffff !important; }'
  ].join('\n');
  (document.head || document.documentElement).appendChild(s);
}

var sentryManager = new KeymanSentryManager({
  hostPlatform: "android"
});
sentryManager.init();

/*
 * KEYSENSE Android, debug-only, standard English-layout key-ID diagnostic.
 * Does not inspect, log, or transfer typed text. Returns metadata labels only.
 * This is deliberately NOT a general character-output classifier.
 * Must be called only for actual OSK 'keyevent' events, NOT insertText edits.
 */
function keySenseClassifyExtraOSKAction(keyEvent) {
  if (!keyEvent || typeof keyEvent.kName !== 'string') {
    return null;
  }

  var name = keyEvent.kName;
  if (name === 'K_ENTER') {
    // Enter may submit/send rather than insert a newline; DO NOT count yet.
    return 'ENTER_CANDIDATE';
  }

  var layer = typeof keyEvent.kLayer === 'string'
    ? keyEvent.kLayer.toLowerCase()
    : (typeof keyEvent.kbdLayer === 'string'
        ? keyEvent.kbdLayer.toLowerCase()
        : '');

  if (/^K_[0-9]$/.test(name)) {
    // Restrict estimated categories to conventional layers already tested.
    if (layer === 'default' || layer === 'caps' || layer === 'numeric') {
      return 'NUMBER';
    }
    if (layer === 'shift') {
      // Standard US English shifted digit row = punctuation.
      return 'SPECIAL_OTHER';
    }
    return 'EXTRA_UNRESOLVED';
  }

  // Some Keyman touch layouts identify a literal key by its Unicode ID
  // (e.g., U_002D for hyphen or U_0021 for exclamation). Decode ONLY
  // transiently in JS; send only the coarse category across the bridge.
  // A key ID describes a layout key, not guaranteed output after Keyman rules.
  var unicodeKey = /^U_([0-9A-F]{4,6})$/i.exec(name);
  if (unicodeKey) {
    var cp = parseInt(unicodeKey[1], 16);
    if (cp >= 0x30 && cp <= 0x39) return 'NUMBER';
    if (cp === 0x2D || cp === 0x27) return 'SPECIAL_WORD_INTERNAL';
    if ((cp >= 0x21 && cp <= 0x2F) ||
        (cp >= 0x3A && cp <= 0x40) ||
        (cp >= 0x5B && cp <= 0x60) ||
        (cp >= 0x7B && cp <= 0x7E)) return 'SPECIAL_OTHER';
    return 'EXTRA_UNRESOLVED';
  }

  var punct = /^(K_BKQUOTE|K_HYPHEN|K_EQUAL|K_LBRKT|K_RBRKT|K_BKSLASH|K_SEMICOLON|K_QUOTE|K_COMMA|K_PERIOD|K_SLASH)$/.test(name);
  if (!punct) {
    // Diagnostic only: a keyevent occurred, but its ID is not supported.
    // Modifier/navigation keys deliberately excluded to reduce noise.
    if (/^K_[A-Z0-9_]+$/.test(name) &&
        !/^K_[A-Z]$/.test(name) &&
        !/^(K_SHIFT|K_CAPS|K_LOPT|K_ROPT|K_LSHIFT|K_RSHIFT|K_NUMLOCK|K_SCROLL|K_CTRL|K_ALT|K_TAB|K_LEFT|K_RIGHT|K_UP|K_DOWN|K_BKSP|K_SPACE)$/.test(name)) {
      return 'EXTRA_UNRESOLVED';
    }
    return null;
  }

  if (layer !== 'default' && layer !== 'caps' && layer !== 'shift') {
    return 'EXTRA_UNRESOLVED';
  }

  // On a conventional English keyboard, unshifted ' and - may be word-internal.
  // The accumulator intentionally does not create a word from punctuation alone.
  if ((name === 'K_QUOTE' || name === 'K_HYPHEN') && layer !== 'shift') {
    return 'SPECIAL_WORD_INTERNAL';
  }

  return 'SPECIAL_OTHER';
}

// Optional export for isolated Node tests. No module is required by Android.
if (typeof module !== 'undefined' && module.exports) {
  module.exports = keySenseClassifyExtraOSKAction;
}

window.addEventListener('load', init, false);

function init() {
  keySenseInstallAppearanceStyles();
  keySenseApplyAppearance();
  //document.body.style.backgroundColor="transparent";
  //window.console.log('Device type = '+device);
  //window.console.log('Keyboard height = '+oskHeight);
  keyman.showKeyboardList = showMenu;
  keyman.hideKeyboard = hideKeyboard;
  keyman.menuKeyUp = menuKeyUp;
  keyman.getOskHeight = getOskHeight;
  keyman.getOskWidth = getOskWidth;
  keyman.beepKeyboard = beepKeyboard;

  // Readies the keyboard stub for instant loading during the init process.
  try {
    let starterStubStr = jsInterface.initialKeyboard();
    if(starterStubStr) {
      KeymanWeb.registerStub(JSON.parse(starterStubStr));
    }
  } catch (err) {
    // If a Java exception triggers during this time... okay, we can't
    // load a keyboard during startup.  Rely on older loading techniques.
    //
    // Note:  no information about the Java-side error is available here.
    if(err.message.indexOf("Java exception") < 0) {
      throw err;
    }
  }

  keyman.init({
    'embeddingApp':device,
    'fonts':'packages/',
    oninserttext: insertText,
    root:'./'
  }).then(function () {
    // KEYSENSE DEBUG: Observe original OSK key events.
    // Only transmit predefined event categories.
    // Never transmit typed characters or keyboard context.
    if (keyman.osk && typeof keyman.osk.on === 'function') {

      keyman.osk.on('keyevent', function (keyEvent) {

        if (!keyEvent || !window.jsInterface) {
          return;
        }

        // KEYSENSE: sound feedback for an actual on-screen key tap.
        // No key identity or typed content is passed to the native bridge.
        // Settings and Emoji open native panels and have their own UI feedback.
        if (keyEvent.kName !== 'T_KS_SETTINGS' &&
            keyEvent.kName !== 'T_KS_EMOJI' &&
            typeof window.jsInterface.keySensePlayKeySound === 'function') {
          window.jsInterface.keySensePlayKeySound();
        }

        // Dedicated KEYSENSE control keys have real layout positions.
        // Do not classify these as typing events or prediction selections.
        if (keyEvent.kName === 'T_KS_SETTINGS' ||
            keyEvent.kName === 'T_KS_EMOJI') {
          if (typeof window.jsInterface.keySenseUiAction === 'function') {
            window.jsInterface.keySenseUiAction(
              keyEvent.kName === 'T_KS_SETTINGS' ? 'SETTINGS' : 'EMOJI');
          }
          return;
        }

        var category = null;

        if (keyEvent.kName === 'K_SPACE') {
          category = 'SPACE';
        } else if (keyEvent.kName === 'K_BKSP') {
          category = 'BACKSPACE';
        } else if (
          typeof keyEvent.kName === 'string' &&
          /^K_[A-Z]$/.test(keyEvent.kName)
        ) {

          // KEYSENSE DEBUG:
          // Match Keyman's effective key-layer state.
          // Never send key identity or typed content.

          var layer =
            typeof keyEvent.kLayer === 'string'
              ? keyEvent.kLayer
              : keyEvent.kbdLayer;

          if (typeof layer !== 'string' || layer.length === 0) {
            category = 'LETTER_STATE_UNKNOWN';
          } else {
            var normalizedLayer = layer.toLowerCase();

            var hasShift = normalizedLayer.indexOf('shift') >= 0;
            var hasCaps = normalizedLayer.indexOf('caps') >= 0;

            if (hasShift && hasCaps) {
              category = 'LETTER_SHIFT_CAPS_STATE';
            } else if (hasCaps) {
              category = 'LETTER_CAPS_STATE';
            } else if (hasShift) {
              category = 'LETTER_SHIFT_STATE';
            } else {
              category = 'LETTER_BASE_STATE';
            }
          }
        }

        if (category === null) {
          category = keySenseClassifyExtraOSKAction(keyEvent);
        }

        if (category !== null &&
            typeof window.jsInterface.keySenseDebugAction === 'function') {

          window.jsInterface.keySenseDebugAction(category);
        }
      });
    }

    // Note:  For non-upgraded API 21, arrow functions will break the keyboard!
    bannerHeight = Math.ceil(window.jsInterface.getDefaultBannerHeight() / window.devicePixelRatio);
    if (bannerHeight > 0) {

      // The OSK is not available until initialization is complete.
      keyman.osk.bannerView.activeBannerHeight = bannerHeight;

      if(deferredBannerCall) {
        deferredBannerCall();
        deferredBannerCall = null;
      }

      keyman.refreshOskLayout();
    }
    // Initialize the longpress delay
    if(longpressDelay !== null) {
      setLongpressDelay(longpressDelay);
    }
  });

  keyman.addEventListener('keyboardloaded', setIsChiral);
  keyman.addEventListener('keyboardchange', setIsChiral);
  keyman.core.languageProcessor.on('statechange', onStateChange);

  // If the keyboard fails to display, this usually indicates that a Javascript
  // error has occurred, perhaps in the active keyboard. So we have a last-gasp
  // fallback in that situation; if the user touches the blank document, we will
  // attempt to reload the default keyboard.
  document.addEventListener('touchend', (event) => {
    if(event.target == document.documentElement) {
      // The error will be reported through to Sentry if the user has error
      // reporting enabled; breadcrumbs can give us helpful details on possible
      // root causes for the error
      window.console.error('user touched the document body, which can only happen if the keyboard was not presented. This is usually caused by another error.');
      notifyHost('reloadAfterError');
    }
  });

  notifyHost('pageLoaded');
}

function showBanner(flag) {
  if(!keyman.osk) {
    deferredBannerCall = function() {
      showBanner(flag);
    }

    return;
  }

  var bc = keyman.osk.bannerController;

  console_debug("Setting banner display for dictionaryless keyboards to " + flag);
  console_debug("bannerHTMLContents: " + bannerHTMLContents);
  if(bc) {
    if (bannerHTMLContents != '') {
      bc.inactiveBanner = flag ? new bc.HTMLBanner(bannerHTMLContents) : null;
    } else {
      bc.inactiveBanner = flag ? new bc.ImageBanner(bannerImagePath) : null;
    }
  }
}

function setBannerImage(path) {
  bannerImagePath = path;
}

// Set the HTML banner to use when predictive-text is not available
// contents - HTML content to use for the banner
function setBannerHTML(contents) {
  bannerHTMLContents = contents;
}

function notifyHost(event, params) {
  console_debug('notifyHost(event='+event+',params='+params+')');
  // TODO: Update all other host notifications to use notifyHost instead of directly setting window.location.hash
  window.setTimeout(function() {
    // We use a timeout so that the navigation doesn't cause the calling function to abort after the call
    fragmentToggle = (fragmentToggle + 1) % 100;
    params = params ? '+'+params : '';
    window.location.hash = event+'-'+fragmentToggle+params;
  }, 10);
}

// Update the KeymanWeb longpress delay
// delay is in milliseconds
function setLongpressDelay(delay) {
  longpressDelay = delay;
  if (keyman && keyman.osk) {
    keyman.osk.gestureParams.longpress.waitLength = delay;
    console_debug('setLongpressDelay('+delay+')');
  }
}

// Update the KMW banner height
// h is in dpi (different from iOS)
function setBannerHeight(h) {
  if (h > 0) {
    // The banner itself may not be loaded yet.  This will preemptively help set
    // its eventual display height.
    bannerHeight = Math.ceil(h / window.devicePixelRatio);

    if (keyman.osk) {
      keyman.osk.bannerView.activeBannerHeight = bannerHeight;
    }
  }

  // Refresh KMW's OSK
  keyman.refreshOskLayout();
}

function setOskHeight(h) {
  if(h > 0) {
    oskHeight = Math.ceil(h / window.devicePixelRatio);
  }
  if(keyman && keyman.core && keyman.core.activeKeyboard) {
    keyman.core.activeKeyboard.refreshLayouts();
  }
  keyman.refreshOskLayout();
}

function setOskWidth(w) {
  if(w > 0) {
    oskWidth = w / window.devicePixelRatio;
  }
}

function getOskHeight() {
  // banner height is calculated completely separately, unlike iOS
  return oskHeight;
}

function getOskWidth() {
  if(oskWidth <= 0) {
    oskWidth = window.jsInterface.getKeyboardWidth();
  }
  //window.console.log('Keyboard width = '+oskWidth);
  return oskWidth;
}

function beepKeyboard() {
  window.jsInterface.beepKeyboard();
}

function onStateChange(change) {
  //window.console.log('onStateChange change: ' + change);

  // Refresh KMW OSK
  keyman.refreshOskLayout();

  fragmentToggle = (fragmentToggle + 1) % 100;
  if(change != 'configured') {
    window.location.hash = 'refreshBannerHeight-'+fragmentToggle;
  }
}

// Query KMW if a given keyboard uses chiral modifiers.
function setIsChiral(keyboardProperties) {
  var name = typeof(keyboardProperties.internalName) == 'undefined' ? keyboardProperties.keyboardName : keyboardProperties.internalName;
  var isChiral = keyman.isChiral(name);
  window.jsInterface.setIsChiral(isChiral);
  return true;
}

function setKeymanLanguage(k) {
  KeymanWeb.registerStub(k);
  keyman.setActiveKeyboard(k.KI, k.KLC);
}

function setSpacebarText(mode) {
  var text = (mode == undefined) || !mode.text ? '' : mode.text;
  keyman.config.spacebarText = text;
}

// #6665: we need to know when the user has pressed a hardware key so we don't
// generate haptic feedback when text changes are made via insertText. We know
// that the executeHardware->insertText process is a non-reentrant
// single-threaded sequence so this global flag tracking whether we are
// currently executing a hardware keystroke event is safe here. We can't do this
// on the Java side, because the Java<-->JS interface is asynchronous, and we
// cannot reliably know when each hardware keystroke has completed processing.
var executingHardwareKeystroke = false;

/**
 * Inserts the selected string <i>s</i>
 * @param dn  Number of pre-caret code points (UTF+8 characters) to delete
 * @param s   Text to insert
 * @param dr  Number of post-caret code points to delete.  (optional)
 */
function insertText(dn, s, dr) {
  console_debug('insertText(dn='+dn+',s='+s+',dr='+dr+')');
  dr = dr || 0; // Sets a default value of zero when dr is undefined
  //window.console.log('insertText('+ dn +', ' + s +', ' + dr + ');');
  window.jsInterface.insertText(dn, s, dr, executingHardwareKeystroke);
}

function deregisterModel(modelID) {
  keyman.removeModel(modelID);
}

function enableSuggestions(model, suggestionType) {
  // Set the options first so that KMW's ModelCache can properly handle model enablement states
  // the moment we actually register the new model.
  // Use console_debug
  console_debug('enableSuggestions(model, maySuggest='+suggestionType+')');
  const suggestionSettings = [
    // mayPredict, mayCorrect, mayAutoCorrect
    [false, false, false],  // 0 = SuggestionType.SUGGESTIONS_DISABLED
    [true, false, false],   // 1 = SuggestionType.PREDICTIONS_ONLY
    [true, true, false],    // 2 = SuggestionType.PREDICTIONS_WITH_CORRECTIONS
    [true, true, true],     // 3 = SuggestionType.PREDICTIONS_WITH_AUTO_CORRECT
  ];
  const t = suggestionSettings[suggestionType]
    ? suggestionSettings[suggestionType] : suggestionSettings[0];
  keyman.core.languageProcessor.mayPredict = t[0];
  keyman.core.languageProcessor.maySuggest = t[1];
  keyman.core.languageProcessor.mayAutoCorrect = t[2];

  registerModel(model);
}

function setBannerOptions(mayPredict) {
  keyman.core.languageProcessor.mayPredict = mayPredict;
}

function registerModel(model) {
  //window.console.log('registerModel: ' + model);
  keyman.addModel(model);
}

function resetContext() {
  keyman.resetContext();
}

// Tell KMW to switch to "numeric" layer
function setNumericLayer() {
  if (keyman && keyman.core && keyman.core.activeKeyboard) {
    keyman.setNumericLayer();
  }
}

function updateKMText(k) {
  var text = (k == undefined) || !k.text ? '' : k.text;

  console_debug('updateKMText(text=' + text + ') with: \n' + build_context_string(keyman.context));

  if(!text || text != keyman.context.getText()) {
    keyman.context.setText(text);
    keyman.resetContext();


    console_debug('result: \n' + build_context_string(keyman.context));
  } else {
    console_debug('context unchanged');
  }
}

function console_debug(s) {
  if(_debug) {
    console.debug(s);
  }
}

function build_context_string(context) {
  // Sadly, ES6-style "template strings" - strings with backticks - require Chrome 41+.
  return 'preCaret: `' + context.getTextBeforeCaret() + '`\n' +
    'selected: `' + context.getSelectedText() + '`\n' +
    'postCaret: `' + context.getTextAfterCaret() + '`';
}

function updateKMSelectionRange(start, end) {
  var context = keyman.context;

  console_debug('updateKMSelectionRange(' + start + ', ' + end + ') with: \n' + build_context_string(context));

  if(start > end) {
    var e0 = end;
    end = start;
    start = e0;
  }

  if(context.selStart != start || context.selEnd != end) {
    keyman.context.setSelection(start, end);
    keyman.resetContext();

    console_debug('result:\n' + build_context_string(context));
  } else {
    console_debug('range unchanged');
  }
}

var lastKeyTip = null;

function signalHelpBubbleDismissal() {
  fragmentToggle = (fragmentToggle + 1) % 100;
  window.location.hash = 'helpBubbleDismissed-'+fragmentToggle;
}

function suggestionPopup(obj,custom,x,y,w,h) {
  if(obj != null) {
    var s = JSON.stringify(obj);

    fragmentToggle=(fragmentToggle+1) % 100;
    var hash = 'suggestPopup-' + fragmentToggle;

    hash = hash + '&x=' + encodeURIComponent(x) + '&y=' + encodeURIComponent(y);
    hash = hash + '&w=' + encodeURIComponent(w) + '&h=' + encodeURIComponent(h);
    hash = hash + '&suggestion=' + encodeURIComponent(s) + '&custom=' + encodeURIComponent(custom);

    window.location.hash = hash;
  }
}

// legacy function name for globe key down
function showMenu() {
  fragmentToggle = (fragmentToggle + 1) % 100;
  var hash = 'globeKeyAction&fragmentToggle=' + fragmentToggle + '&keydown=true';
  window.location.hash = hash;
}

// legacy function name for globe key up
function menuKeyUp() {
  fragmentToggle = (fragmentToggle + 1) % 100;
  var hash = 'globeKeyAction&fragmentToggle=' + fragmentToggle + '&keydown=false';
  window.location.hash = hash;
}

// The keyboard-picker displayed via Android longpress disrupts Web-side
// gesture-handling; this function helps force-clear the globe key's highlighting.
function clearGlobeHighlight() {
  if(keyman.osk && keyman.osk.vkbd && keyman.osk.vkbd.currentLayer.globeKey) {
    keyman.osk.vkbd.currentLayer.globeKey.highlight(false)
  }
}

function hideKeyboard() {
  fragmentToggle = (fragmentToggle + 1) % 100;
  window.location.hash = 'hideKeyboard' + fragmentToggle;
}

function showKeyboard() {
  // Refresh KMW OSK
  keyman.refreshOskLayout();
}

// Cannot make it explicitly async / await on API 21.
function executeHardwareKeystroke(code, shift, lstates, eventModifiers) {
  console_debug('executeHardwareKeystroke(code='+code+',shift='+shift+',lstates='+lstates+',eventModifiers='+eventModifiers+')');

  executingHardwareKeystroke = true;

  // Would be cleaner if we could async / await here, which would give us a simple try-catch implementation.
  var promise = keyman.hardKeyboard.raiseKeyEvent(code, shift, lstates);
  promise.then(function (result) {
    if(result) { // false if matched, true if not
      // KMW didn't process the key, so have the Android app dispatch the key with the original event modifiers
      window.jsInterface.dispatchKey(code, eventModifiers);
      executingHardwareKeystroke = false;
    }
  }).catch(function (e) {
    window.console.log('executeHardwareKeystroke exception: '+e);
    executingHardwareKeystroke = false;
  });
}

function popupVisible(value) {
  keyman.popupVisible(value);
}

function toHex(theString) {
  if (typeof theString != 'string') {
    return '';
  }
  var hexString = '';
  for (var i=0; i < theString.length; i++) {
    var theHex = theString.charCodeAt(i).toString(16).toUpperCase();
    while (theHex.length < 4) {
      theHex = '0' + theHex;
    }
    theHex = '\\u' + theHex;
    hexString += theHex;
  }
  return hexString;
}

let _debug = false;

if(_debug) {
    console = new Object();
    console.log = function(log) {
        var iframe = document.createElement("IFRAME");
        iframe.setAttribute("src", "ios-log:#iOS#" + log);
        document.documentElement.appendChild(iframe);
        iframe.parentNode.removeChild(iframe);
        iframe = null;
        if (typeof(window.webkit) != 'undefined') {
            window.webkit.messageHandlers.keyman.postMessage("ios-log:#iOS#" + log);
        }
    };
    console.debug = console.log;
    console.info = console.log;
    console.warn = console.log;
    console.error = console.log;
    window.onerror = function(error, url, line) {
        console.log('ERROR: '+error+' URL:'+url+' L:'+line);
    };
}

var oskHeight = 0;
var oskWidth = 0;
var bannerHeight = 0;
var bannerImgPath = '';

var sentryManager = new KeymanSentryManager({
    hostPlatform: "ios"
});

sentryManager.init();

window.addEventListener('load', init, false);

function init() {
    const device = navigator.userAgent.match(/iPad|Macintosh/) ? 'AppleTablet' : 'AppleMobile';

    // As of iOS 15, Safari WebViews will try to avoid letting us use the "safe area"
    // at the bottom of iPhone X style devices.  While `-webkit-fill-available` will partly
    // counteract this... it's only a "partly".  Fortunately, we can manually force the
    // page to our desired size.
    document.body.style.height = window.outerHeight;

    var kmw=window['keyman'];
    kmw['showKeyboardList'] = menuKeyDown;
    kmw['menuKeyUp'] = menuKeyUp;
    kmw['hideKeyboard'] = hideKeyboard;
    kmw['getOskHeight'] = getOskHeight;
    kmw['getOskWidth'] = getOskWidth;
    kmw['beepKeyboard'] = beepKeyboard;
    kmw.init({
        'embeddingApp':device,
        'fonts':'fonts/',
        oninserttext: insertText
    }).then(function() {
      if(bannerHeight > 0) {
        // The OSK is not available until initialization is complete.
        kmw.osk.bannerView.activeBannerHeight = bannerHeight;
        keyman.refreshOskLayout();
      }

      var bc = keyman.osk.bannerController;
      if(bannerImgPath) {
        bc.inactiveBanner = new bc.ImageBanner(bannerImgPath);
      }
    });
}

function verifyLoaded() {
    // During proper loads of KMW, a keyboard will be set very early on.
    // There's a chance that the keyboard is still loading, so we double-check
    // against the stub count.
    if(!keyman.core.activeKeyboard && !keyman.keyboardRequisitioner.cache.defaultStub) {
    location.reload();
    }
}

function setBannerImage(path) {
    bannerImgPath = path;

    var bc = keyman && keyman.osk && keyman.osk.bannerController;
    if(!bc) {
      return;
    }

    // If an inactive banner is set, update its image.
    bc.inactiveBanner = new bc.ImageBanner(bannerImgPath);
}

function setBannerHeight(h) {
  if(h >= 0) {
    // The banner itself may not be loaded yet.  This will preemptively help set
    // its eventual display height.
    bannerHeight = h;

    if(keyman.osk) {
        keyman.osk.bannerView.activeBannerHeight = h;
    }
  }

  // Refresh KMW's OSK
  keyman.refreshOskLayout();
  doResetContext();
}

function setOskHeight(height) {
    var kmw=window['keyman'];
    oskHeight = height;
    if(kmw && kmw.core && kmw.core.activeKeyboard) {
        kmw.core.activeKeyboard.refreshLayouts();
    }
    kmw.refreshOskLayout();
    doResetContext();
}

function setOskWidth(width) {
    oskWidth = width;
}

function getOskWidth() {
    if (oskWidth == 0)
        oskWidth = Math.abs(window.orientation) == 90 ? screen.height : screen.width;

    if (Math.abs(window.orientation) == 90) document.body.className="kmw-embedded keyman-app kmw-landscape";
    else document.body.className="kmw-embedded keyman-app kmw-portrait";

    return oskWidth;
}

function getOskHeight() {
    var height = oskHeight;
    if(keyman.osk && keyman.osk.banner && keyman.osk.banner._activeType != 'blank') {
        height = height - keyman.osk.banner.height;
    }
    return height;
}

var keyboardOffset = 0;

function keySenseLayoutLog(message) {

    if (typeof window.webkit !== 'undefined' &&
        window.webkit.messageHandlers &&
        window.webkit.messageHandlers.keyman) {

        window.webkit.messageHandlers.keyman.postMessage(
            'ios-log:#iOS#[KEYSENSE LAYOUT] ' + message
        );
    }
}

function keySenseSetInputModeSwitchKeyRequired(required) {

    window.keySenseInputModeSwitchKeyRequired =
        !!required;

    keySenseInstallEmojiKey();
}

window.keySenseSetInputModeSwitchKeyRequired =
    keySenseSetInputModeSwitchKeyRequired;

function keySenseInstallEmojiKey() {

    keySenseLayoutLog(
        'install called'
    );

    var keyboard =
        keyman &&
        keyman.core &&
        keyman.core.activeKeyboard;

    if (!keyboard) {

        keySenseLayoutLog(
            'FAILED: no activeKeyboard'
        );

        return;
    }

    keySenseLayoutLog(
        'active keyboard = ' +
        (
            keyboard.id ||
            keyboard.keyboardName ||
            'unknown'
        )
    );

    var layouts =
        keyboard._layouts;

    if (!layouts) {

        keySenseLayoutLog(
            'FAILED: activeKeyboard has no _layouts'
        );

        return;
    }

    var formFactors =
        Object.keys(layouts);

    keySenseLayoutLog(
        'form factors = ' +
        formFactors.join(',')
    );

    var bottomRowCount = 0;
    var insertionCount = 0;

    formFactors.forEach(
        function(formFactor) {

            var layout =
                layouts[formFactor];

            if (!layout ||
                !Array.isArray(layout.layer)) {

                return;
            }

            layout.layer.forEach(
                function(layer) {

                    if (!Array.isArray(layer.row)) {
                        return;
                    }

                    var bottomRow =
                        layer.row.find(
                            function(row) {

                                if (!Array.isArray(row.key)) {
                                    return false;
                                }

                                return row.key.some(
                                    function(key) {
                                        return key.id === 'K_SPACE';
                                    }
                                );
                            }
                        );

                    if (!bottomRow) {

                        keySenseLayoutLog(
                            'no space row in layer ' +
                            layer.id
                        );

                        return;
                    }

                    bottomRowCount += 1;
                  
                    var inputModeSwitchRequired =
                        window.keySenseInputModeSwitchKeyRequired === true;

                    if (!inputModeSwitchRequired) {

                        var globeIndex =
                            bottomRow.key.findIndex(
                                function(key) {
                                    return key.id === 'K_LOPT';
                                }
                            );

                        if (globeIndex >= 0) {

                            var globeKey =
                                bottomRow.key[globeIndex];

                            var globeWidth =
                                parseFloat(
                                    globeKey.width || '90'
                                );

                            bottomRow.key.splice(
                                globeIndex,
                                1
                            );

                            var spaceForGlobe =
                                bottomRow.key.find(
                                    function(key) {
                                        return key.id === 'K_SPACE';
                                    }
                                );

                            if (spaceForGlobe) {

                                var currentSpaceWidth =
                                    parseFloat(
                                        spaceForGlobe.width || '520'
                                    );

                                spaceForGlobe.width =
                                    String(
                                        currentSpaceWidth +
                                        globeWidth
                                    );
                            }

                            insertionCount += 1;

                            keySenseLayoutLog(
                                'removed K_LOPT from layer ' +
                                layer.id
                            );
                        }
                    }

                    var hasEmoji =
                        bottomRow.key.some(
                            function(key) {
                                return key.id ===
                                    'T_KEYSENSE_EMOJI';
                            }
                        );

                    var hasSettings =
                        bottomRow.key.some(
                            function(key) {
                                return key.id ===
                                    'T_KEYSENSE_SETTINGS';
                            }
                        );

                    if (hasEmoji && hasSettings) {

                        keySenseLayoutLog(
                            'controls already installed in layer ' +
                            layer.id
                        );

                        return;
                    }

                    var spaceIndex =
                        bottomRow.key.findIndex(
                            function(key) {
                                return key.id ===
                                    'K_SPACE';
                            }
                        );

                    if (spaceIndex < 0) {

                        keySenseLayoutLog(
                            'no K_SPACE in layer ' +
                            layer.id
                        );

                        return;
                    }

                    var spaceKey =
                        bottomRow.key[
                            spaceIndex
                        ];

                    var oldWidth =
                        parseFloat(
                            spaceKey.width || '610'
                        );

                    var keysToInsert = [];

                    if (!hasSettings) {

                        keysToInsert.push({
                            id:
                                'T_KEYSENSE_SETTINGS',

                            text:
                                '⚙️',

                            width:
                                '90',

                            sp:
                                '1'
                        });
                    }

                    if (!hasEmoji) {

                        keysToInsert.push({
                            id:
                                'T_KEYSENSE_EMOJI',

                            text:
                                '😀',

                            width:
                                '90',

                            sp:
                                '1'
                        });
                    }

                    spaceKey.width =
                        String(
                            Math.max(
                                300,
                                oldWidth -
                                    (90 * keysToInsert.length)
                            )
                        );

                    bottomRow.key.splice(
                        spaceIndex,
                        0,
                        ...keysToInsert
                    );

                    insertionCount +=
                        keysToInsert.length;

                    keySenseLayoutLog(
                        'inserted controls into layer ' +
                        layer.id +
                        ', space ' +
                        oldWidth +
                        ' -> ' +
                        spaceKey.width
                    );
                }
            );
        }
    );

    keySenseLayoutLog(
        'bottom rows = ' +
        bottomRowCount +
        ', insertions = ' +
        insertionCount
    );

    if (insertionCount > 0) {

        keyboard.refreshLayouts();

        keySenseLayoutLog(
            'osk exists = ' +
            !!(keyman && keyman.osk) +
            ', osk activeKeyboard exists = ' +
            !!(keyman &&
               keyman.osk &&
               keyman.osk.activeKeyboard)
        );

        if (keyman.osk &&
            keyman.osk.activeKeyboard) {

            keySenseLayoutLog(
                'rebuilding OSK after control insertion'
            );

            keyman.osk.activeKeyboard =
                keyman.osk.activeKeyboard;
        }
    }
}


function setKeymanLanguage(stub) {
    var kmw = window.keyman;

    KeymanWeb.registerStub(stub);

    kmw.setActiveKeyboard(stub.KI, stub.KLC).then(function() {

        keySenseInstallEmojiKey();

        // KEYSENSE:
        // Rebuild Keyman's internal touch-layout cache
        // after modifying KVKL.
        if (kmw.core &&
            kmw.core.activeKeyboard &&
            typeof kmw.core.activeKeyboard.refreshLayouts === 'function') {

            kmw.core.activeKeyboard.refreshLayouts();
        }

        kmw.refreshOskLayout();
        doResetContext();
        
        setTimeout(
            function() {

                var found =
                    document.documentElement
                        .innerHTML
                        .indexOf(
                            'T_KEYSENSE_EMOJI'
                        ) >= 0;

                var kv =
                    keyman &&
                    keyman.osk &&
                    keyman.osk.keyboardView;

                if (kv && kv.element) {

                    var rect =
                        kv.element.getBoundingClientRect();

                    var style =
                        window.getComputedStyle(kv.element);

                    keySenseLayoutLog(
                        'keyboardView DOM: ' +
                        'width=' + rect.width +
                        ', height=' + rect.height +
                        ', display=' + style.display +
                        ', visibility=' + style.visibility +
                        ', opacity=' + style.opacity
                    );

                } else {

                    keySenseLayoutLog(
                        'keyboardView DOM: MISSING'
                    );
                }
              
                var vk =
                    keyman &&
                    keyman.osk &&
                    keyman.osk.keyboardView;

                if (vk && vk.layerGroup) {

                    var layerGroup =
                        vk.layerGroup;

                    var activeLayer =
                        layerGroup.activeLayer;

                    var groupRect =
                        layerGroup.element.getBoundingClientRect();

                    var activeRect =
                        activeLayer && activeLayer.element
                            ? activeLayer.element.getBoundingClientRect()
                            : null;

                    var keyCount =
                        activeLayer && activeLayer.element
                            ? activeLayer.element
                                .querySelectorAll('.kmw-key').length
                            : -1;

                    keySenseLayoutLog(
                        'layerGroup: active=' +
                        layerGroup.activeLayerId +
                        ', group=' +
                        groupRect.width + 'x' +
                        groupRect.height +
                        ', activeExists=' +
                        !!activeLayer +
                        ', active=' +
                        (activeRect
                            ? activeRect.width + 'x' +
                              activeRect.height
                            : 'none') +
                        ', keys=' +
                        keyCount
                    );

                } else {

                    keySenseLayoutLog(
                        'layerGroup: MISSING'
                    );
                }
              
              if (activeLayer && activeLayer.element) {

                  var firstKey =
                      activeLayer.element.querySelector('.kmw-key');

                  if (firstKey) {

                      var keyRect =
                          firstKey.getBoundingClientRect();

                      var keyStyle =
                          window.getComputedStyle(firstKey);

                    keySenseLayoutLog(
                        'first key: ' +
                        'x=' + keyRect.x +
                        ', y=' + keyRect.y +
                        ', right=' + keyRect.right +
                        ', bottom=' + keyRect.bottom +
                        ', width=' + keyRect.width +
                        ', height=' + keyRect.height +
                        ', viewport=' +
                        window.innerWidth + 'x' +
                        window.innerHeight
                    );

                  } else {

                      keySenseLayoutLog(
                          'first key: MISSING'
                      );
                  }
                
                var centerX =
                    keyRect.left + keyRect.width / 2;

                var centerY =
                    keyRect.top + keyRect.height / 2;

                var topElement =
                    document.elementFromPoint(
                        centerX,
                        centerY
                    );

                if (topElement) {

                    var topStyle =
                        window.getComputedStyle(topElement);

                    keySenseLayoutLog(
                        'element over first key: ' +
                        'tag=' + topElement.tagName +
                        ', id=' + (topElement.id || 'none') +
                        ', class=' +
                        (topElement.className || 'none') +
                        ', zIndex=' +
                        topStyle.zIndex +
                        ', pointerEvents=' +
                        topStyle.pointerEvents
                    );

                } else {

                    keySenseLayoutLog(
                        'element over first key: NONE'
                    );
                }
                
                var keyText =
                    firstKey.querySelector('.kmw-key-text');

                if (keyText) {

                    var textRect =
                        keyText.getBoundingClientRect();

                    var textStyle =
                        window.getComputedStyle(keyText);

                    keySenseLayoutLog(
                        'first key text: ' +
                        'content="' +
                        keyText.textContent +
                        '"' +
                        ', size=' +
                        textRect.width + 'x' +
                        textRect.height +
                        ', color=' +
                        textStyle.color +
                        ', font=' +
                        textStyle.fontFamily +
                        ', fontSize=' +
                        textStyle.fontSize
                    );

                } else {

                    keySenseLayoutLog(
                        'first key text: MISSING'
                    );
                }
                var repaintTarget =
                    keyman &&
                    keyman.osk &&
                    keyman.osk.keyboardView &&
                    keyman.osk.keyboardView.element;

                if (repaintTarget) {

                    repaintTarget.style.webkitTransform =
                        'translateZ(0)';

                    repaintTarget.style.transform =
                        'translateZ(0)';

                    void repaintTarget.offsetHeight;

                    keySenseLayoutLog(
                        'forced keyboard repaint'
                    );
                }
              }
                
                keySenseLayoutLog(
                      'rendered DOM contains emoji key = ' +
                      found
                  );
            },
            100
        );
    });
  
}

function setHostAppName(name) {
    // Sentry will capture this in a breadcrumb for Web errors.
    keyman.config.embeddingApp = name;
}

var fragmentToggle = 0;
/**
 * Inserts the selected string <i>s</i>
 * @param dn  Number of pre-caret code points (UTF+8 characters) to delete
 * @param s   Text to insert
 * @param dr  Number of post-caret code points to delete.  (optional)
 */

// -----------------------------------------------------------------------------
// KEYSENSE
// Converts a Keyman OSK key event into a privacy-preserving behavioural category.
// No actual typed character is sent to Swift.
// -----------------------------------------------------------------------------

function keySenseClassifyKeyEvent(event) {
    if (!event || !event.isSynthetic) {
        return null;
    }

    const name = (event.kName || '').toUpperCase();

    // Explicit typing-control keys.
    if (name === 'K_BKSP') {
        return 'backspace';
    }

    if (name === 'K_SPACE') {
        return 'space';
    }

    if (name === 'K_ENTER') {
        return 'newline';
    }

    // Ignore non-text control/layout keys.
    const ignoredKeys = new Set([
        'K_SHIFT',
        'K_CAPS',
        'K_CTRL',
        'K_ALT',
        'K_MENU',
        'K_LCTRL',
        'K_RCTRL',
        'K_LALT',
        'K_RALT'
    ]);

    if (ignoredKeys.has(name)) {
        return null;
    }

    // Standard positional alphabet keys: K_A ... K_Z.
    if (/^K_[A-Z]$/.test(name)) {
        // Keyman's modifier flags use 0x10 for Shift.
        const shiftActive = (event.Lmodifiers & 0x10) !== 0;

        return shiftActive ? 'uppercase' : 'lowercase';
    }

    // Standard positional number keys: K_0 ... K_9.
    if (/^K_[0-9]$/.test(name)) {
        const shiftActive = (event.Lmodifiers & 0x10) !== 0;

        // e.g. Shift+1 -> ! rather than numeric input.
        return shiftActive ? 'special' : 'number';
    }

    // Word-internal punctuation.
    if (name === 'K_QUOTE' || name === 'K_HYPHEN') {
        return 'wordInternalSpecial';
    }

    // Unicode-key IDs used by many Keyman touch layouts:
    // U_0061, U_0041, U_0031, U_0021, etc.
    const unicodeMatch = /^U_([0-9A-F]{4,6})$/.exec(name);

    if (unicodeMatch) {
        const codePoint = parseInt(unicodeMatch[1], 16);

        if (!Number.isFinite(codePoint)) {
            return null;
        }

        const character = String.fromCodePoint(codePoint);

        // Equivalent representations of our explicit control keys.
        if (character === ' ') {
            return 'space';
        }

        if (character === '\n' || character === '\r') {
            return 'newline';
        }

        // Apostrophe / hyphen stay word-internal.
        if (character === "'" ||
            character === '\u2019' ||
            character === '-') {
            return 'wordInternalSpecial';
        }

        // Unicode-aware character classification.
        if (/^\p{Ll}$/u.test(character)) {
            return 'lowercase';
        }

        if (/^\p{Lu}$/u.test(character)) {
            return 'uppercase';
        }

        if (/^\p{N}$/u.test(character)) {
            return 'number';
        }

        return 'special';
    }

    // Known Keyman punctuation / symbol keys.
    const specialKeys = new Set([
        'K_COMMA',
        'K_PERIOD',
        'K_FULLSTOP',
        'K_SEMICOLON',
        'K_COLON',
        'K_SLASH',
        'K_BKSLASH',
        'K_LBRKT',
        'K_RBRKT',
        'K_EQUAL',
        'K_BKQUOTE'
    ]);

    if (specialKeys.has(name)) {
        return 'special';
    }

    // Unknown/control keys should NOT be guessed as typing.
    return null;
}

function keySenseSendKeyEvent(event) {
    const name =
        event && event.kName
            ? event.kName.toUpperCase()
            : '';

    if (name === 'T_KEYSENSE_EMOJI') {

        keySenseRequestEmojiPanel();

        return true;
    }
  
    if (name === 'T_KEYSENSE_SETTINGS') {

        keySenseRequestSettingsPanel();

        return true;
    }
  
    const category = keySenseClassifyKeyEvent(event);

    if (!category) {
        return;
    }

    if (typeof window.webkit !== 'undefined' &&
        window.webkit.messageHandlers &&
        window.webkit.messageHandlers.keyman) {

        window.webkit.messageHandlers.keyman.postMessage(
            '#keysenseKey-' + category
        );
    }
  return false;
}
window.keySenseSendKeyEvent = keySenseSendKeyEvent;

function keySenseRequestSettingsPanel() {
    if (typeof window.webkit !== 'undefined' &&
        window.webkit.messageHandlers &&
        window.webkit.messageHandlers.keyman) {

        window.webkit.messageHandlers.keyman.postMessage(
            '#keysenseShowSettings'
        );
    }
}

window.keySenseRequestSettingsPanel =
    keySenseRequestSettingsPanel;

function keySenseRequestEmojiPanel() {

    if (typeof window.webkit !== 'undefined' &&
        window.webkit.messageHandlers &&
        window.webkit.messageHandlers.keyman) {

        window.webkit.messageHandlers.keyman.postMessage(
            '#keysenseShowEmoji'
        );
    }
}

window.keySenseRequestEmojiPanel =
    keySenseRequestEmojiPanel;


function insertText(dn, s, dr) {
    dr = dr || 0;  // Sets a default value of zero when dr is undefined
    fragmentToggle = (fragmentToggle + 1) % 100;
    var insertHash = 'insertText-'+ fragmentToggle + '+dn=' + dn + '+s=' + toHex(s) + '+dr=' + dr;
    if (typeof(window.webkit) != 'undefined')
        window.webkit.messageHandlers.keyman.postMessage('#' + insertHash);
}

function beepKeyboard() {
    fragmentToggle = (fragmentToggle + 1) % 100;
    var beepHash = 'beep-'+ fragmentToggle;
    if(typeof(window.webkit) != 'undefined') {
        window.webkit.messageHandlers.keyman.postMessage('#' + beepHash);
    }
}

function oskCreateKeyPreview(x,y,w,h,t)
{
    fragmentToggle = (fragmentToggle + 1) % 100;
    var div = document.createElement('div');
    div.innerHTML = t;
    var dt = div.firstChild.nodeValue;
    var previewHash = 'showKeyPreview-'+fragmentToggle+'+x='+x+'+y='+y+'+w='+w+'+h='+h+'+t='+toHex(dt);
    //window.location.hash = 'showKeyPreview-'+fragmentToggle+'+x='+x+'+y='+y+'+w='+w+'+h='+h+'+t='+toHex(dt);
    if (typeof(window.webkit) != 'undefined')
        window.webkit.messageHandlers.keyman.postMessage('#' + previewHash);
}

function oskClearKeyPreview()
{
    fragmentToggle = (fragmentToggle + 1) % 100;
    var dismissHash = 'dismissKeyPreview-'+fragmentToggle;
    //window.location.hash = 'dismissKeyPreview-'+fragmentToggle;
    if (typeof(window.webkit) != 'undefined')
        window.webkit.messageHandlers.keyman.postMessage('#' + dismissHash);
}

oskCreatePopup = function(obj,x,y,w,h)
{
    if(obj != null) {
        var i;
        var s = '';
        var shift = false;
        var frame = x.toString() + ',' + y.toString() + ',' + w.toString() + ',' + h.toString();
        for(i=0; i<obj.length; i++)
        {
            s=s+obj[i].layer+'-'+obj[i].id;
            if(obj[i].sp == 1 || obj[i].sp == 2) shift = true;
            if(typeof(obj[i].text) != 'undefined' && obj[i].text != '') s=s+':'+toHex(obj[i].text);
            if(i < (obj.length -1)) s=s+';'
        }
        fragmentToggle=(fragmentToggle+1) % 100;
        var hash = 'showMore-' + fragmentToggle + '+baseFrame=' + frame + '+keys=' + s;
        if(shift) {
            hash = hash + '+font=' + 'SpecialOSK';
        }
        //window.location.hash = hash;
        if (typeof(window.webkit) != 'undefined')
            window.webkit.messageHandlers.keyman.postMessage('#' + hash);
    }
}

function suggestionPopup(obj,custom,x,y,w,h) {
    if(obj != null) {
    fragmentToggle=(fragmentToggle+1) % 100;

    var cmd = {
        'suggestion': obj,
        'isCustom': custom,
        'x': x,
        'y': y,
        'width': w,
        'height': h
    };

    var hash = 'suggestPopup-' + fragmentToggle + '+cmd=' + JSON.stringify(cmd);

    if (typeof(window.webkit) != 'undefined') {
        window.webkit.messageHandlers.keyman.postMessage('#' + hash);
    }
    }
}

function menuKeyDown() {
    fragmentToggle = (fragmentToggle + 1) % 100;
    var keyDownHash = 'menuKeyDown-' + fragmentToggle;
    //window.location.hash = 'menuKeyDown-' + fragmentToggle;
    if (typeof(window.webkit) != 'undefined')
        window.webkit.messageHandlers.keyman.postMessage('#' + keyDownHash);
}

function menuKeyUp()
{
    fragmentToggle = (fragmentToggle + 1) % 100;
    var keyUpHash = 'menuKeyUp-'+fragmentToggle;
    //window.location.hash = 'menuKeyUp-'+fragmentToggle;
    if (typeof(window.webkit) != 'undefined')
        window.webkit.messageHandlers.keyman.postMessage('#' + keyUpHash);
}

function hideKeyboard() {
    fragmentToggle = (fragmentToggle + 1) % 100;
    var hideHash = 'hideKeyboard-' + fragmentToggle;
    //window.location.hash = 'hideKeyboard-' + fragmentToggle;
    if (typeof(window.webkit) != 'undefined')
        window.webkit.messageHandlers.keyman.postMessage('#' + hideHash);
}

function langMenuPos() {
    var kmw = window['keyman'];
    var pos = kmw['touchMenuPos']();
    return pos;
}

function doResetContext() {
    keyman.resetContext();
}

function setKeymanContext(text, doSync, selStart, selLength) {
    // console.log(`setKeymanContext(${JSON.stringify(text)}, ${doSync}, ${pos}, ${length})`);
    if(text == undefined) {
        text = '';
    }

    // Both pos + length are optional parameters.
    // undefined + <number> => NaN; undefined + undefined => NaN.
    let selEnd = selStart + selLength;
    selEnd = isNaN(selEnd) ? undefined : selEnd;

    if(doSync) {
      const shouldReset = keyman.context.updateContext(text, selStart, selEnd);
      if(shouldReset) {
        keyman.resetContext();
      }
    } else {
      // if not in "sync" mode, we have a hard context reset; just full-reset it.
      keyman.context.setText(text);
      keyman.context.setSelection(selStart, selStart+selLength);
      keyman.resetContext();
    }
}

function executePopupKey(keyID, keyText) {
    var kmw=window['keyman'];
    kmw['executePopupKey'](keyID, keyText);
}

function popupVisible(value) {
    var kmw=window['keyman'];
    kmw['popupVisible'](value);
}

function toHex(theString) {
    var hexString = '';
    for (var i=0; i < theString.length; i++) {
        var theHex = theString.charCodeAt(i).toString(16).toUpperCase();
        while (theHex.length < 4) {
            theHex = '0' + theHex;
        }
        theHex = '0x' + theHex;
        hexString += theHex + ',';
    }
    return hexString.substr(0, hexString.length-1);
}

function enableSuggestions(model, mayPredict, mayCorrect, mayAutocorrect) {
    // Set the options first so that KMW's ModelCache can properly handle model enablement states
    // the moment we actually register the new model.
    keyman.core.languageProcessor.mayPredict = mayPredict;
    keyman.core.languageProcessor.mayCorrect = mayCorrect;
    keyman.core.languageProcessor.mayAutoCorrect = mayAutocorrect;

    keyman.addModel(model);
}

function setSpacebarText(mode) {
    keyman.config.spacebarText = mode;
}


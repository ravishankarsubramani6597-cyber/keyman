
package com.keyman.engine;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

/**
 * KEYSENSE: Determines whether an input field is eligible
 * for privacy-preserving typing metadata collection.
 *
 * No typed text is inspected or stored.
 */
public final class KeySenseInputEligibility {

    private KeySenseInputEligibility() {
        // Utility class
    }

    public static boolean isEligible(EditorInfo editorInfo) {

        // Fail closed when editor information is unavailable.
        if (editorInfo == null) {
            return false;
        }

        // Respect requests to avoid personalized typing history.
        if ((editorInfo.imeOptions
                & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) {
            return false;
        }

        int inputType = editorInfo.inputType;
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;

        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            switch (variation) {
                case InputType.TYPE_TEXT_VARIATION_PASSWORD:
                case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
                case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
                    return false;

                default:
                    return true;
            }
        }

        if (inputClass == InputType.TYPE_CLASS_NUMBER) {
            return variation != InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        }

        // Exclude unrecognized, phone, and date/time fields.
        return false;
    }
}

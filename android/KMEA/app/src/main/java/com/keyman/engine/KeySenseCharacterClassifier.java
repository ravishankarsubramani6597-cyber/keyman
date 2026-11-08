
package com.keyman.engine;

/**
 * KEYSENSE Android character-event classifier.
 *
 * Mirrors the iOS typing-event taxonomy.
 *
 * This class performs classification only.
 * It does not store text, start sessions, or update metrics.
 */
public final class KeySenseCharacterClassifier {

    public enum EventKind {
        LOWERCASE,
        UPPERCASE,
        NUMBER,
        SPECIAL_WORD_INTERNAL,
        SPECIAL_OTHER,
        SPACE,
        NEWLINE,
        BACKSPACE,
        EMOJI
    }

    private KeySenseCharacterClassifier() {
        // Utility class.
    }

    /**
     * Classify one Unicode code point.
     *
     * The wordInternalSpecial flag must come from
     * a separately validated classification rule.
     *
     * Do not use this method to classify emoji
     * sequences or entire text replacements.
     */
    public static EventKind classifyCodePoint(
            int codePoint,
            boolean wordInternalSpecial) {

        if (!Character.isValidCodePoint(codePoint)
                || (codePoint >= 0xD800
                    && codePoint <= 0xDFFF)) {
            throw new IllegalArgumentException(
                    "Invalid Unicode code point");
        }

        if (codePoint == ' ') {
            return EventKind.SPACE;
        }

        if (codePoint == '\n' || codePoint == '\r') {
            return EventKind.NEWLINE;
        }

        if (Character.isLowerCase(codePoint)) {
            return EventKind.LOWERCASE;
        }

        if (Character.isUpperCase(codePoint)) {
            return EventKind.UPPERCASE;
        }

        if (Character.isDigit(codePoint)) {
            return EventKind.NUMBER;
        }

        if (wordInternalSpecial) {
            return EventKind.SPECIAL_WORD_INTERNAL;
        }

        return EventKind.SPECIAL_OTHER;
    }

    /**
     * An explicitly verified user backspace action.
     */
    public static EventKind backspaceEvent() {
        return EventKind.BACKSPACE;
    }

    /**
     * An explicitly verified emoji insertion event.
     *
     * No emoji identity is retained.
     */
    public static EventKind emojiEvent() {
        return EventKind.EMOJI;
    }
}

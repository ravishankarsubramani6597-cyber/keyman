
package com.keyman.engine;

/**
 * KEYSENSE: Classifies editing operations received
 * through the Keyman Android JavaScript bridge.
 *
 * Does not retain typed text.
 */
public final class KeySenseEventClassifier {

    public enum EditType {
        INSERTION,
        DELETION,
        REPLACEMENT,
        NO_CHANGE
    }

    private KeySenseEventClassifier() {
        // Utility class
    }

    public static EditType classify(
            int deleteLeft,
            String insertedText,
            int deleteRight) {

        boolean hasInsertion =
                insertedText != null && !insertedText.isEmpty();

        boolean hasDeletion =
                deleteLeft > 0 || deleteRight > 0;

        if (hasInsertion && hasDeletion) {
            return EditType.REPLACEMENT;
        }

        if (hasInsertion) {
            return EditType.INSERTION;
        }

        if (hasDeletion) {
            return EditType.DELETION;
        }

        return EditType.NO_CHANGE;
    }
}

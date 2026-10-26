import Foundation
import Dispatch

// MARK: - Typing event taxonomy

enum KeySenseSpecialKind {
    case wordInternal
    case other
}

enum KeySenseTypingEventKind {
    case lowercase
    case uppercase
    case number
    case special(KeySenseSpecialKind)
    case space
    case newline
    case backspace
    /// The String is transient and must never be persisted.
    case emoji(transientId: String)
}

enum KeySenseSessionEndReason: String, Codable {
    case inactivityTimeout = "inactivity_timeout"
    case keyboardDismissed = "keyboard_dismissed"
    case inputContextChanged = "input_context_changed"
    case appContextChanged = "app_context_changed"
    case orientationChanged = "orientation_changed"
    case extensionTerminated = "extension_terminated"
    case other
}

struct KeySenseEventTime {
    let utcMs: Int64
    let uptimeNs: UInt64
    let timezoneOffsetMinutes: Int64
}

protocol KeySenseClock {
    func capture() -> KeySenseEventTime
}

struct KeySenseSystemClock: KeySenseClock {
    func capture() -> KeySenseEventTime {
        let now = Date()
        return KeySenseEventTime(
            utcMs: Int64((now.timeIntervalSince1970 * 1_000.0).rounded()),
            uptimeNs: DispatchTime.now().uptimeNanoseconds,
            timezoneOffsetMinutes: Int64(TimeZone.current.secondsFromGMT(for: now) / 60)
        )
    }
}

enum KeySenseOrientation: String, Codable {
    case portrait
    case portraitUpsideDown
    case landscapeLeft
    case landscapeRight
    case unknown
}

// MARK: - Persisted session record

struct KeySenseTypingSessionV1: Codable {
    // Technical/session metadata
    let schemaVersion: Int64
    let participantId: String
    let sessionId: String
    let platform: String
    let keyboardVersion: String
    let orientation: KeySenseOrientation
    let sessionStartUtcMs: Int64
    let sessionEndUtcMs: Int64
    let timezoneOffsetMinutes: Int64
    let sessionDurationMs: Int64
    let sessionEndReason: KeySenseSessionEndReason
    let pauseThresholdMs: Int64
    let sessionTimeoutMs: Int64
    let appIdentifier: String?
    let appCategory: String?

    // Basic typing activity
    let totalKeyCount: Int64
    let characterCount: Int64
    let lowercaseCount: Int64
    let uppercaseCount: Int64
    let numericCount: Int64
    let specialCharCount: Int64
    let spaceCount: Int64
    let newlineCount: Int64
    let backspaceCount: Int64
    let wordCount: Int64
  
    //Prediction Selection Count
    let predictionSelectionCount: Int64

    // Emoji behaviour
    let emojiCount: Int64
    let uniqueEmojiCount: Int64
    let emojiBurstCount: Int64
    let maxEmojiRunLength: Int64
    let repeatedEmojiCount: Int64

    // Active inter-key timing
    let activeIkiCount: Int64
    let interKeyIntervalMeanMs: Double?
    let interKeyIntervalMedianMs: Double?
    let interKeyIntervalSdMs: Double?
    let interKeyIntervalMinMs: Double?
    let interKeyIntervalMaxMs: Double?

    // Pausing
    let pauseCount: Int64
    let totalPauseDurationMs: Int64
    let meanPauseDurationMs: Double?
    let maxPauseDurationMs: Double?
    let pauseTimeRatio: Double?

    // Typing bursts
    let typingBurstCount: Int64
    let meanBurstLength: Double?
    let maxBurstLength: Int64

    // Editing/text-production behaviour
    let deletionRate: Double?
    let charactersPerWord: Double?
    let newlinesPerWord: Double?

    // Typing speed/intensity
    let keysPerSecond: Double?
    let charactersPerSecond: Double?
    let wordsPerMinute: Double?
    let activeTypingDurationMs: Int64
    let activeTypingRate: Double?

    // Typing consistency
    let typingIntervalCv: Double?

    // Emoji-derived behaviour
    let emojiRate: Double?
    let emojiDiversityRatio: Double?
}

// MARK: - Persistence boundary

protocol KeySenseSessionRepository: AnyObject {
    /// Must synchronously and atomically persist the aggregate session record.
    /// It must never persist transient event content.
    func insert(_ session: KeySenseTypingSessionV1) throws
}

enum KeySenseValidationError: Error, CustomStringConvertible {
    case characterCountMismatch
    case totalKeyCountMismatch
    case invalidTimeRange
    case negativeDuration
    case negativePauseDuration
    case pauseDurationExceedsSession
    case uniqueEmojiCountExceedsEmojiCount
    case invalidPauseTimeRatio
    case invalidEmojiDiversityRatio

    var description: String {
        switch self {
        case .characterCountMismatch: return "characterCount does not equal the sum of character-category counts."
        case .totalKeyCountMismatch: return "totalKeyCount does not equal characterCount + backspaceCount."
        case .invalidTimeRange: return "sessionEndUtcMs is earlier than sessionStartUtcMs."
        case .negativeDuration: return "sessionDurationMs is negative."
        case .negativePauseDuration: return "totalPauseDurationMs is negative."
        case .pauseDurationExceedsSession: return "totalPauseDurationMs exceeds sessionDurationMs."
        case .uniqueEmojiCountExceedsEmojiCount: return "uniqueEmojiCount exceeds emojiCount."
        case .invalidPauseTimeRatio: return "pauseTimeRatio is outside [0, 1]."
        case .invalidEmojiDiversityRatio: return "emojiDiversityRatio is outside [0, 1]."
        }
    }
}

extension KeySenseTypingSessionV1 {
    func validate() throws {
        let expectedCharacterCount = lowercaseCount
            + uppercaseCount
            + numericCount
            + specialCharCount
            + spaceCount
            + newlineCount
            + emojiCount

        guard characterCount == expectedCharacterCount else {
            throw KeySenseValidationError.characterCountMismatch
        }

        guard totalKeyCount == characterCount + backspaceCount else {
            throw KeySenseValidationError.totalKeyCountMismatch
        }

        guard sessionEndUtcMs >= sessionStartUtcMs else {
            throw KeySenseValidationError.invalidTimeRange
        }

        guard sessionDurationMs >= 0 else {
            throw KeySenseValidationError.negativeDuration
        }

        guard totalPauseDurationMs >= 0 else {
            throw KeySenseValidationError.negativePauseDuration
        }

        guard totalPauseDurationMs <= sessionDurationMs else {
            throw KeySenseValidationError.pauseDurationExceedsSession
        }

        guard uniqueEmojiCount <= emojiCount else {
            throw KeySenseValidationError.uniqueEmojiCountExceedsEmojiCount
        }

        if let pauseTimeRatio, !(0.0 ... 1.0).contains(pauseTimeRatio) {
            throw KeySenseValidationError.invalidPauseTimeRatio
        }

        if let emojiDiversityRatio, !(0.0 ... 1.0).contains(emojiDiversityRatio) {
            throw KeySenseValidationError.invalidEmojiDiversityRatio
        }
    }
}

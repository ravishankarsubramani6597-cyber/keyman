import Foundation

/// Mutable, RAM-only state for one active typing session.
/// This type intentionally contains transient state that must never be persisted directly.
final class KeySenseSessionAccumulator {
    let sessionId: String
    let sessionStartUtcMs: Int64
    let sessionStartUptimeNs: UInt64
    let timezoneOffsetMinutes: Int64

    private(set) var lastEventUtcMs: Int64
    private(set) var lastEventUptimeNs: UInt64
  
    // Keyboard Orientation
    private let orientation: KeySenseOrientation

    // Basic typing counts
    private(set) var lowercaseCount: Int64 = 0
    private(set) var uppercaseCount: Int64 = 0
    private(set) var numericCount: Int64 = 0
    private(set) var specialCharCount: Int64 = 0
    private(set) var spaceCount: Int64 = 0
    private(set) var newlineCount: Int64 = 0
    private(set) var backspaceCount: Int64 = 0
    private(set) var wordCount: Int64 = 0
  
    //Prediction Character Count
    private(set) var predictionSelectionCount: Int64 = 0

    // Word state (RAM-only)
    private var insideWord = false

    // Emoji state
    private(set) var emojiCount: Int64 = 0
    private(set) var emojiBurstCount: Int64 = 0
    private(set) var maxEmojiRunLength: Int64 = 0
    private(set) var repeatedEmojiCount: Int64 = 0
    private var transientEmojiSet = Set<String>()
    private var previousEmojiId: String?
    private var currentEmojiRunLength: Int64 = 0

    // Active IKI state (Welford + temporary array for exact median)
    private(set) var activeIkiCount: Int64 = 0
    private var ikiMeanMs: Double = 0
    private var ikiM2: Double = 0
    private var ikiMinMs: Double?
    private var ikiMaxMs: Double?
    private var activeIkiValuesMs: [Double] = []

    // Pause state
    private(set) var pauseCount: Int64 = 0
    private var totalPauseDurationExactMs: Double = 0
    private var maxPauseDurationExactMs: Double?

    // Burst state
    private(set) var typingBurstCount: Int64 = 1
    private var currentBurstLength: Int64 = 0
    private(set) var maxBurstLength: Int64 = 0

    init(
        firstEventTime: KeySenseEventTime,
        orientation: KeySenseOrientation
    ) {
        sessionId = UUID().uuidString
        sessionStartUtcMs = firstEventTime.utcMs
        sessionStartUptimeNs = firstEventTime.uptimeNs
        timezoneOffsetMinutes = firstEventTime.timezoneOffsetMinutes
        lastEventUtcMs = firstEventTime.utcMs
        lastEventUptimeNs = firstEventTime.uptimeNs
        self.orientation = orientation
      }

    var characterCount: Int64 {
        lowercaseCount
            + uppercaseCount
            + numericCount
            + specialCharCount
            + spaceCount
            + newlineCount
            + emojiCount
    }

    var totalKeyCount: Int64 {
        characterCount + backspaceCount
    }

    var uniqueEmojiCount: Int64 {
        Int64(transientEmojiSet.count)
    }

    func updateLastEventTime(_ time: KeySenseEventTime) {
        lastEventUtcMs = time.utcMs
        lastEventUptimeNs = time.uptimeNs
    }

    // MARK: Timing

    func registerActiveIki(milliseconds x: Double) {
        guard x > 0 else { return }

        activeIkiCount += 1
        let n = Double(activeIkiCount)
        let delta = x - ikiMeanMs
        ikiMeanMs += delta / n
        let delta2 = x - ikiMeanMs
        ikiM2 += delta * delta2

        if ikiMinMs == nil || x < ikiMinMs! { ikiMinMs = x }
        if ikiMaxMs == nil || x > ikiMaxMs! { ikiMaxMs = x }
        activeIkiValuesMs.append(x)
    }

    func registerPause(milliseconds x: Double) {
        guard x >= 0 else { return }

        pauseCount += 1
        totalPauseDurationExactMs += x

        if maxPauseDurationExactMs == nil || x > maxPauseDurationExactMs! {
            maxPauseDurationExactMs = x
        }

        finalizeCurrentBurst()
        typingBurstCount += 1
        currentBurstLength = 0
        resetEmojiRun()
    }

    // MARK: Event aggregation

    func process(_ event: KeySenseTypingEventKind) {
        switch event {
        case .lowercase:
            lowercaseCount += 1
            insideWord = true
            resetEmojiRun()

        case .uppercase:
            uppercaseCount += 1
            insideWord = true
            resetEmojiRun()

        case .number:
            numericCount += 1
            insideWord = true
            resetEmojiRun()

        case .special(let specialKind):
            specialCharCount += 1
            if specialKind == .other {
                finalizePendingWord()
            }
            resetEmojiRun()

        case .space:
            finalizePendingWord()
            spaceCount += 1
            resetEmojiRun()

        case .newline:
            finalizePendingWord()
            newlineCount += 1
            resetEmojiRun()

        case .backspace:
            backspaceCount += 1
            // Deliberately do not reverse prior category/word counts.
            resetEmojiRun()

        case .emoji(let transientId):
            // Emoji acts as a word boundary, but does not itself create a word.
            finalizePendingWord()
            emojiCount += 1
            transientEmojiSet.insert(transientId)

            if currentEmojiRunLength == 0 {
                emojiBurstCount += 1
            }

            if previousEmojiId == transientId {
                repeatedEmojiCount += 1
            }

            currentEmojiRunLength += 1
            if currentEmojiRunLength > maxEmojiRunLength {
                maxEmojiRunLength = currentEmojiRunLength
            }
            previousEmojiId = transientId
        }

        currentBurstLength += 1
    }
  
    func registerPredictionSelection() {
        predictionSelectionCount += 1
    }

    // MARK: Finalization

    func makeRecord(
        participantId: String,
        keyboardVersion: String,
        endReason: KeySenseSessionEndReason,
        pauseThresholdMs: Int64,
        sessionTimeoutMs: Int64,
        appIdentifier: String?,
        appCategory: String?
    ) -> KeySenseTypingSessionV1 {
        finalizePendingWord()
        finalizeCurrentBurst()

        let durationExactMs = Double(lastEventUptimeNs - sessionStartUptimeNs) / 1_000_000.0
        let sessionDurationMs = max(0, Int64(durationExactMs.rounded()))

        let totalPauseDurationMs = max(0, Int64(totalPauseDurationExactMs.rounded()))
        let activeTypingDurationMs = max(0, sessionDurationMs - totalPauseDurationMs)

        let meanIki: Double? = activeIkiCount > 0 ? ikiMeanMs : nil
        let medianIki = median(activeIkiValuesMs)
        let sdIki: Double? = activeIkiCount >= 2
            ? sqrt(max(0, ikiM2 / Double(activeIkiCount)))
            : nil

        let meanPause: Double? = pauseCount > 0
            ? totalPauseDurationExactMs / Double(pauseCount)
            : nil

        let pauseTimeRatio: Double? = sessionDurationMs > 0
            ? min(1.0, max(0.0, Double(totalPauseDurationMs) / Double(sessionDurationMs)))
            : nil

        let meanBurstLength: Double? = typingBurstCount > 0
            ? Double(totalKeyCount) / Double(typingBurstCount)
            : nil

        let deletionRate: Double? = characterCount > 0
            ? Double(backspaceCount) / Double(characterCount)
            : nil

        let textCharacterCount = lowercaseCount + uppercaseCount + numericCount + specialCharCount

        let charactersPerWord: Double? = wordCount > 0
            ? Double(textCharacterCount) / Double(wordCount)
            : nil

        let newlinesPerWord: Double? = wordCount > 0
            ? Double(newlineCount) / Double(wordCount)
            : nil

        let keysPerSecond: Double? = sessionDurationMs > 0
            ? (Double(totalKeyCount) * 1_000.0) / Double(sessionDurationMs)
            : nil

        let charactersPerSecond: Double? = sessionDurationMs > 0
            ? (Double(characterCount) * 1_000.0) / Double(sessionDurationMs)
            : nil

        let wordsPerMinute: Double? = sessionDurationMs > 0
            ? (Double(wordCount) * 60_000.0) / Double(sessionDurationMs)
            : nil

        let activeTypingRate: Double? = activeTypingDurationMs > 0
            ? (Double(totalKeyCount) * 1_000.0) / Double(activeTypingDurationMs)
            : nil

        let typingIntervalCv: Double? = {
            guard activeIkiCount >= 2,
                  let sdIki,
                  ikiMeanMs > 0 else { return nil }
            return sdIki / ikiMeanMs
        }()

        let emojiRate: Double? = characterCount > 0
            ? Double(emojiCount) / Double(characterCount)
            : nil

        let emojiDiversityRatio: Double? = emojiCount > 0
            ? Double(uniqueEmojiCount) / Double(emojiCount)
            : nil
      
        enum KeySenseOrientation: String, Codable {
            case portrait
            case portraitUpsideDown
            case landscapeLeft
            case landscapeRight
            case unknown
        }

        return KeySenseTypingSessionV1(
            schemaVersion: 1,
            participantId: participantId,
            sessionId: sessionId,
            platform: "ios",
            keyboardVersion: keyboardVersion,
            orientation: orientation,
            sessionStartUtcMs: sessionStartUtcMs,
            sessionEndUtcMs: lastEventUtcMs,
            timezoneOffsetMinutes: timezoneOffsetMinutes,
            sessionDurationMs: sessionDurationMs,
            sessionEndReason: endReason,
            pauseThresholdMs: pauseThresholdMs,
            sessionTimeoutMs: sessionTimeoutMs,
            appIdentifier: appIdentifier,
            appCategory: appCategory,
            totalKeyCount: totalKeyCount,
            characterCount: characterCount,
            lowercaseCount: lowercaseCount,
            uppercaseCount: uppercaseCount,
            numericCount: numericCount,
            specialCharCount: specialCharCount,
            spaceCount: spaceCount,
            newlineCount: newlineCount,
            backspaceCount: backspaceCount,
            wordCount: wordCount,
            predictionSelectionCount: predictionSelectionCount,
            emojiCount: emojiCount,
            uniqueEmojiCount: uniqueEmojiCount,
            emojiBurstCount: emojiBurstCount,
            maxEmojiRunLength: maxEmojiRunLength,
            repeatedEmojiCount: repeatedEmojiCount,
            activeIkiCount: activeIkiCount,
            interKeyIntervalMeanMs: meanIki,
            interKeyIntervalMedianMs: medianIki,
            interKeyIntervalSdMs: sdIki,
            interKeyIntervalMinMs: ikiMinMs,
            interKeyIntervalMaxMs: ikiMaxMs,
            pauseCount: pauseCount,
            totalPauseDurationMs: totalPauseDurationMs,
            meanPauseDurationMs: meanPause,
            maxPauseDurationMs: maxPauseDurationExactMs,
            pauseTimeRatio: pauseTimeRatio,
            typingBurstCount: typingBurstCount,
            meanBurstLength: meanBurstLength,
            maxBurstLength: maxBurstLength,
            deletionRate: deletionRate,
            charactersPerWord: charactersPerWord,
            newlinesPerWord: newlinesPerWord,
            keysPerSecond: keysPerSecond,
            charactersPerSecond: charactersPerSecond,
            wordsPerMinute: wordsPerMinute,
            activeTypingDurationMs: activeTypingDurationMs,
            activeTypingRate: activeTypingRate,
            typingIntervalCv: typingIntervalCv,
            emojiRate: emojiRate,
            emojiDiversityRatio: emojiDiversityRatio
        )
    }

    // MARK: Private helpers

    private func finalizePendingWord() {
        if insideWord {
            wordCount += 1
            insideWord = false
        }
    }

    private func resetEmojiRun() {
        currentEmojiRunLength = 0
        previousEmojiId = nil
    }

    private func finalizeCurrentBurst() {
        if currentBurstLength > maxBurstLength {
            maxBurstLength = currentBurstLength
        }
    }

    private func median(_ values: [Double]) -> Double? {
        guard !values.isEmpty else { return nil }
        let sorted = values.sorted()
        let middle = sorted.count / 2
        if sorted.count.isMultiple(of: 2) {
            return (sorted[middle - 1] + sorted[middle]) / 2.0
        }
        return sorted[middle]
    }
}

import Foundation
import Dispatch

/// Owns session segmentation and converts abstract keyboard events into one aggregate DB record.
/// Call this object from the keyboard extension's main thread.
final class KeySenseTypingSessionManager {
    static let pauseThresholdMs: Int64 = 2_000
    static let sessionTimeoutMs: Int64 = 30_000

    private static let pauseThresholdNs: UInt64 = 2_000_000_000
    private static let sessionTimeoutNs: UInt64 = 30_000_000_000

    private let repository: KeySenseSessionRepository
    private let participantId: String
    private let keyboardVersion: String
    private let clock: KeySenseClock
  
    private let orientationProvider: () -> KeySenseOrientation

    /// iOS does not expose a supported host-app identifier to custom keyboards.
    /// Keep these nil unless your existing architecture has a legitimate, supported source.
    private let appIdentifierProvider: () -> String?
    private let appCategoryProvider: () -> String?

    private var accumulator: KeySenseSessionAccumulator?
    private var timeoutWorkItem: DispatchWorkItem?

    var onPersistenceError: ((Error) -> Void)?
    var onSessionPersisted: ((KeySenseTypingSessionV1) -> Void)?

    init(
        repository: KeySenseSessionRepository,
        participantId: String,
        keyboardVersion: String,
        clock: KeySenseClock = KeySenseSystemClock(),
        appIdentifierProvider: @escaping () -> String? = { nil },
        appCategoryProvider: @escaping () -> String? = { nil },
        orientationProvider: @escaping () -> KeySenseOrientation = {.unknown}
    ) {
        self.repository = repository
        self.participantId = participantId
        self.keyboardVersion = keyboardVersion
        self.clock = clock
        self.appIdentifierProvider = appIdentifierProvider
        self.appCategoryProvider = appCategoryProvider
        self.orientationProvider = orientationProvider
    }

    var hasActiveSession: Bool {
        accumulator != nil
    }

    /// Main entry point used by the Keyman/UI integration layer.
    func handle(_ event: KeySenseTypingEventKind) {
        assertMainThread()
      
        print(
                "[KEYSENSE REGRESSION] handle event: \(event)"
            )
        let now = clock.capture()

        guard let active = accumulator else {
            startSession(with: event, at: now)
            return
        }

        // Defensive monotonic-clock handling. A new event after >=30 s splits the session
        // even if iOS suspended the extension and the timeout callback never fired.
        let deltaNs: UInt64
        if now.uptimeNs >= active.lastEventUptimeNs {
            deltaNs = now.uptimeNs - active.lastEventUptimeNs
        } else {
            // Uptime should never move backwards. Treat it as a hard boundary if it does.
            finalizeActiveSession(reason: .other)
            startSession(with: event, at: now)
            return
        }

        if deltaNs >= Self.sessionTimeoutNs {
            finalizeActiveSession(reason: .inactivityTimeout)
            startSession(with: event, at: now)
            return
        }

        let deltaMs = Double(deltaNs) / 1_000_000.0

        if deltaNs >= Self.pauseThresholdNs {
            active.registerPause(milliseconds: deltaMs)
        } else if deltaNs > 0 {
            active.registerActiveIki(milliseconds: deltaMs)
        }

        active.process(event)
        active.updateLastEventTime(now)
        scheduleTimeout(forSessionId: active.sessionId)
    }
  
    func registerPredictionSelection() {
        assertMainThread()

        guard let active = accumulator else {
            return
        }

        active.registerPredictionSelection()
    }

    func endSession(reason: KeySenseSessionEndReason) {
        assertMainThread()
        finalizeActiveSession(reason: reason)
    }

    func keyboardDismissed() {
        endSession(reason: .keyboardDismissed)
    }

    func inputContextChanged() {
        endSession(reason: .inputContextChanged)
    }

    func appContextChanged() {
        endSession(reason: .appContextChanged)
    }

    func extensionWillTerminate() {
        endSession(reason: .extensionTerminated)
    }

    private func startSession(with event: KeySenseTypingEventKind, at time: KeySenseEventTime) {
      let newAccumulator = KeySenseSessionAccumulator(firstEventTime: time, orientation: orientationProvider())
        newAccumulator.process(event)
        newAccumulator.updateLastEventTime(time)
        accumulator = newAccumulator
        scheduleTimeout(forSessionId: newAccumulator.sessionId)
    }

    private func finalizeActiveSession(reason: KeySenseSessionEndReason) {
        timeoutWorkItem?.cancel()
        timeoutWorkItem = nil

        guard let active = accumulator else { return }
        print(
            "[KEYSENSE REGRESSION] finalizeActiveSession called: \(reason.rawValue)"
        )

        let record = active.makeRecord(
            participantId: participantId,
            keyboardVersion: keyboardVersion,
            endReason: reason,
            pauseThresholdMs: Self.pauseThresholdMs,
            sessionTimeoutMs: Self.sessionTimeoutMs,
            appIdentifier: appIdentifierProvider(),
            appCategory: appCategoryProvider()
        )

        // Clear transient content before returning control to the keyboard UI.
        // The record contains aggregate values only.
        accumulator = nil

        do {
            try record.validate()
            try repository.insert(record)
            onSessionPersisted?(record)
        } catch {
            onPersistenceError?(error)
        }
    }

    private func scheduleTimeout(forSessionId sessionId: String) {
        timeoutWorkItem?.cancel()

        let workItem = DispatchWorkItem { [weak self] in
            guard let self,
                  self.accumulator?.sessionId == sessionId else { return }
            self.finalizeActiveSession(reason: .inactivityTimeout)
        }

        timeoutWorkItem = workItem
        DispatchQueue.main.asyncAfter(
            deadline: .now() + .milliseconds(Int(Self.sessionTimeoutMs)),
            execute: workItem
        )
    }

    private func assertMainThread() {
        assert(Thread.isMainThread, "KeySenseTypingSessionManager must be called on the main thread.")
    }
}

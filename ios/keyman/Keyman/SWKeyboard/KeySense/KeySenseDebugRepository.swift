import Foundation
import os.log

/// Development-only repository for validating the collection engine
/// before PROSIT / database integration.
final class KeySenseDebugRepository: KeySenseSessionRepository {

    private(set) var sessions: [KeySenseTypingSessionV1] = []

    func insert(_ session: KeySenseTypingSessionV1) throws {

        sessions.append(session)

        #if DEBUG
        let encoder = JSONEncoder()
        encoder.outputFormatting = [
            .prettyPrinted,
            .sortedKeys
        ]

        if let data = try? encoder.encode(session),
           let json = String(
                data: data,
                encoding: .utf8
           ) {

            os_log(
                "[KEYSENSE SESSION]\n%{public}s",
                type: .info,
                json
            )
        }
        #endif
    }
}

//
//  KeySenseEmojiHistory.swift
//  Keyman
//
//  Created by RAVISHANKAR SUBRAMANI IYER on 2026-10-08.
//  Copyright © 2026 SIL International. All rights reserved.
//

import Foundation

/// Privacy-preserving recent emoji history.
///
/// Emoji identities are kept only in memory for UI convenience.
/// Nothing from this history is written to UserDefaults,
/// database storage, logs, or the final KEYSENSE session record.
final class KeySenseEmojiHistory {

    private(set) var recent: [String] = []

    private let maximumCount: Int

    init(maximumCount: Int = 30) {
        self.maximumCount = maximumCount
    }

    func register(_ emoji: String) {

        // Remove an existing occurrence so the newest
        // selection can move to the front.
        recent.removeAll {
            $0 == emoji
        }

        recent.insert(
            emoji,
            at: 0
        )

        if recent.count > maximumCount {
            recent.removeLast(
                recent.count - maximumCount
            )
        }
    }

    func clear() {
        recent.removeAll()
    }
}

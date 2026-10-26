//
//  KeySenseAppearanceSettings.swift
//  Keyman
//
//  Created by RAVISHANKAR SUBRAMANI IYER on 2026-10-22.
//  Copyright © 2026 SIL International. All rights reserved.
//

import UIKit

enum KeySenseAppearanceMode: String {
    case system
    case light
    case dark

    var userInterfaceStyle: UIUserInterfaceStyle {
        switch self {

        case .system:
            return .unspecified

        case .light:
            return .light

        case .dark:
            return .dark
        }
    }
}

final class KeySenseAppearanceStore {

    static let shared = KeySenseAppearanceStore()

    private let key =
        "keysense.appearanceMode"

    private let defaults: UserDefaults

    private init() {

        defaults =
            UserDefaults(
                suiteName: "group.com.dal.keyman.keysense"
            )
            ?? .standard
    }

    var mode: KeySenseAppearanceMode {

        get {

            guard
                let rawValue =
                    defaults.string(forKey: key),
                let mode =
                    KeySenseAppearanceMode(
                        rawValue: rawValue
                    )
            else {
                return .system
            }

            return mode
        }

        set {
            defaults.set(
                newValue.rawValue,
                forKey: key
            )
        }
    }
}

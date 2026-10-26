//
//  KeySenseEmojiCompatibility.swift
//  Keyman
//
//  KEYSENSE emoji runtime compatibility checking.
//
//  Created by RAVISHANKAR SUBRAMANI IYER on 2026-10-11.
//  Copyright © 2026 SIL International. All rights reserved.
//

import Foundation
import UIKit
import CoreText


enum KeySenseEmojiCompatibility {

    /// Returns true only when the emoji is considered safe to expose
    /// in the KEYSENSE emoji picker on the current device.
    static func isUsable(_ emoji: String) -> Bool {

        // ---------------------------------------------------------
        // 1. Basic data-integrity checks
        // ---------------------------------------------------------

        guard !emoji.isEmpty else {
            return false
        }

        // A literal question mark should never be an emoji item.
        guard emoji != "?" else {
            return false
        }

        // Unicode replacement character means the source data
        // has already become invalid/corrupted.
        guard !emoji.unicodeScalars.contains(
            where: { $0.value == 0xFFFD }
        ) else {
            return false
        }


        // ---------------------------------------------------------
        // 2. Device rendering / shaping check
        // ---------------------------------------------------------

        return canRenderEmojiSequence(emoji)
    }

    private static func canRenderEmojiSequence(
        _ emoji: String
    ) -> Bool {

        let emojiFont = CTFontCreateWithName(
            "AppleColorEmoji" as CFString,
            32,
            nil
        )

        let attributes: [
            NSAttributedString.Key: Any
        ] = [
            kCTFontAttributeName
                as NSAttributedString.Key: emojiFont
        ]

        let attributedString =
            NSAttributedString(
                string: emoji,
                attributes: attributes
            )

        let line =
            CTLineCreateWithAttributedString(
                attributedString
            )

        let runs =
            CTLineGetGlyphRuns(line)
                as NSArray

        guard runs.count > 0 else {
            return false
        }

        var foundGlyph = false

        for case let run as CTRun in runs {

            // ---------------------------------------------
            // Check which font CoreText actually selected.
            // ---------------------------------------------

            let runAttributes =
                CTRunGetAttributes(run)
                    as NSDictionary

            guard let fontValue =
                runAttributes[
                    kCTFontAttributeName
                ]
            else {
                return false
            }

            let runFont =
                fontValue as! CTFont

            let postScriptName =
                CTFontCopyPostScriptName(
                    runFont
                ) as String

            let familyName =
                CTFontCopyFamilyName(
                    runFont
                ) as String

            let normalizedPostScript =
                postScriptName.lowercased()

            let normalizedFamily =
                familyName.lowercased()


            // ---------------------------------------------
            // Reject Apple's missing-glyph fallback font.
            // ---------------------------------------------

            if normalizedPostScript.contains(
                "lastresort"
            ) ||
            normalizedFamily.contains(
                "last resort"
            ) {
                return false
            }


            // ---------------------------------------------
            // Emoji should actually be shaped using
            // Apple's emoji font.
            // ---------------------------------------------

            let isAppleEmojiFont =
                normalizedPostScript.contains(
                    "applecoloremoji"
                )
                ||
                normalizedFamily.contains(
                    "apple color emoji"
                )

            guard isAppleEmojiFont else {
                return false
            }


            // ---------------------------------------------
            // Make sure real glyphs were produced.
            // ---------------------------------------------

            let glyphCount =
                CTRunGetGlyphCount(run)

            guard glyphCount > 0 else {
                return false
            }

            var glyphs = Array(
                repeating: CGGlyph(),
                count: glyphCount
            )

            CTRunGetGlyphs(
                run,
                CFRange(
                    location: 0,
                    length: 0
                ),
                &glyphs
            )

            for glyph in glyphs {

                guard glyph != 0 else {
                    return false
                }

                foundGlyph = true
            }
        }

        return foundGlyph
    }
  
}

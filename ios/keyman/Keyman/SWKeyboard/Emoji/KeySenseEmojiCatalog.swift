//
//  KeySenseEmojiCatalog.swift
//  Keyman
//
//  Created by RAVISHANKAR SUBRAMANI IYER on 2026-10-07.
//  Copyright © 2026 SIL International. All rights reserved.
//

import Foundation
import os.log

struct KeySenseEmojiItem: Codable, Hashable {
    let value: String
    let group: String
    let subgroup: String
}

enum KeySenseEmojiCatalogError: Error {
    case resourceNotFound
    case unableToReadData
    case unableToDecode
}

final class KeySenseEmojiCatalog {

    private(set) var items: [KeySenseEmojiItem] = []
    private(set) var sourceItemCount: Int = 0
    private(set) var filteredOutCount: Int = 0

    var isLoaded: Bool {
        !items.isEmpty
    }

    func load(
        from bundle: Bundle,
        resourceName: String = "EmojiCatalog",
        resourceExtension: String = "json"
    ) throws {

        guard let url = bundle.url(
            forResource: resourceName,
            withExtension: resourceExtension
        ) else {
            throw KeySenseEmojiCatalogError.resourceNotFound
        }

        let data = try Data(
            contentsOf: url
        )

        let decodedItems =
            try JSONDecoder().decode(
                [KeySenseEmojiItem].self,
                from: data
            )

        sourceItemCount =
            decodedItems.count

        items =
            decodedItems.filter { item in

                let usable =
                    KeySenseEmojiCompatibility.isUsable(
                        item.value
                    )

                if !usable {

                    os_log(
                        "[KEYSENSE EMOJI FILTERED] %{public}s | %{public}s | %{public}s",
                        log: KeymanLogger.ui,
                        type: .info,
                        item.value,
                        item.group,
                        item.subgroup
                    )
                }

                return usable
            }

        filteredOutCount =
            sourceItemCount - items.count
    }

    func items(
        inGroup group: String
    ) -> [KeySenseEmojiItem] {

        items.filter {
            $0.group == group
        }
    }
  
    func subgroups(
        inGroup group: String
    ) -> [String] {

        var seen = Set<String>()
        var result: [String] = []

        for item in items {

            guard item.group == group else {
                continue
            }

            if seen.insert(item.subgroup).inserted {
                result.append(item.subgroup)
            }
        }

        return result
    }
  
    func items(
        inGroup group: String,
        subgroup: String
    ) -> [KeySenseEmojiItem] {

        items.filter {
            $0.group == group &&
            $0.subgroup == subgroup
        }
    }

    var groups: [String] {

        var seen = Set<String>()
        var result: [String] = []

        for item in items {
            if seen.insert(item.group).inserted {
                result.append(item.group)
            }
        }

        return result
    }
}

//
//  KeymanWebDelegate.swift
//  KeymanEngine
//
//  Created by Gabriel Wong on 2017-10-17.
//  Copyright © 2017 SIL International. All rights reserved.
//

import WebKit

/// Delegate receiving events from Keyman Web.
protocol KeymanWebDelegate: class {
  /// Keyman Web has loaded (or reloaded).
  func keyboardLoaded(_ keymanWeb: KeymanWebViewController)

  /// - Parameters:
  ///   - numCharsToDelete: The number of UTF-16 code units to delete before inserting the new text.
  ///   - newText: The string to insert.
  func insertText(_ keymanWeb: KeymanWebViewController, numCharsToDelete: Int, newText: String)
  
  //KEYSENSE
  func keySenseKeyEvent(_ keymanWeb: KeymanWebViewController, category: String)
  
  //KEYSENSE Suggested Text Count
  func keySensePredictionSelection(_ keymanWeb: KeymanWebViewController)
  
  func keySenseShowEmoji(_ keymanWeb: KeymanWebViewController)
  func keySenseShowSettings(_ keymanWeb: KeymanWebViewController)
  
  
  
  /// - Parameters:
  func beep(_ keymanWeb: KeymanWebViewController)
  func menuKeyDown(_ keymanWeb: KeymanWebViewController)
  func menuKeyUp(_ keymanWeb: KeymanWebViewController)
  func menuKeyHeld(_ keymanWeb: KeymanWebViewController)
}

extension KeymanWebDelegate {
  func keyboardLoaded(_ keymanWeb: KeymanWebViewController) {}
  func insertText(_ keymanWeb: KeymanWebViewController, numCharsToDelete: Int, newText: String) {}
  func keySenseKeyEvent(_ keymanWeb: KeymanWebViewController, category: String) {}
  func beep(_ keymanWeb: KeymanWebViewController) {}
  func menuKeyDown(_ keymanWeb: KeymanWebViewController) {}
  func menuKeyUp(_ keymanWeb: KeymanWebViewController) {}
  func menuKeyHeld(_ keymanWeb: KeymanWebViewController) {}
  func keySensePredictionSelection(_ keymanWeb: KeymanWebViewController) {}
  func keySenseShowEmoji(_ keymanWeb: KeymanWebViewController) {}
  func keySenseShowSettings(_ keymanWeb: KeymanWebViewController) {}
}

//
//  KeyboardViewController.swift
//  Keyman
//
//  Created by Gabriel Wong on 2017-09-04.
//  Copyright © 2017 SIL International. All rights reserved.
//

import KeymanEngine
import UIKit
import Sentry
import os.log

class KeyboardViewController: InputViewController {
  var topBarImageSource: ImageBannerViewController!
  private let keySenseEmojiCatalog = KeySenseEmojiCatalog()
  private let keySenseEmojiHistory = KeySenseEmojiHistory()
  
  private var keySenseEmojiPanel: KeySenseEmojiPanelViewController?
  private var isKeySenseEmojiPanelVisible = false
  
  private var keySenseSettingsPanel: KeySenseSettingsViewController?
  private var isKeySenseSettingsPanelVisible = false
  
  // MARK: - KEYSENSE

  /// Temporary repository used while validating KEYSENSE collection.
  /// This will later be replaced by the App Group / database repository.
  private let keySenseRepository = KeySenseDebugRepository()

  /// Session-level KEYSENSE collection engine.
  private lazy var keySenseManager: KeySenseTypingSessionManager = {
    let bundle = Bundle(for: KeyboardViewController.self)

    let version =
      bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString")
        as? String ?? "unknown"

    return KeySenseTypingSessionManager(
        repository: keySenseRepository,
        // PROSIT integration point:
        // Replace debug participant ID with the participant identifier
        // supplied/shared by PROSIT Universal.
        participantId: "KEYSENSE_IOS_DEBUG",
        keyboardVersion: version,
        orientationProvider: { [weak self] in
            self?.currentKeySenseOrientation() ?? .unknown
        }
    )
  }()
  
  private func formattedLocalTimestamp(
      utcMs: Int64,
      timezoneOffsetMinutes: Int64
  ) -> String {
      let date = Date(
          timeIntervalSince1970: Double(utcMs) / 1000.0
      )

      let formatter = DateFormatter()
      formatter.dateFormat = "dd-MM-yyyy HH:mm:ss"
      formatter.locale = Locale(identifier: "en_US_POSIX")
      formatter.timeZone = TimeZone(
          secondsFromGMT: Int(timezoneOffsetMinutes * 60)
      )

      return formatter.string(from: date)
  }
  
  private func currentKeySenseOrientation() -> KeySenseOrientation {
      guard let orientation =
          view.window?.windowScene?.interfaceOrientation
      else {
          return .unknown
      }

      switch orientation {
      case .portrait:
          return .portrait

      case .portraitUpsideDown:
          return .portraitUpsideDown

      case .landscapeLeft:
          return .landscapeLeft

      case .landscapeRight:
          return .landscapeRight

      default:
          return .unknown
      }
  }

  private func applyKeySenseAppearance(
      _ mode: KeySenseAppearanceMode
  ) {

      let style = mode.userInterfaceStyle

      overrideUserInterfaceStyle = style
      view.overrideUserInterfaceStyle = style

      if let window = view.window {
          window.overrideUserInterfaceStyle = style
      }

      view.setNeedsLayout()
      view.layoutIfNeeded()
  }
  
  // The entrypoint for the app-extension.
  override init(nibName nibNameOrNil: String?, bundle nibBundleOrNil: Bundle?) {
    // Only true if it's the first init of the class under app-extension mode.
    if !SentryManager.hasStarted {
      // Sentry can send errors from app extensions when "Allow Full Access"
      // is enabled.  They seem to get blocked otherwise, except in the Simulator.
      SentryManager.start(sendingEnabled: true)
    }

    Manager.applicationGroupIdentifier = "group.com.dal.keyman.keysense"

    let bundle = Bundle(for: KeyboardViewController.self)
    topBarImageSource = ImageBannerViewController(nibName: "ImageBanner", bundle: bundle)

    super.init(nibName: nibNameOrNil, bundle: nibBundleOrNil)
  }

  required init?(coder aDecoder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  override func updateViewConstraints() {
    super.updateViewConstraints()

    if view.frame.size.width == 0 || self.view.frame.size.height == 0 {
      return
    }

    setupTopBarImage(size: view.frame.size)
  }

  override func viewDidAppear(_ animated: Bool) {
      super.viewDidAppear(animated)
    
      keySenseUpdateInputModeSwitchKeyRequirement()
    Manager.shared.spacebarText = .BLANK
  }
  
  override func viewDidDisappear(_ animated: Bool) {
    keySenseManager.keyboardDismissed()

    super.viewDidDisappear(animated)
  }
  
  //MARK: Keysense EMOJI KEYBOARD..
  private func setupKeySenseEmojiInterface() {

      // Only enable the emoji UI if the bundled catalog
      // was successfully loaded.
      guard keySenseEmojiCatalog.isLoaded else {

          os_log(
              "[KEYSENSE EMOJI ERROR] Cannot create emoji panel because catalog is empty",
              log: KeymanLogger.ui,
              type: .error
          )

          return
      }

      // MARK: Emoji panel

      let panel = KeySenseEmojiPanelViewController(
          catalog: keySenseEmojiCatalog,
          history: keySenseEmojiHistory
      )

      panel.onEmojiSelected = { [weak self] emoji in
          UIDevice.current.playInputClick()
          self?.keySenseTestEmoji(emoji)
        
      }
    
      panel.onABCRequested = { [weak self] in

          guard let self = self else {
              return
          }

          if self.isKeySenseEmojiPanelVisible {
              self.toggleKeySenseEmojiPanel()
          }
      }
    
      panel.onSpaceRequested = { [weak self] in

          guard let self = self else {
              return
          }
          
          UIDevice.current.playInputClick()
          self.textDocumentProxy.insertText(" ")

          self.keySenseManager.handle(
              .space
          )
      }
    
      panel.onBackspaceRequested = { [weak self] in

          guard let self = self else {
              return
          }
          UIDevice.current.playInputClick()
          self.textDocumentProxy.deleteBackward()

          self.keySenseManager.handle(
              .backspace
          )
      }

      addChild(panel)

      panel.view.translatesAutoresizingMaskIntoConstraints =
          false

      panel.view.isHidden = true

      view.addSubview(panel.view)

      NSLayoutConstraint.activate([

          panel.view.topAnchor.constraint(
              equalTo: view.topAnchor
          ),

          panel.view.leadingAnchor.constraint(
              equalTo: view.leadingAnchor
          ),

          panel.view.trailingAnchor.constraint(
              equalTo: view.trailingAnchor
          ),

          panel.view.bottomAnchor.constraint(
              equalTo: view.bottomAnchor
          )
      ])

      panel.didMove(toParent: self)

      keySenseEmojiPanel = panel
  }
  
  private func setupKeySenseSettingsInterface() {

      let panel = KeySenseSettingsViewController()
    
      panel.onABCRequested = { [weak self] in

          guard let self = self else {
              return
          }

          if self.isKeySenseSettingsPanelVisible {

              // Hide Settings first.
              self.toggleKeySenseSettingsPanel()

              // Then, on the next UI cycle, apply the final
              // stored keyboard height to Keyman's OSK.
              DispatchQueue.main.async { [weak self] in
                  self?.keySenseFinalizeKeyboardLayout()
              }
          }
      }
    
      panel.onResetHeightRequested = { [weak self] in

          guard let self = self else {
              return
          }

          self.keySenseResetKeyboardHeight()

          self.keySenseSettingsPanel?.setHeightSliderValue(
              self.keySenseCurrentKeyboardHeightNormalized()
          )
      }

      addChild(panel)

      panel.view.translatesAutoresizingMaskIntoConstraints = false
      panel.view.isHidden = true

      view.addSubview(panel.view)

      NSLayoutConstraint.activate([
          panel.view.topAnchor.constraint(
              equalTo: view.topAnchor
          ),

          panel.view.leadingAnchor.constraint(
              equalTo: view.leadingAnchor
          ),

          panel.view.trailingAnchor.constraint(
              equalTo: view.trailingAnchor
          ),

          panel.view.bottomAnchor.constraint(
              equalTo: view.bottomAnchor
          )
      ])

      panel.didMove(toParent: self)
    
      panel.onHeightSliderChanged = { [weak self] value in
          self?.keySenseSetKeyboardHeight(
              normalizedValue: value
          )
      }
    
      panel.onAppearanceChanged = { [weak self] mode in

          guard let self = self else {
              return
          }

          KeySenseAppearanceStore.shared.mode = mode
          self.applyKeySenseAppearance(mode)
      }

      keySenseSettingsPanel = panel
  }
  
  @objc
  private func toggleKeySenseEmojiPanel() {

      guard let panel = keySenseEmojiPanel else {
          return
      }

      isKeySenseEmojiPanelVisible.toggle()

      panel.view.isHidden =
          !isKeySenseEmojiPanelVisible

      if isKeySenseEmojiPanelVisible {

          // Keep the emoji panel above Keyman's keyboard
          // and prediction interface.

          view.bringSubviewToFront(
              panel.view
          )
        
          // Make sure the keyboard and emoji panel
          // have their final geometry first.
          view.setNeedsLayout()
          view.layoutIfNeeded()

          panel.view.setNeedsLayout()
          panel.view.layoutIfNeeded()

          // Only now allow the emoji collection
          // to create its sections and cells.

          panel.refreshForPresentation()

      }
      
      else {}
  }
  
  private func keySenseTestEmoji(_ emoji: String) {

      // Insert into the user's text field.
      textDocumentProxy.insertText(emoji)

      // Send only the transient identity to the session aggregator.
      keySenseManager.handle(
          .emoji(transientId: emoji)
      )

  }
  
  override func viewDidLoad() {
      super.viewDidLoad()
      
      globeKeyTapBehaviour = .switchToNextInputMethod
      menuBehaviour = .showNever
    
      do {
          try keySenseEmojiCatalog.load(
              from: Bundle(for: KeyboardViewController.self)
          )

      } catch {
          os_log(
              "[KEYSENSE EMOJI ERROR] Catalog load failed: %{public}s",
              log: KeymanLogger.ui,
              type: .error,
              String(describing: error)
          )
      }

      setupTopBarImage(size: view.frame.size)
      setupKeySenseEmojiInterface()
      setupKeySenseSettingsInterface()

      // MARK: - KEYSENSE Debug Session Output
      
      keySenseManager.onSessionPersisted = { [self] session in

            let summary = """
            ==============================
            [KEYSENSE SESSION FINALIZED]
            ==============================

            sessionId = \(session.sessionId)
            endReason = \(session.sessionEndReason.rawValue)
            sessionDurationMs = \(session.sessionDurationMs)
            sessionStart = \(formattedLocalTimestamp(
                    utcMs: session.sessionStartUtcMs,
                    timezoneOffsetMinutes: session.timezoneOffsetMinutes
                ))
            sessionEnd = \(formattedLocalTimestamp(
                    utcMs: session.sessionEndUtcMs,
                    timezoneOffsetMinutes: session.timezoneOffsetMinutes
                ))

            ----- BASIC COUNTS -----

            totalKeyCount = \(session.totalKeyCount)
            characterCount = \(session.characterCount)

            lowercaseCount = \(session.lowercaseCount)
            uppercaseCount = \(session.uppercaseCount)
            numericCount = \(session.numericCount)
            specialCharCount = \(session.specialCharCount)

            spaceCount = \(session.spaceCount)
            newlineCount = \(session.newlineCount)
            backspaceCount = \(session.backspaceCount)

            wordCount = \(session.wordCount)
            predictionSelectionCount = \(session.predictionSelectionCount)
            
            deletionRate = \(String(describing: session.deletionRate))
            
            activeIkiCount = \(session.activeIkiCount)
            interKeyIntervalMeanMs = \(String(describing: session.interKeyIntervalMeanMs))
            interKeyIntervalMedianMs = \(String(describing: session.interKeyIntervalMedianMs))
            interKeyIntervalSdMs = \(String(describing: session.interKeyIntervalSdMs))
            interKeyIntervalMinMs = \(String(describing: session.interKeyIntervalMinMs))
            interKeyIntervalMaxMs = \(String(describing: session.interKeyIntervalMaxMs))

            pauseCount = \(session.pauseCount)
            totalPauseDurationMs = \(session.totalPauseDurationMs)
            meanPauseDurationMs = \(String(describing: session.meanPauseDurationMs))
            maxPauseDurationMs = \(String(describing: session.maxPauseDurationMs))

            typingBurstCount = \(session.typingBurstCount)
            meanBurstLength = \(String(describing: session.meanBurstLength))
            maxBurstLength = \(session.maxBurstLength)
            
            typingIntervalCv = \(String(describing: session.typingIntervalCv))
            pauseTimeRatio = \(String(describing: session.pauseTimeRatio))
            activeTypingDurationMs = \(session.activeTypingDurationMs)

            keysPerSecond = \(String(describing: session.keysPerSecond))
            charactersPerSecond = \(String(describing: session.charactersPerSecond))
            wordsPerMinute = \(String(describing: session.wordsPerMinute))
            activeTypingRate = \(String(describing: session.activeTypingRate))
            
            charactersPerWord = \(String(describing: session.charactersPerWord))
            newlinesPerWord = \(String(describing: session.newlinesPerWord))
            
            emojiCount = \(session.emojiCount)
            uniqueEmojiCount = \(session.uniqueEmojiCount)
            emojiBurstCount = \(session.emojiBurstCount)
            maxEmojiRunLength = \(session.maxEmojiRunLength)
            repeatedEmojiCount = \(session.repeatedEmojiCount)

            emojiRate = \(String(describing: session.emojiRate))
            emojiDiversityRatio = \(String(describing: session.emojiDiversityRatio))
            
            orientation = \(session.orientation.rawValue)
            
            

            ==============================
            """

            os_log(
                "%{public}s",
                log: KeymanLogger.ui,
                type: .info,
                summary
            )
        }
    
      keySenseManager.onPersistenceError = { error in

          let message =
              "[KEYSENSE ERROR] \(error)"

          os_log(
              "%{public}s",
              log: KeymanLogger.ui,
              type: .error,
              message
          )
      }
    
      print(
          "[KEYSENSE REGRESSION] KeyboardViewController instance = \(ObjectIdentifier(self))"
      )

  }
  
  // MARK: - KEYSENSE Input Events
  override func didReceiveKeySenseKey(
      _ category: String
  ) {
      switch category {

      case "lowercase":
          keySenseManager.handle(.lowercase)

      case "uppercase":
          keySenseManager.handle(.uppercase)

      case "number":
          keySenseManager.handle(.number)

      case "space":
          keySenseManager.handle(.space)

      case "newline":
          keySenseManager.handle(.newline)

      case "backspace":
          keySenseManager.handle(.backspace)

      case "special":
          keySenseManager.handle(
              .special(.other)
          )

      case "wordInternalSpecial":
          keySenseManager.handle(
              .special(.wordInternal)
          )

      default:
          os_log(
              "%{public}s",
              log: KeymanLogger.ui,
              type: .error,
              "[KEYSENSE] Unknown category ignored: \(category)"
          )
          
      }
  }
  
  override func didReceiveKeySenseShowEmoji() {

      guard !isKeySenseEmojiPanelVisible else {
          return
      }

      toggleKeySenseEmojiPanel()
  }
  
  override func didReceiveKeySenseShowSettings() {

      guard !isKeySenseSettingsPanelVisible else {
          return
      }

      keySenseSettingsPanel?.setHeightSliderValue(
          keySenseCurrentKeyboardHeightNormalized()
      )
    
      keySenseSettingsPanel?.setAppearanceMode(
          KeySenseAppearanceStore.shared.mode
      )
      toggleKeySenseSettingsPanel()
  }
  
  @objc
  private func toggleKeySenseSettingsPanel() {

      guard let panel = keySenseSettingsPanel else {
          return
      }

      isKeySenseSettingsPanelVisible.toggle()

      panel.view.isHidden =
          !isKeySenseSettingsPanelVisible

      if isKeySenseSettingsPanelVisible {

          view.bringSubviewToFront(
              panel.view
          )

          view.setNeedsLayout()
          view.layoutIfNeeded()

          panel.view.setNeedsLayout()
          panel.view.layoutIfNeeded()
      }
  }
  
  override func didReceiveKeySensePredictionSelection() {
      keySenseManager.registerPredictionSelection()
  }

  override func viewWillAppear(_ animated: Bool) {
      super.viewWillAppear(animated)

      let style =
          KeySenseAppearanceStore.shared.mode.userInterfaceStyle

      overrideUserInterfaceStyle = style
      view.overrideUserInterfaceStyle = style
  }
  
  override func viewWillTransition(
      to size: CGSize,
      with coordinator: UIViewControllerTransitionCoordinator
  ) {
      if keySenseManager.hasActiveSession {
          keySenseManager.endSession(
              reason: .orientationChanged
          )
      }

      super.viewWillTransition(
          to: size,
          with: coordinator
      )

      setupTopBarImage(size: size)
  }

  func getTopBarImage(size: CGSize) -> String? {
    return topBarImageSource.renderAsBase64(size: CGSize(width: size.width, height: InputViewController.topBarHeight))
  }

  func setupTopBarImage(size: CGSize) {
    // We override the height in getTopBarImage, but not width.
    // If width is 0, we return null from that function; this'll
    // trigger the guard below.
    if size.width == 0 {
      return
    }

    let imgPath = getTopBarImage(size: size)
    guard let path = imgPath else {
      let message = "No image specified for the image banner!"
      os_log("%{public}s", log: KeymanLogger.ui, type: .error, message)
      SentryManager.capture(message)
      return
    }

    self.setBannerImage(to: path)
  }

  override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
    super.traitCollectionDidChange(previousTraitCollection)
    guard let previousTraitCollection = previousTraitCollection else {return}
    if previousTraitCollection.hasDifferentColorAppearance(comparedTo: traitCollection) {
      /* Ensure that the keyboard banner image transitions!  Noting that the backing view isn't in the view hierarchy,
       * and that manipulating said hierarchy is key for triggering the appearance change
       * (see https://developer.apple.com/documentation/uikit/uiappearance)...
       *
       * We have to reload the banner's backing view to trigger the change.  Thanks, Apple.
       * The only other alternative - creating a new instance of the view & its controller.
       *
       * With loadView, at least we can reuse the old instance, which would serve far better
       * for engine API calls if we decide to let the base InputViewController perform UIView
       * render functionality (as a KMEI offering) instead of having it only in our app.
       * That said, note https://developer.apple.com/documentation/uikit/uiviewcontroller/1621454-loadview:
       *
       * > You should never call this method directly.
       *
       * Well... I wouldn't, if it weren't the only way to trigger this without requiring a new instance.
       * We should be fine since we never place the banner's backing view into the actual hierarchy.
       */
      topBarImageSource.loadView()
      setupTopBarImage(size: view.frame.size)
    }
  }
}

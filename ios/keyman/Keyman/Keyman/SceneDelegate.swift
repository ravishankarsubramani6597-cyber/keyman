//
//  SceneDelegate.swift
//  Keyman
//
//  Created by RAVISHANKAR SUBRAMANI IYER on 2026-10-21.
//  Copyright © 2026 SIL International. All rights reserved.
//

import UIKit
import KeymanEngine

class SceneDelegate: UIResponder, UIWindowSceneDelegate {

    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {

        guard let windowScene = scene as? UIWindowScene else {
            return
        }

        let window = UIWindow(windowScene: windowScene)

        let mainViewController = MainViewController()

        let navigationController =
            UINavigationController(
                rootViewController: mainViewController
            )

        navigationController.navigationBar.isTranslucent = false

        window.rootViewController = navigationController

        self.window = window

        if let appDelegate =
            UIApplication.shared.delegate as? AppDelegate {

            appDelegate.window = window
            appDelegate.viewController = mainViewController
            appDelegate.navigationController = navigationController
        }

        window.makeKeyAndVisible()
    }

    func sceneDidEnterBackground(_ scene: UIScene) {

        guard let appDelegate =
            UIApplication.shared.delegate as? AppDelegate else {
            return
        }

        appDelegate.viewController?.saveTextSettings()
        FontManager.shared.unregisterCustomFonts()
    }

    func sceneWillEnterForeground(_ scene: UIScene) {

        guard let appDelegate =
            UIApplication.shared.delegate as? AppDelegate else {
            return
        }

        appDelegate.perform(
            #selector(appDelegate.registerCustomFonts),
            with: nil,
            afterDelay: 1.0
        )
    }
}

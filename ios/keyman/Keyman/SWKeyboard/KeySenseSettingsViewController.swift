//
//  KeySenseSettingsViewController.swift
//  Keyman
//
//  Created by RAVISHANKAR SUBRAMANI IYER on 2026-10-22.
//  Copyright © 2026 SIL International. All rights reserved.
//

import UIKit

final class KeySenseSettingsViewController: UIViewController {

    // MARK: - Callbacks

    var onABCRequested: (() -> Void)?
    var onHeightSliderChanged: ((Float) -> Void)?
    var onResetHeightRequested: (() -> Void)?
    var onAppearanceChanged: ((KeySenseAppearanceMode) -> Void)?


    // MARK: - Header

    private let abcButton: UIButton = {
        let button = UIButton(type: .system)

        button.setTitle("ABC", for: .normal)

        button.titleLabel?.font = .systemFont(
            ofSize: 16,
            weight: .semibold
        )

        button.backgroundColor = .secondarySystemBackground

        button.layer.cornerRadius = 9
        button.layer.cornerCurve = .continuous
        button.layer.borderWidth = 1

        button.accessibilityLabel = "Return to keyboard"

        button.translatesAutoresizingMaskIntoConstraints = false

        return button
    }()

    private let titleLabel: UILabel = {
        let label = UILabel()

        label.text = "Keyboard Settings"

        label.font = .systemFont(
            ofSize: 17,
            weight: .bold
        )

        label.textColor = .label
        label.textAlignment = .center

        label.translatesAutoresizingMaskIntoConstraints = false

        return label
    }()

    private let headerSeparator: UIView = {
        let view = UIView()

        view.backgroundColor = .separator
        view.translatesAutoresizingMaskIntoConstraints = false

        return view
    }()


    // MARK: - Scroll Container

    private let scrollView: UIScrollView = {
        let scrollView = UIScrollView()

        scrollView.showsVerticalScrollIndicator = false
        scrollView.alwaysBounceVertical = false

        scrollView.translatesAutoresizingMaskIntoConstraints = false

        return scrollView
    }()

    private let contentView: UIView = {
        let view = UIView()

        view.translatesAutoresizingMaskIntoConstraints = false

        return view
    }()


    // MARK: - Height Section

    private let heightCard: UIView = {
        let view = UIView()

        view.backgroundColor = .secondarySystemBackground

        view.layer.cornerRadius = 12
        view.layer.cornerCurve = .continuous
        view.layer.borderWidth = 0.5

        view.translatesAutoresizingMaskIntoConstraints = false

        return view
    }()

    private let heightLabel: UILabel = {
        let label = UILabel()

        label.text = "Keyboard Height"

        label.font = .systemFont(
            ofSize: 16,
            weight: .semibold
        )

        label.textColor = .label

        label.translatesAutoresizingMaskIntoConstraints = false

        return label
    }()

    private let heightHintLabel: UILabel = {
        let label = UILabel()

        label.text =
            "Choose a comfortable height. Changes apply when you return to ABC."

        label.font = .systemFont(
            ofSize: 12,
            weight: .regular
        )

        label.textColor = .secondaryLabel
        label.numberOfLines = 0

        label.translatesAutoresizingMaskIntoConstraints = false

        return label
    }()

    private let heightSlider: UISlider = {
        let slider = UISlider()

        slider.minimumValue = 0
        slider.maximumValue = 1
        slider.value = 0.5

        slider.accessibilityLabel = "Keyboard height"

        slider.translatesAutoresizingMaskIntoConstraints = false

        return slider
    }()

    private let smallLabel: UILabel = {
        let label = UILabel()

        label.text = "Small"
        label.font = .systemFont(
            ofSize: 11,
            weight: .regular
        )

        label.textColor = .secondaryLabel

        label.translatesAutoresizingMaskIntoConstraints = false

        return label
    }()

    private let largeLabel: UILabel = {
        let label = UILabel()

        label.text = "Large"
        label.font = .systemFont(
            ofSize: 11,
            weight: .regular
        )

        label.textColor = .secondaryLabel
        label.textAlignment = .right

        label.translatesAutoresizingMaskIntoConstraints = false

        return label
    }()

    private let resetButton: UIButton = {
        let button = UIButton(type: .system)

        button.setTitle(
            "Reset to Default",
            for: .normal
        )

        button.titleLabel?.font = .systemFont(
            ofSize: 14,
            weight: .medium
        )

        button.backgroundColor = .tertiarySystemBackground

        button.layer.cornerRadius = 9
        button.layer.cornerCurve = .continuous
        button.layer.borderWidth = 1

        button.accessibilityLabel =
            "Reset keyboard height to default"

        button.translatesAutoresizingMaskIntoConstraints = false

        return button
    }()


    // MARK: - Appearance Section

    private let appearanceCard: UIView = {
        let view = UIView()

        view.backgroundColor = .secondarySystemBackground

        view.layer.cornerRadius = 12
        view.layer.cornerCurve = .continuous
        view.layer.borderWidth = 0.5

        view.translatesAutoresizingMaskIntoConstraints = false

        return view
    }()

    private let appearanceLabel: UILabel = {
        let label = UILabel()

        label.text = "Appearance"

        label.font = .systemFont(
            ofSize: 16,
            weight: .semibold
        )

        label.textColor = .label

        label.translatesAutoresizingMaskIntoConstraints = false

        return label
    }()

    private let appearanceHintLabel: UILabel = {
        let label = UILabel()

        label.text =
            "Choose how the keyboard should appear."

        label.font = .systemFont(
            ofSize: 12,
            weight: .regular
        )

        label.textColor = .secondaryLabel

        label.translatesAutoresizingMaskIntoConstraints = false

        return label
    }()

    private let appearanceControl: UISegmentedControl = {
        let control = UISegmentedControl(
            items: [
                "System",
                "Light",
                "Dark"
            ]
        )

        control.selectedSegmentIndex = 0

        control.translatesAutoresizingMaskIntoConstraints = false

        return control
    }()


    // MARK: - Public UI Updates

    func setHeightSliderValue(_ value: Float) {

        heightSlider.value = max(
            0.0,
            min(1.0, value)
        )
    }

    func setAppearanceMode(
        _ mode: KeySenseAppearanceMode
    ) {

        switch mode {

        case .system:
            appearanceControl.selectedSegmentIndex = 0

        case .light:
            appearanceControl.selectedSegmentIndex = 1

        case .dark:
            appearanceControl.selectedSegmentIndex = 2
        }
    }


    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .systemBackground

        configureActions()
        configureHierarchy()
        configureConstraints()
        updateDynamicBorders()
    }

    override func traitCollectionDidChange(
        _ previousTraitCollection: UITraitCollection?
    ) {
        super.traitCollectionDidChange(
            previousTraitCollection
        )

        guard
            let previousTraitCollection,
            previousTraitCollection
                .hasDifferentColorAppearance(
                    comparedTo: traitCollection
                )
        else {
            return
        }

        updateDynamicBorders()
    }


    // MARK: - Setup

    private func configureActions() {

        abcButton.addTarget(
            self,
            action: #selector(abcButtonTapped),
            for: .touchUpInside
        )

        heightSlider.addTarget(
            self,
            action: #selector(heightSliderChanged),
            for: .valueChanged
        )

        resetButton.addTarget(
            self,
            action: #selector(resetHeightTapped),
            for: .touchUpInside
        )

        appearanceControl.addTarget(
            self,
            action: #selector(appearanceChanged),
            for: .valueChanged
        )
    }

    private func configureHierarchy() {

        view.addSubview(abcButton)
        view.addSubview(titleLabel)
        view.addSubview(headerSeparator)

        view.addSubview(scrollView)

        scrollView.addSubview(contentView)

        contentView.addSubview(heightCard)
        contentView.addSubview(appearanceCard)

        heightCard.addSubview(heightLabel)
        heightCard.addSubview(heightHintLabel)
        heightCard.addSubview(heightSlider)
        heightCard.addSubview(smallLabel)
        heightCard.addSubview(largeLabel)
        heightCard.addSubview(resetButton)

        appearanceCard.addSubview(appearanceLabel)
        appearanceCard.addSubview(appearanceHintLabel)
        appearanceCard.addSubview(appearanceControl)
    }

    private func configureConstraints() {

        NSLayoutConstraint.activate([

            // Header

            abcButton.leadingAnchor.constraint(
                equalTo: view.leadingAnchor,
                constant: 12
            ),

            abcButton.topAnchor.constraint(
                equalTo: view.topAnchor,
                constant: 8
            ),

            abcButton.widthAnchor.constraint(
                equalToConstant: 60
            ),

            abcButton.heightAnchor.constraint(
                equalToConstant: 36
            ),

            titleLabel.centerXAnchor.constraint(
                equalTo: view.centerXAnchor
            ),

            titleLabel.centerYAnchor.constraint(
                equalTo: abcButton.centerYAnchor
            ),

            titleLabel.leadingAnchor.constraint(
                greaterThanOrEqualTo:
                    abcButton.trailingAnchor,
                constant: 8
            ),

            titleLabel.trailingAnchor.constraint(
                lessThanOrEqualTo:
                    view.trailingAnchor,
                constant: -12
            ),

            headerSeparator.topAnchor.constraint(
                equalTo: abcButton.bottomAnchor,
                constant: 8
            ),

            headerSeparator.leadingAnchor.constraint(
                equalTo: view.leadingAnchor
            ),

            headerSeparator.trailingAnchor.constraint(
                equalTo: view.trailingAnchor
            ),

            headerSeparator.heightAnchor.constraint(
                equalToConstant: 0.5
            ),


            // Scroll View

            scrollView.topAnchor.constraint(
                equalTo: headerSeparator.bottomAnchor
            ),

            scrollView.leadingAnchor.constraint(
                equalTo: view.leadingAnchor
            ),

            scrollView.trailingAnchor.constraint(
                equalTo: view.trailingAnchor
            ),

            scrollView.bottomAnchor.constraint(
                equalTo: view.bottomAnchor
            ),

            contentView.topAnchor.constraint(
                equalTo:
                    scrollView.contentLayoutGuide.topAnchor
            ),

            contentView.leadingAnchor.constraint(
                equalTo:
                    scrollView.contentLayoutGuide.leadingAnchor
            ),

            contentView.trailingAnchor.constraint(
                equalTo:
                    scrollView.contentLayoutGuide.trailingAnchor
            ),

            contentView.bottomAnchor.constraint(
                equalTo:
                    scrollView.contentLayoutGuide.bottomAnchor
            ),

            contentView.widthAnchor.constraint(
                equalTo:
                    scrollView.frameLayoutGuide.widthAnchor
            ),


            // Height Card

            heightCard.topAnchor.constraint(
                equalTo: contentView.topAnchor,
                constant: 12
            ),

            heightCard.leadingAnchor.constraint(
                equalTo: contentView.leadingAnchor,
                constant: 12
            ),

            heightCard.trailingAnchor.constraint(
                equalTo: contentView.trailingAnchor,
                constant: -12
            ),

            heightLabel.topAnchor.constraint(
                equalTo: heightCard.topAnchor,
                constant: 14
            ),

            heightLabel.leadingAnchor.constraint(
                equalTo: heightCard.leadingAnchor,
                constant: 16
            ),

            heightLabel.trailingAnchor.constraint(
                equalTo: heightCard.trailingAnchor,
                constant: -16
            ),

            heightHintLabel.topAnchor.constraint(
                equalTo: heightLabel.bottomAnchor,
                constant: 4
            ),

            heightHintLabel.leadingAnchor.constraint(
                equalTo: heightLabel.leadingAnchor
            ),

            heightHintLabel.trailingAnchor.constraint(
                equalTo: heightLabel.trailingAnchor
            ),

            heightSlider.topAnchor.constraint(
                equalTo: heightHintLabel.bottomAnchor,
                constant: 10
            ),

            heightSlider.leadingAnchor.constraint(
                equalTo: heightCard.leadingAnchor,
                constant: 16
            ),

            heightSlider.trailingAnchor.constraint(
                equalTo: heightCard.trailingAnchor,
                constant: -16
            ),

            smallLabel.topAnchor.constraint(
                equalTo: heightSlider.bottomAnchor,
                constant: 1
            ),

            smallLabel.leadingAnchor.constraint(
                equalTo: heightSlider.leadingAnchor
            ),

            largeLabel.topAnchor.constraint(
                equalTo: heightSlider.bottomAnchor,
                constant: 1
            ),

            largeLabel.trailingAnchor.constraint(
                equalTo: heightSlider.trailingAnchor
            ),

            resetButton.topAnchor.constraint(
                equalTo: smallLabel.bottomAnchor,
                constant: 12
            ),

            resetButton.centerXAnchor.constraint(
                equalTo: heightCard.centerXAnchor
            ),

            resetButton.widthAnchor.constraint(
                equalToConstant: 150
            ),

            resetButton.heightAnchor.constraint(
                equalToConstant: 36
            ),

            resetButton.bottomAnchor.constraint(
                equalTo: heightCard.bottomAnchor,
                constant: -14
            ),


            // Appearance Card

            appearanceCard.topAnchor.constraint(
                equalTo: heightCard.bottomAnchor,
                constant: 12
            ),

            appearanceCard.leadingAnchor.constraint(
                equalTo: contentView.leadingAnchor,
                constant: 12
            ),

            appearanceCard.trailingAnchor.constraint(
                equalTo: contentView.trailingAnchor,
                constant: -12
            ),

            appearanceCard.bottomAnchor.constraint(
                equalTo: contentView.bottomAnchor,
                constant: -12
            ),

            appearanceLabel.topAnchor.constraint(
                equalTo: appearanceCard.topAnchor,
                constant: 14
            ),

            appearanceLabel.leadingAnchor.constraint(
                equalTo: appearanceCard.leadingAnchor,
                constant: 16
            ),

            appearanceLabel.trailingAnchor.constraint(
                equalTo: appearanceCard.trailingAnchor,
                constant: -16
            ),

            appearanceHintLabel.topAnchor.constraint(
                equalTo: appearanceLabel.bottomAnchor,
                constant: 4
            ),

            appearanceHintLabel.leadingAnchor.constraint(
                equalTo: appearanceLabel.leadingAnchor
            ),

            appearanceHintLabel.trailingAnchor.constraint(
                equalTo: appearanceLabel.trailingAnchor
            ),

            appearanceControl.topAnchor.constraint(
                equalTo: appearanceHintLabel.bottomAnchor,
                constant: 10
            ),

            appearanceControl.leadingAnchor.constraint(
                equalTo: appearanceCard.leadingAnchor,
                constant: 16
            ),

            appearanceControl.trailingAnchor.constraint(
                equalTo: appearanceCard.trailingAnchor,
                constant: -16
            ),

            appearanceControl.heightAnchor.constraint(
                equalToConstant: 34
            ),

            appearanceControl.bottomAnchor.constraint(
                equalTo: appearanceCard.bottomAnchor,
                constant: -14
            )
        ])
    }


    // MARK: - Dynamic Appearance

    private func updateDynamicBorders() {

        let borderColor =
            UIColor.separator
                .resolvedColor(
                    with: traitCollection
                )
                .cgColor

        abcButton.layer.borderColor =
            borderColor

        resetButton.layer.borderColor =
            borderColor

        heightCard.layer.borderColor =
            borderColor

        appearanceCard.layer.borderColor =
            borderColor
    }


    // MARK: - Actions

    @objc
    private func abcButtonTapped() {
        onABCRequested?()
    }

    @objc
    private func heightSliderChanged() {

        onHeightSliderChanged?(
            heightSlider.value
        )
    }

    @objc
    private func resetHeightTapped() {
        onResetHeightRequested?()
    }

    @objc
    private func appearanceChanged() {

        let mode: KeySenseAppearanceMode

        switch appearanceControl.selectedSegmentIndex {

        case 1:
            mode = .light

        case 2:
            mode = .dark

        default:
            mode = .system
        }

        onAppearanceChanged?(mode)
    }
}

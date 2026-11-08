//
//  KeySenseTypingAssistanceSection.swift
//  KEYSENSE
//
//  UI-only section for the existing keyboard Settings panel.
//  Keyman preference persistence and live engine refresh must be supplied
//  by the enclosing keyboard controller. No keystrokes are read here.
//

import UIKit

enum KeySenseTypingAssistanceMode: String, CaseIterable {
    case disabled = "disabled"
    case suggestions = "suggestions"
    case suggestionsWithAutoCorrection = "suggestionsWithAutoCorrection"

    static let defaultMode: KeySenseTypingAssistanceMode = .suggestions

    var title: String {
        switch self {
        case .disabled:
            return "Disable suggestions"
        case .suggestions:
            return "Enable suggestions"
        case .suggestionsWithAutoCorrection:
            return "Enable suggestions with auto-correction"
        }
    }
}

final class KeySenseTypingAssistanceSection: UIView {

    var onModeChanged: ((KeySenseTypingAssistanceMode) -> Void)?

    private(set) var selectedMode: KeySenseTypingAssistanceMode = .defaultMode

    private let heading: UILabel = {
        let label = UILabel()
        label.text = "Typing Assistance"
        label.font = .systemFont(ofSize: 16, weight: .semibold)
        label.textColor = .label
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private let explanation: UILabel = {
        let label = UILabel()
        label.text = "Choose how the keyboard suggests words for you."
        label.font = .systemFont(ofSize: 12)
        label.textColor = .secondaryLabel
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private let options: UIStackView = {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 10
        stack.distribution = .fill
        stack.translatesAutoresizingMaskIntoConstraints = false
        return stack
    }()

    private var optionButtons: [UIButton] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        configure()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
    }

    func setMode(_ mode: KeySenseTypingAssistanceMode) {
        selectedMode = mode
        updateOptionAppearance()
    }

    private func configure() {
        translatesAutoresizingMaskIntoConstraints = false
        backgroundColor = .secondarySystemBackground
        layer.cornerRadius = 12
        layer.cornerCurve = .continuous
        layer.borderWidth = 0.5

        addSubview(heading)
        addSubview(explanation)
        addSubview(options)

        for (index, mode) in KeySenseTypingAssistanceMode.allCases.enumerated() {
            let button = UIButton(type: .system)
            button.tag = index
            button.setTitle(mode.title, for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: 14, weight: .medium)
            button.titleLabel?.numberOfLines = 2
            button.titleLabel?.textAlignment = .center
            button.contentEdgeInsets = UIEdgeInsets(
                top: 8, left: 8, bottom: 8, right: 8
            )
            button.layer.cornerRadius = 8
            button.layer.cornerCurve = .continuous
            button.layer.borderWidth = 0.5
            button.translatesAutoresizingMaskIntoConstraints = false
            button.accessibilityLabel = mode.title
            button.addTarget(
                self,
                action: #selector(optionTapped(_:)),
                for: .touchUpInside
            )
            options.addArrangedSubview(button)
            button.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
            optionButtons.append(button)
        }

        NSLayoutConstraint.activate([
            heading.topAnchor.constraint(equalTo: topAnchor, constant: 14),
            heading.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            heading.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),

            explanation.topAnchor.constraint(equalTo: heading.bottomAnchor, constant: 4),
            explanation.leadingAnchor.constraint(equalTo: heading.leadingAnchor),
            explanation.trailingAnchor.constraint(equalTo: heading.trailingAnchor),

            options.topAnchor.constraint(equalTo: explanation.bottomAnchor, constant: 12),
            options.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            options.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            options.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -16)
        ])

        updateOptionAppearance()
    }

    @objc private func optionTapped(_ sender: UIButton) {
        guard KeySenseTypingAssistanceMode.allCases.indices.contains(sender.tag) else {
            return
        }
        let mode = KeySenseTypingAssistanceMode.allCases[sender.tag]
        setMode(mode)
        onModeChanged?(mode)
    }

    private func updateOptionAppearance() {
        layer.borderColor = UIColor.separator.resolvedColor(with: traitCollection).cgColor

        for (index, button) in optionButtons.enumerated() {
            let isSelected = KeySenseTypingAssistanceMode.allCases[index] == selectedMode
            button.backgroundColor = isSelected ? .systemBlue : .tertiarySystemBackground
            button.setTitleColor(isSelected ? .white : .label, for: .normal)
            button.layer.borderColor = UIColor.separator.resolvedColor(
                with: traitCollection
            ).cgColor
            button.accessibilityTraits = isSelected ? [.button, .selected] : .button
        }
    }

    override func traitCollectionDidChange(
        _ previousTraitCollection: UITraitCollection?
    ) {
        super.traitCollectionDidChange(previousTraitCollection)
        updateOptionAppearance()
    }
}

// App Group preference shared by KEYSENSE host and keyboard extension.
// This is UI state, NOT a research typing metric.
enum KeySenseTypingAssistanceStore {
    private static let preferenceKey = "keysense.typingAssistance.mode.v1"
    private static let appGroup = "group.com.dal.keyman.keysense"

    private static var preferences: UserDefaults {
        UserDefaults(suiteName: appGroup) ?? .standard
    }

    static var mode: KeySenseTypingAssistanceMode {
        get {
            guard let value = preferences.string(forKey: preferenceKey),
                  let mode = KeySenseTypingAssistanceMode(rawValue: value)
            else {
                return .defaultMode
            }
            return mode
        }
        set {
            preferences.set(newValue.rawValue, forKey: preferenceKey)
        }
    }
}

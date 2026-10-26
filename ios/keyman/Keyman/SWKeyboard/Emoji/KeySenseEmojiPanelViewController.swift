//
//  KeySenseEmojiPanelViewController.swift
//  Keyman
//
//  Created by RAVISHANKAR SUBRAMANI IYER on 2026-10-07.
//  Copyright © 2026 SIL International. All rights reserved.
//

import UIKit

final class KeySenseEmojiPanelViewController:
    UIViewController,
    UICollectionViewDataSource,
    UICollectionViewDelegateFlowLayout {

    // MARK: - Public callback

    var onEmojiSelected: ((String) -> Void)?
    var onABCRequested: (() -> Void)?
    var onSpaceRequested: (() -> Void)?
    var onBackspaceRequested: (() -> Void)?

    // MARK: - Data
  
    private let catalog: KeySenseEmojiCatalog
    private let history: KeySenseEmojiHistory
    private let groups: [String]
  
    private var showingRecent = false
    private var categoryButtons: [UIButton] = []
    private var selectedCategoryButtonIndex = 1
    private var selectedGroupIndex = 0
    private var lastCollectionViewWidth: CGFloat = 0
  
    private var isReadyForPresentation = false
  
    private var selectedGroup: String {
        groups[selectedGroupIndex]
    }

    private var selectedSubgroups: [String] {
        catalog.subgroups(
            inGroup: selectedGroup
        )
    }

    // MARK: - UI

    private let collectionView: UICollectionView
    private let categoryScrollView = UIScrollView()
  
    private let bottomControlBar: UIView = {
        let view = UIView()
        view.translatesAutoresizingMaskIntoConstraints = false
        view.backgroundColor = .secondarySystemBackground
        return view
    }()

    private let abcButton: UIButton = {

        let button = UIButton(type: .system)

        button.translatesAutoresizingMaskIntoConstraints = false

        button.setTitle(
            "ABC",
            for: .normal
        )

        button.titleLabel?.font =
            UIFont.systemFont(
                ofSize: 18,
                weight: .medium
            )

        // Similar to Keyman's modifier/action keys.
        button.backgroundColor =
            .secondarySystemFill

        button.setTitleColor(
            .label,
            for: .normal
        )

        button.layer.cornerRadius = 7
        button.layer.masksToBounds = true

        button.accessibilityLabel =
            "Return to alphabet keyboard"

        return button
    }()
  
    private let spaceButton: UIButton = {

        let button = UIButton(type: .system)

        button.translatesAutoresizingMaskIntoConstraints = false

        button.setTitle(
            "",
            for: .normal
        )

        button.titleLabel?.font =
            UIFont.systemFont(
                ofSize: 15,
                weight: .medium
            )

        // Main keyboard-key appearance.
        button.backgroundColor =
            .systemBackground

        button.setTitleColor(
            .label,
            for: .normal
        )

        button.layer.cornerRadius = 7
        button.layer.masksToBounds = true

        button.accessibilityLabel =
            "Space"

        return button
    }()

    private let backspaceButton: UIButton = {

        let button = UIButton(type: .system)

        button.translatesAutoresizingMaskIntoConstraints = false

        let image = UIImage(
            systemName: "delete.left"
        )

        button.setImage(
            image,
            for: .normal
        )

        button.imageView?.contentMode =
            .scaleAspectFit

        // Similar to Keyman's Delete key.
        button.backgroundColor =
            .secondarySystemFill

        button.tintColor =
            .label

        button.layer.cornerRadius = 7
        button.layer.masksToBounds = true

        button.accessibilityLabel =
            "Backspace"

        return button
    }()

    private let categoryStackView: UIStackView = {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.alignment = .center
        stack.distribution = .fillEqually
        stack.spacing = 4
        stack.translatesAutoresizingMaskIntoConstraints = false
        return stack
    }()

    // MARK: - Init

    init(
        catalog: KeySenseEmojiCatalog,
        history: KeySenseEmojiHistory
    ) {

        self.catalog = catalog
        self.history = history
        self.groups = catalog.groups

        let layout = UICollectionViewFlowLayout()
        layout.scrollDirection = .vertical
        layout.minimumInteritemSpacing = 2
        layout.minimumLineSpacing = 4

        layout.sectionInset = UIEdgeInsets(
            top: 6,
            left: 6,
            bottom: 6,
            right: 6
        )

        self.collectionView = UICollectionView(
            frame: .zero,
            collectionViewLayout: layout
        )

        super.init(
            nibName: nil,
            bundle: nil
        )
    }

    required init?(coder: NSCoder) {
        fatalError(
            "init(coder:) has not been implemented"
        )
    }

    // MARK: - Lifecycle

    override func viewDidLoad() {
      super.viewDidLoad()

      setupCategoryBar()
      setupBottomControlBar()
      setupCollectionView()
    }
  
    override func viewDidLayoutSubviews() {

        super.viewDidLayoutSubviews()

        let currentWidth =
            collectionView.bounds.width

        guard currentWidth > 0 else {
            return
        }

        if abs(
            currentWidth - lastCollectionViewWidth
        ) > 1 {

            lastCollectionViewWidth =
                currentWidth

            collectionView.collectionViewLayout
                .invalidateLayout()
        }
    }

    // MARK: - Setup
  
    private func setupBottomControlBar() {

        view.addSubview(bottomControlBar)

        bottomControlBar.addSubview(abcButton)
        bottomControlBar.addSubview(spaceButton)
        bottomControlBar.addSubview(backspaceButton)

        abcButton.addTarget(
            self,
            action: #selector(abcButtonTapped),
            for: .touchUpInside
        )

        spaceButton.addTarget(
            self,
            action: #selector(spaceButtonTapped),
            for: .touchUpInside
        )

        backspaceButton.addTarget(
            self,
            action: #selector(backspaceButtonTapped),
            for: .touchUpInside
        )

        NSLayoutConstraint.activate([

            bottomControlBar.leadingAnchor.constraint(
                equalTo: view.leadingAnchor
            ),

            bottomControlBar.trailingAnchor.constraint(
                equalTo: view.trailingAnchor
            ),

            bottomControlBar.bottomAnchor.constraint(
                equalTo: view.safeAreaLayoutGuide.bottomAnchor,
                constant: -4
            ),

            bottomControlBar.heightAnchor.constraint(
                equalToConstant: 50
            ),


            abcButton.leadingAnchor.constraint(
                equalTo: bottomControlBar.leadingAnchor,
                constant: 12
            ),

            abcButton.centerYAnchor.constraint(
                equalTo: bottomControlBar.centerYAnchor
            ),

            abcButton.widthAnchor.constraint(
                equalToConstant: 70
            ),


            backspaceButton.trailingAnchor.constraint(
                equalTo: bottomControlBar.trailingAnchor,
                constant: -12
            ),

            backspaceButton.centerYAnchor.constraint(
                equalTo: bottomControlBar.centerYAnchor
            ),

            backspaceButton.widthAnchor.constraint(
                equalToConstant: 70
            ),


            spaceButton.leadingAnchor.constraint(
                equalTo: abcButton.trailingAnchor,
                constant: 8
            ),

            spaceButton.trailingAnchor.constraint(
                equalTo: backspaceButton.leadingAnchor,
                constant: -8
            ),

            spaceButton.centerYAnchor.constraint(
                equalTo: bottomControlBar.centerYAnchor
            ),

            spaceButton.heightAnchor.constraint(
                equalToConstant: 38
            ),
            
            abcButton.heightAnchor.constraint(
                equalToConstant: 38
            ),

            backspaceButton.heightAnchor.constraint(
                equalToConstant: 38
            )
            
        ])
    }
  
    private func iconForGroup(_ group: String) -> String {

        switch group {

        case "Smileys & Emotion":
            return "😀"

        case "People & Body":
            return "🧑"

        case "Animals & Nature":
            return "🐻"

        case "Food & Drink":
            return "🍔"

        case "Travel & Places":
            return "🚗"

        case "Activities":
            return "⚽"

        case "Objects":
            return "💡"

        case "Symbols":
            return "♥️"

        case "Flags":
            return "🚩"

        default:
            return "•"
        }
    }
  
    private func displayNameForSubgroup(
        _ subgroup: String
    ) -> String {

        let cleaned = subgroup
            .replacingOccurrences(
                of: "-",
                with: " "
            )

        return cleaned
            .split(separator: " ")
            .map {
                $0.prefix(1).uppercased()
                + $0.dropFirst()
            }
            .joined(separator: " ")
    }
  
    private func setupCategoryBar() {

        // MARK: - Scroll View

        categoryScrollView.translatesAutoresizingMaskIntoConstraints = false
        categoryScrollView.showsHorizontalScrollIndicator = false
      
        if #available(iOS 27.0, *) {
            categoryScrollView.topEdgeEffect.isHidden = true
            categoryScrollView.bottomEdgeEffect.isHidden = true
        }
        
        categoryScrollView.alwaysBounceHorizontal = true
        categoryScrollView.alwaysBounceVertical = false
        categoryScrollView.backgroundColor = .secondarySystemBackground

        categoryScrollView.clipsToBounds = true

        // MARK: - Stack View

        categoryStackView.translatesAutoresizingMaskIntoConstraints = false
        categoryStackView.axis = .horizontal
        categoryStackView.alignment = .fill
        categoryStackView.distribution = .equalSpacing
        categoryStackView.spacing = 4

        view.addSubview(categoryScrollView)
        categoryScrollView.addSubview(categoryStackView)

        NSLayoutConstraint.activate([

            // Category bar stays attached to the top
            // of the emoji keyboard.

            categoryScrollView.topAnchor.constraint(
                equalTo: view.topAnchor
            ),

            categoryScrollView.leadingAnchor.constraint(
                equalTo: view.leadingAnchor
            ),

            categoryScrollView.trailingAnchor.constraint(
                equalTo: view.trailingAnchor
            ),

            categoryScrollView.heightAnchor.constraint(
                equalToConstant: 42
            ),


            // The stack lives inside the scroll view's
            // actual scrollable content area.

            categoryStackView.topAnchor.constraint(
                equalTo: categoryScrollView.contentLayoutGuide.topAnchor
            ),

            categoryStackView.bottomAnchor.constraint(
                equalTo: categoryScrollView.contentLayoutGuide.bottomAnchor
            ),

            categoryStackView.leadingAnchor.constraint(
                equalTo: categoryScrollView.contentLayoutGuide.leadingAnchor,
                constant: 6
            ),

            categoryStackView.trailingAnchor.constraint(
                equalTo: categoryScrollView.contentLayoutGuide.trailingAnchor,
                constant: -6
            ),


            // Keep the category buttons vertically aligned
            // with the visible category bar.

            categoryStackView.heightAnchor.constraint(
                equalTo: categoryScrollView.frameLayoutGuide.heightAnchor
            ),


            // If all categories fit on screen, make the stack
            // occupy the available width so equalSpacing distributes
            // them cleanly.
            //
            // If they need more room, Auto Layout allows the stack
            // to grow beyond this minimum and the scroll view scrolls.

            categoryStackView.widthAnchor.constraint(
                greaterThanOrEqualTo:
                    categoryScrollView.frameLayoutGuide.widthAnchor,
                constant: -12
            )
        ])


        // MARK: - Recent Button

        let recentButton = UIButton(type: .system)

        recentButton.setTitle(
            "🕘",
            for: .normal
        )

        recentButton.titleLabel?.font =
            UIFont.systemFont(ofSize: 21)

        recentButton.tag = 0

        recentButton.widthAnchor.constraint(
            equalToConstant: 40
        ).isActive = true

        recentButton.accessibilityLabel =
            "Recent emoji"

        recentButton.addTarget(
            self,
            action: #selector(categoryButtonTapped(_:)),
            for: .touchUpInside
        )

        categoryButtons.append(
            recentButton
        )

        categoryStackView.addArrangedSubview(
            recentButton
        )


        // MARK: - Unicode Category Buttons

        for (groupIndex, group) in groups.enumerated() {

            let button = UIButton(type: .system)

            button.setTitle(
                iconForGroup(group),
                for: .normal
            )

            button.titleLabel?.font =
                UIFont.systemFont(ofSize: 21)

            // Button 0 is reserved for Recent.
            button.tag = groupIndex + 1

            button.widthAnchor.constraint(
                equalToConstant: 40
            ).isActive = true

            button.accessibilityLabel =
                group

            button.addTarget(
                self,
                action: #selector(categoryButtonTapped(_:)),
                for: .touchUpInside
            )

            categoryButtons.append(
                button
            )

            categoryStackView.addArrangedSubview(
                button
            )
        }


        // MARK: - Initial Selection

        updateCategoryHighlight(
            selectedIndex: 1
        )
    }

    private func setupCollectionView() {

        view.backgroundColor = .systemBackground

        collectionView.translatesAutoresizingMaskIntoConstraints = false
        collectionView.backgroundColor = .systemBackground
        collectionView.dataSource = self
        collectionView.delegate = self

        collectionView.register(
            KeySenseEmojiCell.self,
            forCellWithReuseIdentifier:
                KeySenseEmojiCell.reuseIdentifier
        )

        collectionView.register(
            KeySenseEmojiSectionHeader.self,
            forSupplementaryViewOfKind:
                UICollectionView.elementKindSectionHeader,
            withReuseIdentifier:
                KeySenseEmojiSectionHeader.reuseIdentifier
        )

        view.addSubview(collectionView)

        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(
                equalTo: categoryScrollView.bottomAnchor
            ),
            collectionView.leadingAnchor.constraint(
                equalTo: view.leadingAnchor
            ),
            collectionView.trailingAnchor.constraint(
                equalTo: view.trailingAnchor
            ),
            collectionView.bottomAnchor.constraint(
                equalTo: bottomControlBar.topAnchor
            )
        ])
    }
  
    private func updateCategoryHighlight(
        selectedIndex: Int
    ) {

        selectedCategoryButtonIndex =
            selectedIndex

        for (index, button) in
            categoryButtons.enumerated() {

            let isSelected =
                index == selectedIndex

            button.backgroundColor =
                isSelected
                ? .tertiarySystemFill
                : .clear

            button.layer.cornerRadius = 8

            if isSelected {
                button.accessibilityTraits.insert(
                    .selected
                )
            } else {
                button.accessibilityTraits.remove(
                    .selected
                )
            }
        }
    }

    // MARK: - Data source

    func numberOfSections(
        in collectionView: UICollectionView
    ) -> Int {

        guard isReadyForPresentation else {
            return 0
        }

        if showingRecent {
            return 1
        }

        return selectedSubgroups.count
    }

    func collectionView(
        _ collectionView: UICollectionView,
        numberOfItemsInSection section: Int
    ) -> Int {

        if showingRecent {
            return history.recent.count
        }

        guard section >= 0,
              section < selectedSubgroups.count
        else {
            return 0
        }

        let subgroup =
            selectedSubgroups[section]

        return catalog.items(
            inGroup: selectedGroup,
            subgroup: subgroup
        ).count
    }
  
    func collectionView(
        _ collectionView: UICollectionView,
        cellForItemAt indexPath: IndexPath
    ) -> UICollectionViewCell {

        let cell = collectionView.dequeueReusableCell(
            withReuseIdentifier:
                KeySenseEmojiCell.reuseIdentifier,
            for: indexPath
        )

        guard let emojiCell =
                cell as? KeySenseEmojiCell else {
            return cell
        }

        let emoji: String

        if showingRecent {

            emoji = history.recent[
                indexPath.item
            ]

        } else {

            let subgroup =
                selectedSubgroups[
                    indexPath.section
                ]

            let items =
                catalog.items(
                    inGroup: selectedGroup,
                    subgroup: subgroup
                )

            emoji =
                items[indexPath.item].value
        }

        emojiCell.configure(
            emoji: emoji
        )

        return emojiCell
    }

    // MARK: - Selection

    func collectionView(
        _ collectionView: UICollectionView,
        didSelectItemAt indexPath: IndexPath
    ) {

        let emoji: String

        if showingRecent {

            emoji = history.recent[
                indexPath.item
            ]

        } else {

            let subgroup =
                selectedSubgroups[
                    indexPath.section
                ]

            let items =
                catalog.items(
                    inGroup: selectedGroup,
                    subgroup: subgroup
                )

            emoji =
                items[indexPath.item].value
        }

        history.register(emoji)

        // If Recent is currently visible,
        // immediately refresh its ordering.
        if showingRecent {
            collectionView.reloadData()
        }

        onEmojiSelected?(emoji)
    }

    // MARK: - Section headers

    func collectionView(
        _ collectionView: UICollectionView,
        viewForSupplementaryElementOfKind kind: String,
        at indexPath: IndexPath
    ) -> UICollectionReusableView {

        guard kind ==
                UICollectionView.elementKindSectionHeader,
              let header =
                collectionView.dequeueReusableSupplementaryView(
                    ofKind: kind,
                    withReuseIdentifier:
                        KeySenseEmojiSectionHeader.reuseIdentifier,
                    for: indexPath
                ) as? KeySenseEmojiSectionHeader
        else {
            return UICollectionReusableView()
        }

        let title: String

        if showingRecent {

            title = "Recent"

        } else {
          
          let subgroup =
              selectedSubgroups[
                  indexPath.section
              ]

          title =
              displayNameForSubgroup(
                  subgroup
              )
      }

        header.configure(
            title: title
        )

        return header
    }

    // MARK: - Layout

    func collectionView(
        _ collectionView: UICollectionView,
        layout collectionViewLayout:
            UICollectionViewLayout,
        sizeForItemAt indexPath: IndexPath
    ) -> CGSize {

        let columns: CGFloat = 8
        let horizontalPadding: CGFloat = 12
        let interItemSpacing: CGFloat = 2 * (columns - 1)

        let collectionWidth =
            collectionView.bounds.width

        guard collectionWidth > 0 else {
            return CGSize(
                width: 44,
                height: 44
            )
        }

        let availableWidth =
            collectionWidth
            - horizontalPadding
            - interItemSpacing

        let side = floor(
            availableWidth / columns
        )

        return CGSize(
            width: side,
            height: 44
        )
    }

    func collectionView(
        _ collectionView: UICollectionView,
        layout collectionViewLayout:
            UICollectionViewLayout,
        referenceSizeForHeaderInSection section: Int
    ) -> CGSize {

        CGSize(
            width: collectionView.bounds.width,
            height: 28
        )
    }
  
  @objc
  private func categoryButtonTapped(
      _ sender: UIButton
  ) {

      let buttonIndex = sender.tag

      updateCategoryHighlight(
          selectedIndex: buttonIndex
      )

      // MARK: Recent

      if buttonIndex == 0 {

          showingRecent = true

          collectionView.reloadData()
          collectionView.layoutIfNeeded()

          collectionView.setContentOffset(
              .zero,
              animated: false
          )

          return
      }

      // MARK: Normal Unicode categories

      showingRecent = false

      let groupIndex =
          buttonIndex - 1

      guard groupIndex >= 0,
            groupIndex < groups.count
      else {
          return
      }

      selectedGroupIndex =
          groupIndex

      collectionView.reloadData()
      collectionView.layoutIfNeeded()

      collectionView.setContentOffset(
          .zero,
          animated: false
      )
    
  }
  
  private func scrollToSectionHeader(
      section: Int
  ) {

      guard section >= 0,
            section < collectionView.numberOfSections
      else {
          return
      }

      collectionView.layoutIfNeeded()

      let indexPath = IndexPath(
          item: 0,
          section: section
      )

      if let attributes =
          collectionView.collectionViewLayout
              .layoutAttributesForSupplementaryView(
                  ofKind: UICollectionView.elementKindSectionHeader,
                  at: indexPath
              ) {

          let targetY =
              attributes.frame.minY
              - collectionView.adjustedContentInset.top

          collectionView.setContentOffset(
              CGPoint(
                  x: 0,
                  y: max(
                      -collectionView.adjustedContentInset.top,
                      targetY
                  )
              ),
              animated: true
          )

      } else {

          // Fallback if the header layout attributes
          // are not available yet.
          collectionView.scrollToItem(
              at: indexPath,
              at: .top,
              animated: true
          )
      }
  }
  
  /*
  func scrollViewDidScroll(
      _ scrollView: UIScrollView
  ) {

      guard scrollView === collectionView,
            !showingRecent
      else {
          return
      }

      let visibleIndexPaths =
          collectionView.indexPathsForVisibleItems

      guard let first =
          visibleIndexPaths.min(
              by: {
                  if $0.section == $1.section {
                      return $0.item < $1.item
                  }

                  return $0.section < $1.section
              }
          )
      else {
          return
      }

      let buttonIndex =
          first.section + 1

      if buttonIndex !=
          selectedCategoryButtonIndex {

          updateCategoryHighlight(
              selectedIndex: buttonIndex
          )
      }
  }
   
   */
  
  func refreshForPresentation() {

      isReadyForPresentation = true

      view.setNeedsLayout()
      view.layoutIfNeeded()

      collectionView.collectionViewLayout
          .invalidateLayout()

      collectionView.reloadData()

      collectionView.setNeedsLayout()
      collectionView.layoutIfNeeded()

      collectionView.setContentOffset(
          .zero,
          animated: false
      )
  }
  
  @objc
  private func abcButtonTapped() {
      onABCRequested?()
  }

  @objc
  private func spaceButtonTapped() {
      onSpaceRequested?()
  }

  @objc
  private func backspaceButtonTapped() {
      onBackspaceRequested?()
  }
  
}


// MARK: - Emoji Cell

private final class KeySenseEmojiCell:
    UICollectionViewCell {

    static let reuseIdentifier =
        "KeySenseEmojiCell"

    private let emojiLabel = UILabel()

    override init(
        frame: CGRect
    ) {
        super.init(frame: frame)

        emojiLabel.translatesAutoresizingMaskIntoConstraints =
            false

        emojiLabel.textAlignment = .center

        emojiLabel.font =
            UIFont.systemFont(ofSize: 30)

        emojiLabel.numberOfLines = 1

        emojiLabel.adjustsFontSizeToFitWidth = false

        emojiLabel.lineBreakMode = .byClipping

        contentView.addSubview(
            emojiLabel
        )

        NSLayoutConstraint.activate([
            emojiLabel.topAnchor.constraint(
                equalTo: contentView.topAnchor
            ),
            emojiLabel.leadingAnchor.constraint(
                equalTo: contentView.leadingAnchor
            ),
            emojiLabel.trailingAnchor.constraint(
                equalTo: contentView.trailingAnchor
            ),
            emojiLabel.bottomAnchor.constraint(
                equalTo: contentView.bottomAnchor
            )
        ])
    }

    required init?(coder: NSCoder) {
        fatalError(
            "init(coder:) has not been implemented"
        )
    }

    func configure(
        emoji: String
    ) {
        emojiLabel.text = emoji
    }

    override func prepareForReuse() {
        super.prepareForReuse()

        emojiLabel.text = nil
    }
}


// MARK: - Section Header

private final class KeySenseEmojiSectionHeader:
    UICollectionReusableView {

    static let reuseIdentifier =
        "KeySenseEmojiSectionHeader"

    private let titleLabel = UILabel()

    override init(
        frame: CGRect
    ) {
        super.init(frame: frame)

        titleLabel.translatesAutoresizingMaskIntoConstraints =
            false

        titleLabel.font =
            UIFont.systemFont(
                ofSize: 13,
                weight: .semibold
            )

        addSubview(titleLabel)

        NSLayoutConstraint.activate([
            titleLabel.leadingAnchor.constraint(
                equalTo: leadingAnchor,
                constant: 8
            ),
            titleLabel.trailingAnchor.constraint(
                equalTo: trailingAnchor,
                constant: -8
            ),
            titleLabel.centerYAnchor.constraint(
                equalTo: centerYAnchor
            )
        ])
    }

    required init?(coder: NSCoder) {
        fatalError(
            "init(coder:) has not been implemented"
        )
    }

    func configure(
        title: String
    ) {
        titleLabel.text = title
    }
}

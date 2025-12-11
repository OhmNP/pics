# Feature Update Request: Android UI Redesign & Modernization

**Date:** 2025-12-11
**Status:** DRAFT
**Priority:** High

## 1. Overview
This feature request targets a complete visual overhaul of the Android client application to match the new "Cyberpunk/Neon" aesthetic provided in the UI mockups. The goal is to create a premium, polished user experience using Jetpack Compose, emphasizing dark themes, glassmorphism, and smooth animations.

## 2. Global Design System
- **Theme:** Dark mode by default.
- **Color Palette:**
    - Background: Deep Navy/Black gradients.
    - Accents: Neon Cyan (`#00E5FF`) and Purple (`#E91E63` / `#BF5AF2`).
    - Success: Neon Green.
- **Components:**
    - **Glassmorphism:** Heavy use of translucent cards with blurred backgrounds for containers.
    - **Glow Effects:** Outer/Inner shadows to create neon glow effects.
    - **Typography:** Modern, sans-serif font (e.g., Outfit or similar) with bold headers.

## 3. Screen Specifications

### 3.1 Home Screen (Dashboard)
**Current:** `HomeScreen.kt` - Partial implementation exists.
**Requirements:**
- **Header:**
    - Greeting text: "Good Evening, [User]" (Dynamic based on time/user).
    - "PhotoSync" logo/branding.
- **Sync Status Card (Hero):**
    - Large glass card showing current sync status.
    - **Visualizer:** Animated audio-wave style bars indicating activity.
    - **Progress Bar:** Linear progress indicator with percentage.
    - Text: "Sync Active" / "Sync Paused" etc.
- **Status Grid:**
    - **Connectivity:** Square glass card with WiFi icon and "Server Connected" status (Green dot indicator).
    - **Storage:** Square glass card with Circular Progress indicator showing used storage (e.g., "45%").
- **Recent Uploads:**
    - Section header "Recent Uploads" with chevron >.
    - Horizontal list of recently uploaded image thumbnails.
    - Thumbnails should have rounded corners and timestamp overlay.

### 3.2 Photo Gallery Screen
**Current:** `GalleryScreen.kt` - Basic grid exists.
**Requirements:**
- **Header:**
    - Title "Photo Gallery".
    - **Search Bar:** Prominent search input field "Search Photos..." with glass style.
    - **Filter Button:** Icon button next to search bar.
- **Photo Grid:**
    - **Layout:** Staggered or Masonry grid to handle mixed aspect ratios aesthetically (or refined standard grid).
    - **Card Style:** Photos displayed as cards with rounded corners.
    - **Overlays:**
        - Date (e.g., "Sep 05, 2023").
        - Location (e.g., "@Location").
        - Gradient overlay at the bottom of each photo to ensure text readability.
        - **Sync Status:** Small green dot indicator to show synced status (e.g., Green for synced, Gray/Empty for pending).
    - **Interaction:**
        - Tap to view full screen.
        - Long press for selection/multi-select.

### 3.3 Sync Status Screen
**Current:** `SyncStatusScreen.kt` - Basic implementation.
**Requirements:**
- **Hero Progress:**
    - Large, central Circular Progress Indicator with double rings (Cyan/Purple).
    - Interior Text: Big percentage font (e.g., "85%") and status text ("Syncing...").
    - Glow effect around the circle.
- **Detailed Status List:**
    - **Backup in Progress:** Linear progress bar, photo count (e.g., "345 Photos"), estimated time.
    - **Uploading Videos:** File count, size progress.
    - **Completed:** Summary of items finished today.
- **Controls:**
    - "Pause Sync" / "Resume Sync" button.
    - Button styling: Full-width glass button with icon.

### 3.4 Main Navigation
**Current:** `PhotoSyncNavigation.kt` (Requires verification/update).
**Requirements:**
- **Bottom Navigation Bar:**
    - Persistent across top-level screens.
    - Glassmorphism background suitable for overlaying content.
    - **Items:** Home, Gallery, Sync, Settings.
    - **Active State:** Highlighted icon + text, potentially with a glow or underline indicator.

## 4. Technical Considerations
- **Animations:** Use `androidx.compose.animation` for smooth transitions between states (especially the sync visualizer and progress circles).
- **Performance:** Ensure the glass effects (`blur`) do not severely impact scrolling performance on lower-end devices. Use `RenderEffect` cautiously.
- **Assets:** Need to import new vector icons for the bottom nav and status indicators if current ones don't match.

## 5. Implementation Plan
1.  **Design System Update:** Refine `Color.kt` and `Theme.kt` to match the exact hex codes from the mockup (requires color picking or safe estimates).
2.  **Navigation Scaffold:** Create a `MainScreen` scaffold that hosts the BottomNavigation and the `NavHost`.
3.  **Home Screen Refactor:** Update `HomeScreen.kt` to match the new layout.
4.  **Gallery Screen Refactor:** Rewrite `GalleryScreen.kt` to include search and improved grid items.
5.  **Sync Screen Refactor:** Rewrite `SyncStatusScreen.kt` completely.


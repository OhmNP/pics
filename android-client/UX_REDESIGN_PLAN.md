# Android PhotoSync UX Redesign & Code Reorganization

Reorganize the Android client application with proper frontend/backend separation following MVVM architecture and create an enhanced user experience with multiple screens, real-time sync status, and visual feedback.

## User Review Required

> [!IMPORTANT]
> **Major Architecture Change**: This plan proposes a significant restructuring from a single-screen app to a multi-screen MVVM architecture. This will require substantial code reorganization but will provide a much better foundation for future features.

> [!IMPORTANT]
> **New Dependencies**: Adding Jetpack Navigation, ViewModel, and Coil for image loading. These are standard Android libraries that will improve app architecture and performance.

> [!NOTE]
> **UX Improvements**: The new design includes 4 main screens (Home Dashboard, Photo Gallery, Sync Status, Settings) with bottom navigation for easy access. Users will see real-time sync progress, connection status, and uploaded photos.

## Proposed Changes

### Backend Layer (Data & Business Logic)

#### [NEW] [SyncRepository.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/repository/SyncRepository.kt)
Central repository for managing sync operations and state. Provides:
- StateFlow for sync progress (photos uploaded, total, current file)
- StateFlow for connection status
- Methods to trigger sync, get sync history
- Bridges between services and UI layer

#### [NEW] [PhotoRepository.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/repository/PhotoRepository.kt)
Repository for photo data access. Provides:
- Methods to fetch local photos
- Sync history from database
- Photo metadata queries

#### [MODIFY] [ConnectionManager.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/data/ConnectionManager.kt)
Add StateFlow for connection status to enable reactive UI updates:
- `connectionStatus: StateFlow<ConnectionStatus>` (Connected, Disconnected, Connecting, Error)
- Emit status changes when connection state changes

#### [MODIFY] [LocalDB.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/data/LocalDB.kt)
Enhance database to track sync history:
- Add `sync_history` table (timestamp, photos_synced, bytes_transferred)
- Add methods to query sync statistics

#### [MODIFY] [SyncService.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/service/SyncService.kt)
Update to emit progress events to repository:
- Report current file being uploaded
- Report progress percentage
- Report completion/error states

#### [MODIFY] [ConnectionService.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/service/ConnectionService.kt)
Update to use ConnectionManager's StateFlow for status updates

---

### Frontend Layer (UI & Presentation)

#### [NEW] [HomeViewModel.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/viewmodel/HomeViewModel.kt)
ViewModel for home dashboard screen:
- Exposes connection status
- Exposes recent sync statistics
- Exposes quick action methods

#### [NEW] [GalleryViewModel.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/viewmodel/GalleryViewModel.kt)
ViewModel for photo gallery screen:
- Loads local photos from MediaStore
- Provides pagination support
- Tracks which photos are synced

#### [NEW] [SyncViewModel.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/viewmodel/SyncViewModel.kt)
ViewModel for sync status screen:
- Real-time sync progress
- Current file being uploaded
- Sync history
- Methods to start/stop sync

#### [NEW] [SettingsViewModel.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/viewmodel/SettingsViewModel.kt)
ViewModel for settings screen:
- Server configuration
- Permission status
- App preferences

#### [NEW] [HomeScreen.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/ui/screens/HomeScreen.kt)
Main dashboard showing:
- Connection status card with visual indicator
- Sync statistics (total photos synced, last sync time)
- Quick action buttons (Start Sync, View Gallery)
- Storage usage visualization

#### [NEW] [GalleryScreen.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/ui/screens/GalleryScreen.kt)
Photo gallery with:
- Grid layout of local photos
- Sync status badge on each photo (synced/pending)
- Pull-to-refresh
- Lazy loading for performance

#### [NEW] [SyncStatusScreen.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/ui/screens/SyncStatusScreen.kt)
Detailed sync status showing:
- Real-time progress bar
- Current file being uploaded with thumbnail
- Upload speed and ETA
- Sync history list
- Start/Stop sync button

#### [NEW] [SettingsScreen.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/ui/screens/SettingsScreen.kt)
Settings interface with:
- Server IP and port configuration
- Permission management
- Auto-sync toggle
- About section

#### [NEW] [Navigation.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/ui/navigation/Navigation.kt)
Navigation setup with:
- Bottom navigation bar
- Screen routes (Home, Gallery, Sync, Settings)
- Navigation graph

#### [NEW] [components/ConnectionStatusCard.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/ui/components/ConnectionStatusCard.kt)
Reusable component showing connection status with animated indicator

#### [NEW] [components/SyncProgressCard.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/ui/components/SyncProgressCard.kt)
Reusable component for displaying sync progress

#### [NEW] [components/PhotoGridItem.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/ui/components/PhotoGridItem.kt)
Grid item for photo gallery with sync status badge

#### [MODIFY] [MainActivity.kt](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/src/main/java/com/photosync/android/MainActivity.kt)
Simplify to host navigation:
- Remove existing settings UI
- Set up NavHost with bottom navigation
- Handle permission requests globally

---

### Dependencies & Configuration

#### [MODIFY] [build.gradle](file:///c:/Users/parim/Desktop/pics/pics/android-client/app/build.gradle)
Add required dependencies:
- `androidx.navigation:navigation-compose` for navigation
- `androidx.lifecycle:lifecycle-viewmodel-compose` for ViewModels
- `io.coil-kt:coil-compose` for image loading
- `androidx.lifecycle:lifecycle-runtime-compose` for collecting StateFlow

## Proposed Folder Structure

```
app/src/main/java/com/photosync/android/
├── MainActivity.kt
├── data/                          # Backend - Data Layer
│   ├── ConnectionManager.kt       # (Modified) Add StateFlow
│   ├── LocalDB.kt                 # (Modified) Add sync history
│   ├── MediaScanner.kt            # (Existing)
│   └── SettingsManager.kt         # (Existing)
├── model/                         # Backend - Data Models
│   ├── FileMeta.kt                # (Existing)
│   ├── ConnectionStatus.kt        # (New) Sealed class for status
│   └── SyncProgress.kt            # (New) Data class for progress
├── repository/                    # Backend - Repository Layer
│   ├── SyncRepository.kt          # (New)
│   └── PhotoRepository.kt         # (New)
├── service/                       # Backend - Background Services
│   ├── ConnectionService.kt       # (Modified)
│   └── SyncService.kt             # (Modified)
├── viewmodel/                     # Frontend - Presentation Logic
│   ├── HomeViewModel.kt           # (New)
│   ├── GalleryViewModel.kt        # (New)
│   ├── SyncViewModel.kt           # (New)
│   └── SettingsViewModel.kt       # (New)
└── ui/                            # Frontend - UI Layer
    ├── screens/                   # Screen composables
    │   ├── HomeScreen.kt          # (New)
    │   ├── GalleryScreen.kt       # (New)
    │   ├── SyncStatusScreen.kt    # (New)
    │   └── SettingsScreen.kt      # (New)
    ├── components/                # Reusable UI components
    │   ├── ConnectionStatusCard.kt # (New)
    │   ├── SyncProgressCard.kt    # (New)
    │   └── PhotoGridItem.kt       # (New)
    ├── navigation/                # Navigation setup
    │   └── Navigation.kt          # (New)
    └── theme/                     # (Existing)
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

## User Experience Flow

### 1. **App Launch**
- User opens app → Home Dashboard appears
- Connection status card shows "Connecting..." with animated indicator
- Once connected, status changes to "Connected" with green indicator
- Dashboard shows sync statistics

### 2. **View Photos**
- User taps "Gallery" in bottom nav
- Grid of photos loads with lazy loading
- Each photo shows sync status badge (✓ synced, ⏱ pending)
- Pull down to refresh

### 3. **Start Sync**
- User taps "Start Sync" button on Home or Sync screen
- Permission check happens automatically
- Navigate to Sync Status screen
- Real-time progress bar shows upload progress
- Current file thumbnail and name displayed
- Upload speed and ETA shown

### 4. **Monitor Sync**
- Sync Status screen shows live updates
- Progress bar animates smoothly
- Notification shows background progress
- Can navigate away and return to see progress

### 5. **Configure Settings**
- User taps "Settings" in bottom nav
- Can update server IP/port
- Toggle auto-sync on/off
- Request permissions
- Save triggers automatic reconnection

## Verification Plan

### Automated Tests

Since this is primarily a UI/UX redesign, automated testing will focus on ViewModels and repositories:

```bash
# Run unit tests (to be created)
cd c:\Users\parim\Desktop\pics\pics\android-client
.\gradlew test
```

New test files to create:
- `SyncRepositoryTest.kt` - Test StateFlow emissions
- `PhotoRepositoryTest.kt` - Test data queries
- `HomeViewModelTest.kt` - Test state management

### Manual Verification

1. **Navigation Testing**
   - Open app and verify Home screen appears
   - Tap each bottom nav item (Home, Gallery, Sync, Settings)
   - Verify each screen loads correctly
   - Verify back button behavior

2. **Connection Status Testing**
   - Start app with server offline → verify "Disconnected" status shows
   - Start server → verify status changes to "Connected" with green indicator
   - Stop server → verify status changes to "Disconnected"

3. **Sync Progress Testing**
   - Navigate to Sync Status screen
   - Tap "Start Sync" button
   - Verify progress bar updates in real-time
   - Verify current file name and thumbnail appear
   - Verify sync completes successfully

4. **Gallery Testing**
   - Navigate to Gallery screen
   - Verify photos load in grid layout
   - Verify sync status badges appear correctly
   - Pull down to refresh and verify reload

5. **Settings Testing**
   - Navigate to Settings screen
   - Change server IP and port
   - Tap Save
   - Verify connection reconnects automatically
   - Verify new settings persist after app restart

6. **Permission Testing**
   - Fresh install app
   - Verify permission request appears when needed
   - Grant permission and verify sync can start
   - Deny permission and verify appropriate error message

**User Action Required**: After implementation, please test the app by:
1. Installing on Android device/emulator
2. Following the manual verification steps above
3. Providing feedback on UX and any issues encountered

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Document-driven feature request process.
- Architectural documentation.
- Feature 1: Login & Session Handling.
- Feature 2: Client List.
- Feature 3: Client Details Page.
- Feature 4: Storage Overview.
- Feature 5: Media Grid View with infinite scroll.
- Feature 6: Search & Filters (Server-side & FTS).
- Feature 7: Settings Page (Dynamic configuration).
- Feature 8: Pairing Token Generator (QR Code).
- Feature 9: Error / Warning Feed.
- Feature 10: Server Health Page.
- Feature 11: Manual Thumbnail Regenerator.
- Feature 12: Activity / Audit Logs.

### Changed (Android Client Refactor 2025-12-08)
- **Consolidated Architecture**: Merged `ConnectionService` into `EnhancedSyncService` for a single, robust server interaction point.
- **Unified Sync Logic**: Consolidated sync state management into `DashboardViewModel`, removing `SyncViewModel`.
- **Cleanup**: Removed redundant components (`PhotoRepository`, `SyncService`, `LocalDB`, `MediaScanner`, `PhotoGridItem`).
- **Standardization**: Unified `SyncProgress` model usage and updated `SyncStatusScreen` to use `DashboardViewModel`.
- **Stability**: Fixed "Phenotype API error" (Room/kapt), "Error loading photos" (MediaPagingSource threading), and resolved all build failures.

## [1.0.0] - 2023-10-01
### Added
- Initial release of PhotoSync.
- Desktop Server (C++).
- Android Client (Kotlin).
- UI Dashboard (React).

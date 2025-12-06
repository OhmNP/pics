# Feature Development Process

This document outlines the workflow for adding features or modifying the PhotoSync project using a document-driven approach.

## High-Level Workflow

1.  **Define**: Human creates a Feature Request document.
2.  **Plan**: AI Agent analyzes the request and creates an Implementation Plan.
3.  **Execute**: AI Agent writes code and tests.
4.  **Verify**: Human reviews and verifies the feature.
5.  **Document**: System documentation is updated to reflect the new reality.

---

## Step-by-Step Guide

### 1. Request a Feature
To start a new task, create a new file in `docs/requests/` using the standard template.

1.  Copy `docs/templates/FEATURE_REQUEST_TEMPLATE.md`.
2.  Save it as `docs/requests/YYYY-MM-DD-short-feature-name.md` (e.g., `2023-10-27-dark-mode.md`).
3.  Fill in the sections. Be specific about **UX** and **Technical Contracts** (API/DB changes).

### 2. Agent Planning
Ask the AI Agent to "Review the feature request at docs/requests/..."

The Agent will:
1.  Read your request.
2.  Cross-reference with `docs/ARCHITECTURE.md` and `docs/TECHNICAL_CONTRACTS.md` to identify conflicts or necessary updates.
3.  Generate an `implementation_plan.md` in the brain/workspace.
    *   *Note: This plan will list specific file edits and verification steps.*

### 3. Approval & Execution
Once you approve the plan, the Agent will:
1.  Write the necessary code.
2.  Run tests.
3.  Update the `Status` in your Feature Request document to `Implemented`.

### 4. Documentation Update (Critical)
After implementation is complete, the Agent must update the "Living Documents" to keep them true.
*   Update `docs/TECHNICAL_CONTRACTS.md` if the API or Database schema changed.
*   Update `docs/ARCHITECTURE.md` if new components were added.

### 5. Release & Versioning
After a feature is successfully implemented and verified:

1.  **Update Changelog**: Add an entry to `CHANGELOG.md` under `[Unreleased]` or a new version header.
2.  **Bump Versions**: If this is a release, increment the version numbers in:
    *   `desktop-server/CMakeLists.txt`: `project(PhotoSyncServer VERSION X.Y.Z ...)`
    *   `android-client/app/build.gradle`: `versionName "X.Y"`, `versionCode N`
    *   `desktop-server/ui-dashboard/package.json`: `"version": "X.Y.Z"`

---

## Directory Structure

```text
docs/
├── ARCHITECTURE.md          # Living
├── TECHNICAL_CONTRACTS.md   # Living (The Law)
├── REQUIREMENTS.md          # Living
├── PROCESS.md               # This file
├── templates/
│   └── FEATURE_REQUEST_TEMPLATE.md
└── requests/                # Archive of all work items
    ├── 2023-10-01-initial-setup.md
    └── ...
```

# Rename App to BattSync - Walkthrough

The application has been renamed from "WearBatteryMonitor" to "BattSync" across both mobile and wear modules.

## Changes Made

### 1. Mobile Module
- Updated `mobile/src/main/res/values/strings.xml` to change `app_name` to "BattSync".

### 2. Wear Module
- Updated `wear/src/main/res/values/strings.xml` to change `app_name` to "BattSync".
- Updated `tile_label` to "BattSync Tile" for a consistent brand experience.
- Updated `complication_label` to "BattSync Complication" to match the new branding.

## Verification Results

### Build Success
- `gradle_assemble_all`: **Passed**

### UI Verification
- The app will now appear as "BattSync" in the launcher.
- Tiles and Complications will now be grouped under the "BattSync" name.

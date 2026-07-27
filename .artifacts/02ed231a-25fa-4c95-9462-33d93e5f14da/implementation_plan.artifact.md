# Update SDK Versions and Cleanup Build Logic

Humein `targetSdk` aur `compileSdk` ko sahi (latest stable) version par lana hai aur build logic ko clean karna hai.

## User Review Required
- **SDK Version Choice:** Maine plan kiya hai ki hum API 35 (Android 15) use karein kyunki ye abhi ke liye sabse stable aur widely supported hai, jabki 36/37 naye hain.
- **Version Catalog:** Main saare versions `libs.versions.toml` mein move kar raha hoon.

## Proposed Changes

### [Component: Build Configuration]

#### [MODIFY] [libs.versions.toml](file:///C:/Users/harsh/AndroidStudioProjects/AllTimeMusic-UI_improve/gradle/libs.versions.toml)
- SDK versions (`min`, `target`, `compile`) add karna.
- Lottie library ka version add karna.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/harsh/AndroidStudioProjects/AllTimeMusic-UI_improve/app/build.gradle.kts)
- Hardcoded versions ko `libs.versions` se replace karna.
- Lottie dependency ko version catalog se link karna.

## Verification Plan
### Automated Tests
- `gradle_sync` execute karna verification ke liye.
- `analyze_file` se check karna ki warnings chali gayi hain.

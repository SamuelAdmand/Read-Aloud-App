# Gradle Terminal Guide for ReadAloud

This guide provides terminal commands for common actions you perform in Android Studio.

## 🚀 Basic Commands

### **Syncing the Project**
When you change `build.gradle.kts` or `libs.versions.toml`, you need to sync. In the terminal, running any task triggers a sync.
- **Normal sync**: `./gradlew help`
- **Force dependency refresh**: `./gradlew build --refresh-dependencies`

### **Building the App**
- **Assemble Debug APK**: `./gradlew :app:assembleDebug`
- **Assemble Release APK**: `./gradlew :app:assembleRelease`

### **Installing and Running**
- **Install on Device**: `./gradlew :app:installDebug`
- **Install and Launch**:
  ```powershell
  ./gradlew :app:installDebug; adb shell am start -n com.samuel.readaloud/.MainActivity
  ```

---

## 🛠 Advanced Build Options

### **Cleaning the Project**
Equivalent to "Build -> Clean Project". Deletes all build artifacts.
- **Command**: `./gradlew clean`

### **Viewing Build Reports**
After a build, you can see a detailed breakdown of tasks.
- **Command**: `./gradlew build --scan` (Gives you a URL to a web-based report)

---

## 🐞 Debugging and Troubleshooting

### **Verbose Logging**
If a build fails, add these flags to see why:
- **`--stacktrace`**: Shows where the error happened in the code.
- **`--info`**: Shows more detailed output of what Gradle is doing.
- **`--debug`**: Very large amount of output; use as a last resort.

**Example**: `./gradlew assembleDebug --stacktrace`

### **Checking Dependencies**
If you have a library conflict:
- **Command**: `./gradlew :app:dependencies`

---

## 🧪 Testing

### **Run Unit Tests**
Runs all tests in `src/test`.
- **Command**: `./gradlew :app:testDebugUnitTest`

### **Run Connected (Android) Tests**
Runs all tests in `src/androidTest` on a connected device.
- **Command**: `./gradlew :app:connectedDebugAndroidTest`

---

## 💡 Quick Tips
- **Daemon**: Gradle runs a background process (daemon) to make subsequent builds faster. You don't need to do anything, but if things get "weird", run `./gradlew --stop` to reset it.
- **Autocomplete**: In many terminals, you can press `Tab` after typing `./gradlew :app:` to see available tasks.

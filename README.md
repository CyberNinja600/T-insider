# T-insider

T-insider is a highly polished, multi-pane terminal emulation and SSH client application designed for modern software development. It features a robust, split-pane layout model, advanced visual tab manipulation, high-performance terminal rendering, and dynamic run-time styling.

---

## 🛠️ Core Language & Build Tools

The application leverages a modern, robust, and platform-integrated stack:

*   **Core Language:** **Java 21 (LTS)** — Utilizes modern language features (pattern matching, enhanced switch expressions, and virtual threads ready) for clean and efficient execution.
*   **Build System:** **Apache Maven 3.9+** — Standardized build orchestration and dependency management. Includes standard Maven wrappers (`mvnw` / `mvnw.cmd`) for absolute zero-setup bootstrapping.
*   **UI & Graphics Framework:** **JavaFX 21.0.2** — Delivers high-performance, hardware-accelerated user interfaces styled dynamically via CSS.
*   **Native Platform Integration:** **Java Native Access (JNA) 5.13.0** — Interacts with operating system APIs for premium native effects like Windows Aero glass integration, custom frame decorations, and glass drag-and-drop.

---

## 🚀 Key Features (Prioritized)

This is the curated list of T-insider features, ranked by engineering complexity, user-facing importance, and architectural centrality:

### 1. Terminal Splitting & Dynamic Grid Layout (BSP Tree)
*   **Description:** Supports infinite horizontal and vertical terminal splitting to create custom grid layouts.
*   **Under the Hood:** Modeled as a **Binary Space Partitioning (BSP) Split Tree**. `TerminalSplitPane` coordinates split relationships across tree nodes, using `SplitParentNode` (JavaFX `SplitPane` wrappers) and `SplitLeafNode` (holding actual `TerminalPane` elements). Supports drag-and-drop tab-splitting, resize listeners, automatic sibling collapse/cleanup, and seamless focus preservation when closing panes.

### 2. Advanced Terminal Tab Drag-and-Drop with Animations
*   **Description:** Rich, interactive tab reordering and drag-to-dock actions with immersive visual indicators.
*   **Under the Hood:** Orchestrated by `TabDragManager`. Spawns an animated floating snapshot of the tab (`TabGhost`) with premium shadow effects. As you drag, adjacent tabs dynamically slide out of the way via `TabAnimationManager`, and edge auto-scrolling is triggered if hovering near boundaries. Uses DPI-immune scene coordinate calculations to pinpoint drop targets.

### 3. SSH Client Integration & Modern Cryptography
*   **Description:** High-performance, secure remote terminal connections that stay alive under any network conditions.
*   **Under the Hood:** Powered by **Apache MINA SSHD (v2.13.0)**. Employs a robust 15-second heartbeat interval to prevent idle disconnects. Supported by **BouncyCastle** and **I2P EdDSA** providers to seamlessly authenticate with modern OpenSSH credentials, including PEM and Ed25519 host keys.

### 4. Local Shell Terminal Emulation (PTY)
*   **Description:** Complete local terminal access with full ANSI support, rich keyboard intercepts, and shell compatibility.
*   **Under the Hood:** Integrates **JetBrains JediTerm (v3.43)** for ANSI rendering and **Pty4J (v0.12.13)** for native process spawning. Automatically detects and spawns native local shells (CMD, PowerShell, WSL, or Git Bash on Windows; environment shell on Unix). Employs SwingNode event filters to swallow leaks (like JavaFX `Ctrl+C` clashes) and styles custom `JScrollBar` elements to match the selected dark/light theme.

### 5. Dynamic Terminal Font Zooming & Layout Scaling
*   **Description:** Scale the terminal font size dynamically across all active terminal tabs simultaneously. Supports intuitive toolbar buttons and global keyboard shortcuts.
*   **Under the Hood:** Implemented via reflection-based invocations of JediTerm's internal `reinitFontAndResize` API within `TerminalTabFactory`, allowing dynamic adjustments of the viewport font and bounds at runtime. Includes the following user controls:
    *   **Toolbar Buttons:** Use the `-`, `[Percentage]`, and `+` buttons on the top toolbar to decrease, reset, or increase the font size.
    *   **Keyboard Shortcuts:**
        *   **Zoom In:** `Ctrl + =` or `Ctrl + +` (or Numpad `+`)
        *   **Zoom Out:** `Ctrl + -` (or Numpad `-`)
        *   **Reset to 100%:** `Ctrl + 0` (or Numpad `0`)

### 6. Interactive Snippet Management
*   **Description:** A searchable sidebar accordion and full-grid snippet dashboard to store and execute reusable command blocks.
*   **Under the Hood:** Serializes snippet configurations as JSON files (`snippets.json` and `packages.json`) via **Google Gson**. Supports two execution paths:
    *   *Direct Run:* Safely passes the raw command block into the active JediTerm session with an appended carriage return (`\r`) for immediate execution.
    *   *Paste Only:* Injects the raw string directly into the shell buffer without running it immediately.

### 7. Real-Time Word Highlighting & Package Management
*   **Description:** Highlight custom keywords, search queries, or regular expressions across all active terminal views in real-time, grouped and customized via packages.
*   **Under the Hood:** 
    *   *Highlight Models & Serialization:* Managed using `WordHighlight` and `HighlightPackage` models, serialized as standard JSON configurations (`highlights.json` and `packages.json`) through the unified `StorageManager` (utilizing Google Gson).
    *   *Terminal Hooks & Regex Engine:* Intercepts terminal text streams during rendering using JetBrains JediTerm's drawing pipeline. Supports standard string matching, case-insensitive keyword matches, and full Regex-based pattern highlighting, drawn using custom color hex codes.
    *   *Stateful Collapsible Sidebar:* Implements custom accordion components using JavaFX `TitledPane` widgets with state-aware class names (`.active-package`, `.inactive-package`). Manages manual folder expansion state persistence using a dynamic `expandedPackageIds` set, and consumes mouse click/press/release events on package eyes to prevent unwanted accordion toggle side effects.
    *   *Validation & Uniqueness:* Validates package name creations inside `MainApp` and `HighlightDialog` to prevent duplicate packages, raising styled warning dialogs.
    *   *Cohesive Theming:* Fully supports dynamic custom properties inside `theme.config.properties` for active/inactive title background colors, opacities, folder icons, titles, and toggle buttons (incorporating dedicated hover and active colors), hot-swapped dynamically when changing themes.

### 8. SSH Host Connection Profile Management
*   **Description:** Quick-connect host profile database supporting customizable SSH targets, credentials, custom ports, and key files.
*   **Under the Hood:** Leverages a unified JSON storage backend via `StorageManager` to handle host configuration CRUD operations. Provides parsing support for the standard `ssh://` URI scheme to immediately extract hostname, port, and username details.

### 9. Dynamic UI Customization & Live CSS Hot-Reloading
*   **Description:** Real-time hot-swappable custom themes and styling.
*   **Under the Hood:** Custom terminal themes are parsed dynamically from macOS Terminal XML `.plist` files, decoding Base64 floating-point colors and maps them via a decoupled configuration properties mapping (`theme.config.properties`). Includes `LiveReloader` — a background daemon utilizing Java's `WatchService` with a 500ms debounce to automatically hot-reload UI stylesheets during development when CSS or properties are modified.

---

## ⚙️ How to Run

This application is completely self-contained and cross-platform. It requires zero manual configuration or pre-setup!

### Windows
1. On a fresh clone/pull, double-click **`tinsider.cmd`** first.
   - This will launch the application and **automatically recreate/self-heal the `T-Insider.lnk` shortcuts** in both the project root directory and directly on your **Windows Desktop**, instantly mapping them to the correct local paths and restoring the custom program icon on your machine!
2. For all subsequent launches, you can double-click either **`T-Insider.lnk`** (located on your Desktop or in the project folder) or **`tinsider.cmd`**.

### macOS & Linux
1. Open a terminal in the project directory.
2. Make the script executable and run it:
   ```bash
   chmod +x tinsider.sh && ./tinsider.sh
   ```
3. The script will automatically detect or download a portable JDK 21 for your OS and architecture, then start the application seamlessly.

---

## 💻 Local Shell Requirements (Windows)

For **Bash** to be detected and available as a local shell option on Windows, it must be installed in one of the following locations (otherwise, the application dynamically hides the option to prevent PTY creation failures):

1. **System-wide Git Bash**: Installed at `C:\Program Files\Git\bin\bash.exe` or `C:\Program Files (x86)\Git\bin\bash.exe`.
2. **User-local Git Bash**: Installed at `%USERPROFILE%\AppData\Local\Programs\Git\bin\bash.exe`.
3. **System Environment PATH**: If `bash.exe` is directly in your system/user `PATH`, or if `git.exe` is in your `PATH` (the application will automatically locate the corresponding `bin\bash.exe` or `usr\bin\bash.exe`).
4. **WSL (Windows Subsystem for Linux)**: WSL's Bash available at `C:\Windows\System32\bash.exe`.

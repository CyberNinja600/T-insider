# T-insider: Framework & Developer Learning Guide

T-insider is a multi-pane terminal emulation and SSH client application designed for modern software development. It features a robust, split-pane layout model, advanced visual tab manipulation, high-performance terminal rendering, and dynamic run-time styling.

---

This document provides a comprehensive overview of the technology stack, frameworks, internal architecture, and the recommended learning roadmap to develop and contribute to **T-insider**.

---

## 1. Technology Stack & Frameworks Used

T-insider is built on **Java 21 (LTS)**, primarily utilizing **JavaFX** for its modern desktop GUI alongside an embedded **Swing/AWT** layer to host JetBrains' terminal engine.

### Core Stack Overview

| Category / Layer              | Technology               | Version      | Purpose in this Project                                                                                                                                                | Key Project References                                                                                                                                                     |
|:------------------------------|:-------------------------|:-------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Language & Runtime**        | Java (LTS)               | 21           | Core programming language leveraging modern syntax (pattern matching, switch expressions, modern standard library).                                                    | [`Launcher.java`](src/main/java/com/tinsider/Launcher.java)                                                                                                                |
| **Build System**              | Apache Maven             | 3.9+         | Build lifecycle, dependency resolution, execution via `javafx-maven-plugin`.                                                                                           | [`pom.xml`](pom.xml), [`mvnw.cmd`](mvnw.cmd), [`mvnw`](mvnw)                                                                                                               |
| **Primary UI Framework**      | JavaFX                   | 21.0.2       | Controls, layout nodes, scene graph, dialogs, drag animations, custom title bar, and CSS styling.                                                                      | [`MainApp.java`](src/main/java/com/tinsider/ui/MainApp.java), [`TopToolBar.java`](src/main/java/com/tinsider/ui/components/toolbar/TopToolBar.java)                        |
| **Terminal Rendering Engine** | JetBrains JediTerm       | 3.43         | High-performance Swing-based terminal emulation engine (used in JetBrains IDEs like IntelliJ IDEA) for ANSI rendering, text buffer management, and viewport rendering. | [`TerminalTabFactory.java`](src/main/java/com/tinsider/terminal/TerminalTabFactory.java)                                                                                   |
| **JavaFX / Swing Interop**    | JavaFX `SwingNode`       | 21.0.2       | Bridges JediTerm's Swing AWT widget into the JavaFX Scene Graph.                                                                                                       | Embedded within [`TerminalPane.java`](src/main/java/com/tinsider/ui/components/terminal/split/TerminalPane.java)                                                           |
| **Local PTY Spawning**        | JetBrains Pty4J          | 0.12.13      | Pseudo-terminal layer for spawning native local shells (PowerShell, CMD, WSL, Git Bash on Windows; `/bin/bash` or `/bin/zsh` on Unix/macOS).                           | [`PtyTtyConnector.java`](src/main/java/com/tinsider/terminal/PtyTtyConnector.java)                                                                                         |
| **SSH Client Core**           | Apache MINA SSHD         | 2.13.0       | Remote terminal protocol engine handling SSH handshakes, authentication, shell channels, and 15-second heartbeat intervals.                                            | [`SshTtyConnector.java`](src/main/java/com/tinsider/terminal/SshTtyConnector.java), [`ConnectionManager.java`](src/main/java/com/tinsider/terminal/ConnectionManager.java) |
| **Cryptography Providers**    | BouncyCastle & I2P EdDSA | 1.78 / 0.3.0 | Modern cryptographic algorithms for modern OpenSSH keys, PEM format parsing, and Ed25519 host key authentication.                                                      | [`pom.xml`](pom.xml)                                                                                                                                                       |
| **OS / Window Integration**   | JNA (Java Native Access) | 5.13.0       | Native Windows DWM API interop for Windows Aero snapping, dark mode window captioning, and custom framing.                                                             | [`DwmUtil.java`](src/main/java/com/tinsider/ui/theme/DwmUtil.java)                                                                                                         |
| **Configuration & Storage**   | Google Gson              | 2.10.1       | JSON serialization and deserialization for host profiles, snippets, word highlight packages, and app configuration.                                                    | [`StorageManager.java`](src/main/java/com/tinsider/storage/StorageManager.java)                                                                                            |
| **Logging**                   | SLF4J (Simple)           | 2.0.7        | Clean application and SSH session logging.                                                                                                                             | [`pom.xml`](pom.xml)                                                                                                                                                       |

---

## 2. Key Architecture Components

To work on this codebase effectively, familiarize yourself with these core subsystems:

```
src/main/java/com/tinsider/
├── Launcher.java                  # Bootstrap & font anti-aliasing initialization
├── config/                        # Theme parsing (macOS .plist color maps, properties)
├── model/                         # Data models (HostProfile, Snippet, WordHighlight, etc.)
├── storage/                       # JSON persistence layer via Gson
├── terminal/                      # JediTerm, Pty4J, and Apache MINA SSHD connector layers
├── ui/
│   ├── MainApp.java               # Primary JavaFX stage and scene assembly
│   ├── components/
│   │   ├── navigation/            # Slim navigation rail
│   │   ├── sidebar/               # Collapsible accordion views (Snippets, Highlights)
│   │   ├── terminal/
│   │   │   ├── dnd/               # Tab drag-and-drop animation engine & ghost renderer
│   │   │   └── split/             # Binary Space Partitioning (BSP) tree split-pane engine
│   │   └── toolbar/               # Global toolbar (zoom controls, quick actions)
│   ├── dashboard/                 # Full dashboard views for hosts, snippets, sessions
│   ├── dialogs/                   # Custom modal dialogs (HostDialog, SnippetDialog, etc.)
│   └── theme/                     # CSS loading, DWM integration, and live hot-reloader
└── util/                          # Live file watcher, activity logging
```

### Notable Architectural Patterns in the Codebase

1. **Binary Space Partitioning (BSP) Split Tree**:
    - Files: [`TerminalSplitPane.java`](src/main/java/com/tinsider/ui/components/terminal/split/TerminalSplitPane.java), [`SplitParentNode.java`](src/main/java/com/tinsider/ui/components/terminal/split/SplitParentNode.java), [`SplitLeafNode.java`](src/main/java/com/tinsider/ui/components/terminal/split/SplitLeafNode.java).
    - Enables arbitrary horizontal and vertical terminal splitting while maintaining automatic sibling collapse, recursive resizing, and focus preservation.

2. **Advanced Tab Drag-and-Drop with Physics/Animations**:
    - Files: [`TabDragManager.java`](src/main/java/com/tinsider/ui/components/terminal/dnd/TabDragManager.java), [`TabGhost.java`](src/main/java/com/tinsider/ui/components/terminal/dnd/TabGhost.java), [`TabAnimationManager.java`](src/main/java/com/tinsider/ui/components/terminal/dnd/TabAnimationManager.java).
    - Generates an animated floating snapshot of the tab during dragging, dynamically slides adjacent tabs out of the way, and uses DPI-aware scene coordinate math for drop targeting.

3. **Hybrid JavaFX & Swing Rendering**:
    - Files: [`TerminalTabFactory.java`](src/main/java/com/tinsider/terminal/TerminalTabFactory.java), [`TerminalPane.java`](src/main/java/com/tinsider/ui/components/terminal/split/TerminalPane.java).
    - Integrates JediTerm (`JediTermWidget`) inside a JavaFX `SwingNode`, filtering events to prevent keyboard shortcuts (e.g., `Ctrl+C`) from colliding between JavaFX and the terminal shell.

4. **Live CSS Hot-Reloading**:
    - Files: [`LiveReloader.java`](src/main/java/com/tinsider/util/LiveReloader.java).
    - Uses Java's `WatchService` with a 500ms debounce to automatically reload stylesheets during development whenever `.css` files change.

---

## 3. What You Need to Learn to Develop This Project

Here is a recommended learning roadmap broken down by priority:

### Phase 1: Core Java 21 & Foundations
* **Modern Java Syntax**:
    - Pattern matching for `switch` and `instanceof`.
    - Records and text blocks.
* **Concurrency & Thread Safety**:
    - Thread pools and background executors.
    - Ensuring background tasks (like SSH connections or process I/O) never block the UI thread.
* **Java Reflection**:
    - Understanding runtime reflection (used in `TerminalTabFactory` to dynamically adjust JediTerm's font buffers).

### Phase 2: JavaFX Desktop UI Development
* **Layout Management**:
    - Deep understanding of `BorderPane`, `SplitPane`, `StackPane`, `VBox`, `HBox`.
    - Custom sizing policies, layout constraints, and min/pref/max dimensions.
* **Reactive UI**:
    - `ObservableList`, `SimpleBooleanProperty`, `SimpleStringProperty`, and change listeners.
* **Event Handling**:
    - Mouse event phases (capturing vs. bubbling, `consume()`).
    - Drag-and-drop mechanics (`DragEvent`, drag-boards, screen-to-local coordinate transformations).
* **JavaFX CSS Styling**:
    - How JavaFX CSS works (`-fx-*` properties, selectors, pseudo-classes like `:hover`, `:focused`).
    - Modular CSS file structures (see `src/main/resources/css/`).

### Phase 3: Swing / AWT & `SwingNode` Interoperability
* **Dual Event Loops**:
    - JavaFX has the **JavaFX Application Thread** (`Platform.runLater`).
    - Swing/AWT has the **Event Dispatch Thread (EDT)** (`SwingUtilities.invokeLater`).
    - Knowing which thread must execute code interacting with Swing widgets vs. JavaFX nodes is critical to avoid deadlocks.
* **Event Filtering**:
    - Intercepting and filtering keys on `SwingNode` to route terminal-specific keystrokes smoothly.

### Phase 4: Terminal Emulation & System Processes (PTY)
* **Terminal Protocol Basics**:
    - How VT100 / ANSI escape sequences work (colors, cursor positioning, clear screen).
    - TTY/PTY concept: pseudo-terminals providing a bidirectional bridge between a shell process and a graphical UI.
* **JetBrains JediTerm**:
    - Implementing `TtyConnector` for stream I/O (`PtyTtyConnector` and `SshTtyConnector`).
    - Text filtering, regex highlighting pipelines, and custom font scaling.
* **JetBrains Pty4J**:
    - Spawning Windows (`cmd.exe`, `powershell.exe`, Git Bash, WSL) and Unix shells.
    - Updating terminal window dimensions (`setPtyDataSize`) when the split pane is resized.

### Phase 5: Remote Networking & SSH Protocols
* **Apache MINA SSHD**:
    - Creating SSH clients (`SshClient`), establishing sessions, and handling client authentication.
    - Opening `ChannelShell`, setting environment variables (`TERM=xterm-256color`), and handling terminal signals.
    - Keep-alive heartbeat mechanisms to prevent timeout disconnects.
* **Public Key Cryptography**:
    - OpenSSH keys, RSA, ECDSA, and modern Ed25519 host keys using BouncyCastle.

### Phase 6: Native OS Integration (Optional / Advanced)
* **Java Native Access (JNA)**:
    - Invoking native C/Windows APIs from Java without writing C JNI glue code.
    - Interacting with `dwmapi.dll` to achieve dark mode window title bars and borderless window snapping on Windows 10/11.

---

## 4. Key Features (Prioritized)

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

## 5. How to Run

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

## 6. Local Shell Requirements (Windows)

For **Bash** to be detected and available as a local shell option on Windows, it must be installed in one of the following locations (otherwise, the application dynamically hides the option to prevent PTY creation failures):

1. **System-wide Git Bash**: Installed at `C:\Program Files\Git\bin\bash.exe` or `C:\Program Files (x86)\Git\bin\bash.exe`.
2. **User-local Git Bash**: Installed at `%USERPROFILE%\AppData\Local\Programs\Git\bin\bash.exe`.
3. **System Environment PATH**: If `bash.exe` is directly in your system/user `PATH`, or if `git.exe` is in your `PATH` (the application will automatically locate the corresponding `bin\bash.exe` or `usr\bin\bash.exe`).
4. **WSL (Windows Subsystem for Linux)**: WSL's Bash available at `C:\Windows\System32\bash.exe`.

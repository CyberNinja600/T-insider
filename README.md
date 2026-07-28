# T-insider

  Core Language & Build Tools
   * Language: Java 21
   * Build System: Maven
   * UI Framework: JavaFX 21.0.2 (using CSS for styling)

  Terminal & SSH Capabilities
   * Terminal Emulation: JetBrains JediTerm (v3.43) - handles the terminal UI and ANSI rendering.
   * Local Process Spawning: JetBrains Pty4J - for local terminal instances (PTY).
   * SSH Client: Apache MINA SSHD (v2.13.0) - handles SSH connections.
   * Cryptography: BouncyCastle and I2P EdDSA - providing support for modern OpenSSH keys (like Ed25519).

  Utilities & Integration
   * Native OS Integration: JNA (Java Native Access) - specifically used for native Windows aero dragging/custom title bars.
   * Data Serialization: Google Gson - for handling JSON configuration (like storing host profiles/snippets).
   * Logging: SLF4J (with slf4j-simple)

## How to Run

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

## Terminal Zoom Feature

You can scale the terminal font size dynamically across all active terminal tabs simultaneously.

### Zoom Controls
- **Toolbar Buttons**: Use the `-`, `[Percentage]`, and `+` buttons located on the top toolbar to decrease, reset, or increase the font size.
- **Keyboard Shortcuts**:
  - **Zoom In**: `Ctrl + =` or `Ctrl + +` (or Numpad `+`)
  - **Zoom Out**: `Ctrl + -` (or Numpad `-`)
  - **Reset to 100%**: `Ctrl + 0` (or Numpad `0`)

## Local Shell Requirements (Windows)

For **Bash** to be detected and available as a local shell option on Windows, it must be installed in one of the following locations (otherwise, the application dynamically hides the option to prevent PTY creation failures):

1. **System-wide Git Bash**: Installed at `C:\Program Files\Git\bin\bash.exe` or `C:\Program Files (x86)\Git\bin\bash.exe`.
2. **User-local Git Bash**: Installed at `%USERPROFILE%\AppData\Local\Programs\Git\bin\bash.exe`.
3. **System Environment PATH**: If `bash.exe` is directly in your system/user `PATH`, or if `git.exe` is in your `PATH` (the application will automatically locate the corresponding `bin\bash.exe` or `usr\bin\bash.exe`).
4. **WSL (Windows Subsystem for Linux)**: WSL's Bash available at `C:\Windows\System32\bash.exe`.
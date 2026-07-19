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
1. Double-click **`T-Insider.lnk`** (the shortcut with the custom program icon) or **`tinsider.cmd`**.
2. The script will automatically detect if you have a compatible Java 21+ environment. If not, it will securely download a portable, isolated JDK 21 (Eclipse Temurin) to a local directory (`.jdks/jdk-21`) and execute the application using the Maven Wrapper.

### macOS & Linux
1. Open a terminal in the project directory.
2. Make the script executable and run it:
   ```bash
   chmod +x tinsider.sh && ./tinsider.sh
   ```
3. The script will automatically detect or download a portable JDK 21 for your OS and architecture, then start the application seamlessly.
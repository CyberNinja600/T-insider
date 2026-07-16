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
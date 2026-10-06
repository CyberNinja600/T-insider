#!/bin/bash
# Exit on error
set -e

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORTABLE_JDK_DIR="${SCRIPT_DIR}/.jdks"
LOCAL_JDK_HOME="${PORTABLE_JDK_DIR}/jdk-21"

# Check for auto-update before environment preparation and execution
check_auto_update() {
    if ! command -v git >/dev/null 2>&1; then
        return 0
    fi
    if [ "$(git rev-parse --is-inside-work-tree 2>/dev/null)" != "true" ]; then
        return 0
    fi
    local current_branch
    current_branch=$(git branch --show-current 2>/dev/null || echo "")
    if [ "$current_branch" != "main" ]; then
        echo "[INFO] Current branch is '$current_branch' (not 'main'). Skipping auto-update."
        return 0
    fi
    local status_output
    status_output=$(git status --porcelain 2>/dev/null || echo "")
    if [ -n "$status_output" ]; then
        echo "[INFO] Uncommitted changes detected. Skipping auto-update to protect local work."
        return 0
    fi
    echo "[INFO] Checking remote updates for branch 'main'..."
    GIT_TERMINAL_PROMPT=0 git -c http.timeout=5 -c connect.timeout=5 pull --ff-only 2>&1 || {
        echo "[WARN] Auto-update skipped or failed. Continuing with local version."
    }
}

check_auto_update


# On macOS, the actual JAVA_HOME is inside Contents/Home of the extracted bundle
OS_TYPE=$(uname -s)
if [ "$OS_TYPE" = "Darwin" ]; then
    LOCAL_JAVA_BIN="${LOCAL_JDK_HOME}/Contents/Home/bin/java"
else
    LOCAL_JAVA_BIN="${LOCAL_JDK_HOME}/bin/java"
fi

is_java21_or_higher() {
    local java_cmd="$1"
    if [ -x "$java_cmd" ]; then
        local version_info
        version_info=$("$java_cmd" -version 2>&1)
        # Match version "21" or higher
        if [[ "$version_info" =~ version\ \"(2[1-9]|[3-9][0-9]+) ]]; then
            return 0
        fi
    fi
    return 1
}

# 1. Check if local portable JDK is already set up and valid
if is_java21_or_higher "$LOCAL_JAVA_BIN"; then
    echo "[INFO] Using portable JDK 21 from $LOCAL_JDK_HOME"
    if [ "$OS_TYPE" = "Darwin" ]; then
        export JAVA_HOME="${LOCAL_JDK_HOME}/Contents/Home"
    else
        export JAVA_HOME="$LOCAL_JDK_HOME"
    fi
    export PATH="${JAVA_HOME}/bin:${PATH}"

# 2. Check if system Java is valid (JDK 21+)
elif command -v java >/dev/null 2>&1 && is_java21_or_higher "$(command -v java)"; then
    echo "[INFO] Compatible system Java (21+) detected."
else
    # 3. Download and extract portable JDK 21
    echo "[INFO] No compatible Java 21+ detected. Automating setup..."
    echo "[INFO] Downloading a self-contained portable JDK 21 (Eclipse Temurin) to run the application..."
    
    mkdir -p "$PORTABLE_JDK_DIR"
    
    if [ -d "$LOCAL_JDK_HOME" ]; then
        echo "[WARN] Found existing but invalid JDK directory. Cleaning up..."
        rm -rf "$LOCAL_JDK_HOME"
    fi
    
    if [ "$OS_TYPE" = "Darwin" ]; then
        OS="mac"
    elif [ "$OS_TYPE" = "Linux" ]; then
        OS="linux"
    else
        echo "[ERROR] Unsupported operating system: $OS_TYPE"
        exit 1
    fi

    ARCH_TYPE=$(uname -m)
    if [ "$ARCH_TYPE" = "x86_64" ]; then
        ARCH="x64"
    elif [ "$ARCH_TYPE" = "arm64" ] || [ "$ARCH_TYPE" = "aarch64" ]; then
        ARCH="aarch64"
    else
        echo "[ERROR] Unsupported architecture: $ARCH_TYPE"
        exit 1
    fi

    URL="https://api.adoptium.net/v3/binary/latest/21/ga/${OS}/${ARCH}/jdk/hotspot/normal/eclipse"
    TAR_PATH="${PORTABLE_JDK_DIR}/jdk.tar.gz"

    echo "[INFO] Fetching JDK from Adoptium..."
    if command -v curl >/dev/null 2>&1; then
        curl -L -o "$TAR_PATH" "$URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$TAR_PATH" "$URL"
    else
        echo "[ERROR] Neither curl nor wget found. Please install curl or wget."
        exit 1
    fi

    echo "[INFO] Extracting files..."
    tar -xzf "$TAR_PATH" -C "$PORTABLE_JDK_DIR"
    rm -f "$TAR_PATH"

    # Find the newly extracted folder (the only folder that is not 'jdk-21') and rename it
    EXTRACTED_DIR=$(find "$PORTABLE_JDK_DIR" -maxdepth 1 -type d -not -path "$PORTABLE_JDK_DIR" -not -name "jdk-21" | head -n 1)
    if [ -n "$EXTRACTED_DIR" ]; then
        mv "$EXTRACTED_DIR" "$LOCAL_JDK_HOME"
    else
        echo "[ERROR] Could not find extracted JDK directory."
        exit 1
    fi

    echo "[INFO] Portable JDK 21 installed successfully!"
    if [ "$OS_TYPE" = "Darwin" ]; then
        export JAVA_HOME="${LOCAL_JDK_HOME}/Contents/Home"
    else
        export JAVA_HOME="$LOCAL_JDK_HOME"
    fi
    export PATH="${JAVA_HOME}/bin:${PATH}"
fi

# Ensure maven wrapper is executable
chmod +x "${SCRIPT_DIR}/mvnw"

echo "[INFO] Executing application via Maven Wrapper..."
cd "$SCRIPT_DIR"
mkdir -p "${SCRIPT_DIR}/logs"

if [ "$1" = "-b" ] || [ "$1" = "--background" ] || [ "$1" = "-hidden" ]; then
    echo "[INFO] Launching T-Insider in the background. Terminal will close..."
    nohup ./mvnw clean compile javafx:run >/dev/null 2>&1 &
    exit 0
else
    ./mvnw clean compile javafx:run 2>&1 | tee "${SCRIPT_DIR}/logs/start.log"
fi
param(
    [switch]$Force
)

$ErrorActionPreference = "Continue"

$ProgressFile = $env:TINSIDER_LOADER_PROGRESS_FILE

function Set-LoaderProgress([int]$Percent, [string]$Message) {
    if (-not [string]::IsNullOrWhiteSpace($ProgressFile)) {
        try {
            [System.IO.File]::WriteAllText(
                $ProgressFile,
                "$Percent|$Message",
                [System.Text.Encoding]::ASCII
            )
        }
        catch {
        }
    }
}

function Log-Message([string]$Message) {
    Write-Host "[AutoUpdate] $Message"
}

try {
    # 1. Verify Git CLI is available on PATH
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        Log-Message "Git command not found on PATH. Skipping auto-update."
        exit 0
    }

    # 2. Verify we are inside a Git repository
    $insideWorkTree = (git rev-parse --is-inside-work-tree 2>$null)
    if ($insideWorkTree -ne "true") {
        Log-Message "Not inside a Git repository. Skipping auto-update."
        exit 0
    }

    $ProjectRoot = (git rev-parse --show-toplevel 2>$null)
    if (-not [string]::IsNullOrWhiteSpace($ProjectRoot)) {
        Set-Location -LiteralPath $ProjectRoot
    }

    # 3. Check current branch (must be 'main' unless Force is requested)
    $currentBranch = (git branch --show-current 2>$null)
    if (-not [string]::IsNullOrWhiteSpace($currentBranch)) {
        $currentBranch = $currentBranch.Trim()
    }
    else {
        $currentBranch = ""
    }

    $isForce = $Force -or ($env:TINSIDER_FORCE_UPDATE -eq "1")

    if (-not $isForce -and $currentBranch -ne "main") {
        Log-Message "Current branch is '$currentBranch' (not 'main'). Skipping auto-update."
        exit 0
    }

    # 4. Check for uncommitted changes (git status --porcelain safety lock)
    $statusOutput = (git status --porcelain 2>$null)
    if (-not $isForce -and -not [string]::IsNullOrWhiteSpace($statusOutput)) {
        Log-Message "Uncommitted changes detected in working tree. Skipping auto-update to protect local work."
        exit 0
    }

    # 5. Check if remote tracking / origin exists
    $remotes = (git remote 2>$null)
    if (-not $remotes -or ($remotes -notcontains "origin" -and -not ($remotes.Count -gt 0))) {
        Log-Message "No remote repository configured. Skipping auto-update."
        exit 0
    }

    Set-LoaderProgress 5 "Checking for updates..."
    Log-Message "Checking remote updates for branch '$currentBranch'..."

    # 6. Execute safe git pull --ff-only with timeout guard and no credentials prompt hang
    $env:GIT_TERMINAL_PROMPT = "0"
    Set-LoaderProgress 10 "Fetching updates..."

    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = "git"
    $processInfo.Arguments = "-c http.timeout=5 -c connect.timeout=5 pull --ff-only"
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true

    $gitProc = [System.Diagnostics.Process]::Start($processInfo)
    $finished = $gitProc.WaitForExit(7000)

    if (-not $finished) {
        try {
            $gitProc.Kill()
        }
        catch {
        }
        Log-Message "Git pull timed out after 7s. Continuing launch with local version."
        Set-LoaderProgress 15 "Update timed out"
        exit 0
    }

    $stdout = $gitProc.StandardOutput.ReadToEnd()
    $stderr = $gitProc.StandardError.ReadToEnd()
    $exitCode = $gitProc.ExitCode

    if ($exitCode -eq 0) {
        if ($stdout -match "Already up to date") {
            Log-Message "Application is already up to date."
            Set-LoaderProgress 15 "App is up to date"
        }
        else {
            Log-Message "Updated application successfully."
            if (-not [string]::IsNullOrWhiteSpace($stdout)) {
                Log-Message $stdout.Trim()
            }
            Set-LoaderProgress 15 "Updated successfully"
            Start-Sleep -Milliseconds 250
        }
    }
    else {
        Log-Message "Fast-forward update skipped or failed (code $exitCode). Continuing with local version."
        if (-not [string]::IsNullOrWhiteSpace($stderr)) {
            Log-Message $stderr.Trim()
        }
        Set-LoaderProgress 15 "Starting environment..."
    }
}
catch {
    Log-Message "Unexpected error during update check: $_"
    Set-LoaderProgress 15 "Starting environment..."
}

exit 0

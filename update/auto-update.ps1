<#
.SYNOPSIS
    T-Insider Git Auto-Update Script with Comprehensive Developer Safety Net.

.DESCRIPTION
    Checks for updates from the configured remote branch during the splash launcher sequence.
    Safely skips or aborts if:
      - Git is not installed or available on the system
      - Current directory is not a Git repository
      - Current HEAD is detached
      - Active branch does not match the configured target branch (developer working on another branch)
      - Working directory has uncommitted modifications, staged files, or untracked changes
      - Git operation is currently in progress (.git/index.lock, rebase, merge, cherry-pick)
      - Local branch has unpushed commits ahead of origin (diverged ancestry)
    Executes non-interactive fetch with timeout, and pulls strictly with --ff-only to ensure zero conflicts.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$ProjectDirectory = "",

    [Parameter(Mandatory = $false)]
    [string]$TargetBranch = "main",

    [Parameter(Mandatory = $false)]
    [string]$ProgressFile = "",

    [Parameter(Mandatory = $false)]
    [string]$ResultFile = "",

    [Parameter(Mandatory = $false)]
    [string]$LogFile = "",

    [Parameter(Mandatory = $false)]
    [int]$FetchTimeoutSeconds = 7
)

$ErrorActionPreference = "Continue"

# Resolve directories
if ([string]::IsNullOrWhiteSpace($ProjectDirectory)) {
    $ScriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
    $ProjectDirectory = Split-Path -Parent $ScriptDir
}

if ([string]::IsNullOrWhiteSpace($LogFile)) {
    $LogsDir = Join-Path $ProjectDirectory "logs"
    if (-not (Test-Path -LiteralPath $LogsDir)) {
        New-Item -ItemType Directory -Path $LogsDir -Force | Out-Null
    }
    $LogFile = Join-Path $LogsDir "update.log"
}

function Write-UpdateLog([string]$Level, [string]$Message) {
    $Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $Entry = "[$Timestamp] [$Level] $Message"
    try {
        Add-Content -LiteralPath $LogFile -Value $Entry -ErrorAction SilentlyContinue
    }
    catch {
    }
}

function Set-SplashProgress([int]$Percent, [string]$Message) {
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

function Find-GitExecutable {
    # 1. Check PATH
    $cmd = Get-Command "git" -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) {
        return $cmd.Source
    }

    # 2. Check standard Windows installations
    $candidates = @(
        "C:\Program Files\Git\cmd\git.exe",
        "C:\Program Files\Git\bin\git.exe",
        "C:\Program Files (x86)\Git\cmd\git.exe",
        "C:\Program Files (x86)\Git\bin\git.exe",
        (Join-Path $env:LOCALAPPDATA "Programs\Git\cmd\git.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Git\bin\git.exe")
    )

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return $candidate
        }
    }

    return $null
}

function Invoke-GitCommand([string]$GitPath, [string[]]$Arguments, [int]$TimeoutSeconds = 0) {
    $pinfo = New-Object System.Diagnostics.ProcessStartInfo
    $pinfo.FileName = $GitPath
    $pinfo.WorkingDirectory = $ProjectDirectory
    $pinfo.RedirectStandardOutput = $true
    $pinfo.RedirectStandardError = $true
    $pinfo.UseShellExecute = $false
    $pinfo.CreateNoWindow = $true

    # Prevent interactive credential prompts or terminal freezes in hidden processes
    $pinfo.EnvironmentVariables["GIT_TERMINAL_PROMPT"] = "0"
    $pinfo.EnvironmentVariables["GIT_ASKPASS"] = "echo"

    # Format arguments compatible with both Windows PowerShell 5.1 (.NET Framework) and modern PowerShell
    $escapedArgs = @()
    foreach ($arg in $Arguments) {
        if ($arg -match '[\s"]') {
            $escapedArgs += '"{0}"' -f ($arg -replace '"', '\"')
        }
        else {
            $escapedArgs += $arg
        }
    }
    $pinfo.Arguments = $escapedArgs -join ' '

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $pinfo

    try {
        $started = $process.Start()
        if (-not $started) {
            return @{ ExitCode = -1; StdOut = ""; StdErr = "Failed to start git process." }
        }

        if ($TimeoutSeconds -gt 0) {
            $exited = $process.WaitForExit($TimeoutSeconds * 1000)
            if (-not $exited) {
                try { $process.Kill() } catch {}
                return @{ ExitCode = -2; StdOut = ""; StdErr = "Git command timed out after $TimeoutSeconds seconds." }
            }
        }
        else {
            $process.WaitForExit()
        }

        $stdout = $process.StandardOutput.ReadToEnd().Trim()
        $stderr = $process.StandardError.ReadToEnd().Trim()
        return @{ ExitCode = $process.ExitCode; StdOut = $stdout; StdErr = $stderr }
    }
    catch {
        return @{ ExitCode = -1; StdOut = ""; StdErr = $_.Exception.Message }
    }
    finally {
        $process.Dispose()
    }
}

function Start-AutoUpdateWorkflow {
    Write-UpdateLog "INFO" "Starting auto-update verification (target branch: $TargetBranch)..."
    Set-SplashProgress 5 "Checking for updates..."

    # Step 1: Check Git executable
    $GitPath = Find-GitExecutable
    if (-not $GitPath) {
        Write-UpdateLog "INFO" "Git is not installed or available on this system. Auto-update skipped."
        return [PSCustomObject]@{
            Status = "SkippedGitMissing"
            Message = "Git is not installed"
            Updated = $false
            Error = $null
        }
    }
    Write-UpdateLog "DEBUG" "Using Git binary at: $GitPath"

    # Step 2: Check if directory is inside a Git repository
    $repoCheck = Invoke-GitCommand -GitPath $GitPath -Arguments @("rev-parse", "--is-inside-work-tree")
    if ($repoCheck.ExitCode -ne 0 -or $repoCheck.StdOut -ne "true") {
        Write-UpdateLog "INFO" "Directory is not a Git repository. Auto-update skipped."
        return [PSCustomObject]@{
            Status = "SkippedNotGitRepo"
            Message = "Not a git repository"
            Updated = $false
            Error = $null
        }
    }

    # Step 3: Check for Git lock files or in-progress operations
    $gitDirRes = Invoke-GitCommand -GitPath $GitPath -Arguments @("rev-parse", "--git-dir")
    $gitDir = if ($gitDirRes.ExitCode -eq 0 -and $gitDirRes.StdOut) {
        if ([System.IO.Path]::IsPathRooted($gitDirRes.StdOut)) { $gitDirRes.StdOut } else { Join-Path $ProjectDirectory $gitDirRes.StdOut }
    } else {
        Join-Path $ProjectDirectory ".git"
    }

    if (Test-Path -LiteralPath (Join-Path $gitDir "index.lock")) {
        Write-UpdateLog "WARN" "Git index.lock detected. Another Git process may be active. Skipping auto-update."
        Set-SplashProgress 10 "Git index is locked; update skipped"
        Start-Sleep -Milliseconds 1200
        return [PSCustomObject]@{
            Status = "SkippedLocked"
            Message = "Git index.lock is present"
            Updated = $false
            Error = $null
        }
    }

    if ((Test-Path -LiteralPath (Join-Path $gitDir "MERGE_HEAD")) -or 
        (Test-Path -LiteralPath (Join-Path $gitDir "rebase-merge")) -or 
        (Test-Path -LiteralPath (Join-Path $gitDir "rebase-apply")) -or
        (Test-Path -LiteralPath (Join-Path $gitDir "CHERRY_PICK_HEAD"))) {
        Write-UpdateLog "WARN" "Git operation in progress (merge/rebase/cherry-pick). Skipping auto-update."
        Set-SplashProgress 10 "Git operation in progress; update skipped"
        Start-Sleep -Milliseconds 1500
        return [PSCustomObject]@{
            Status = "SkippedOperationInProgress"
            Message = "Git merge or rebase in progress"
            Updated = $false
            Error = $null
        }
    }

    # Step 4: Safety Net - Verify active branch
    $branchCheck = Invoke-GitCommand -GitPath $GitPath -Arguments @("branch", "--show-current")
    $currentBranch = $branchCheck.StdOut.Trim()

    if ([string]::IsNullOrWhiteSpace($currentBranch)) {
        # Check if detached HEAD
        Write-UpdateLog "WARN" "Repository is in a detached HEAD state. Skipping auto-update to protect state."
        Set-SplashProgress 10 "Detached HEAD; auto-update skipped"
        Start-Sleep -Milliseconds 1200
        return [PSCustomObject]@{
            Status = "SkippedDetachedHead"
            Message = "Detached HEAD state"
            Updated = $false
            Error = $null
        }
    }

    if ($currentBranch -ne $TargetBranch) {
        Write-UpdateLog "INFO" "Active branch '$currentBranch' does not match target '$TargetBranch'. Auto-update skipped to protect developer branch."
        Set-SplashProgress 10 "Branch '$currentBranch': auto-update skipped"
        Start-Sleep -Milliseconds 1500
        return [PSCustomObject]@{
            Status = "SkippedBranchMismatch"
            CurrentBranch = $currentBranch
            TargetBranch = $TargetBranch
            Message = "Branch mismatch"
            Updated = $false
            Error = $null
        }
    }

    # Step 5: Safety Net - Check for uncommitted local changes (dirty working tree or staged changes)
    $statusCheck = Invoke-GitCommand -GitPath $GitPath -Arguments @("status", "--porcelain")
    if ($statusCheck.ExitCode -eq 0 -and (-not [string]::IsNullOrWhiteSpace($statusCheck.StdOut))) {
        $rawStatus = $statusCheck.StdOut.Trim()
        Write-UpdateLog "WARN" "Local uncommitted or untracked changes detected on '$currentBranch'. Skipping auto-update to prevent overwriting your work."
        Write-UpdateLog "DEBUG" "Modified / untracked files:`n$rawStatus"
        Set-SplashProgress 10 "Local changes detected; update skipped"
        Start-Sleep -Milliseconds 1500
        return [PSCustomObject]@{
            Status = "SkippedDirtyWorkingTree"
            Message = "Uncommitted or untracked changes present"
            Updated = $false
            Error = "Untracked or uncommitted changes detected in working directory:`n`n$rawStatus`n`nPlease commit, stash, or gitignore them."
        }
    }

    # Step 6: Verify remote 'origin'
    $remoteCheck = Invoke-GitCommand -GitPath $GitPath -Arguments @("remote", "get-url", "origin")
    if ($remoteCheck.ExitCode -ne 0) {
        Write-UpdateLog "WARN" "Remote 'origin' is not configured. Auto-update skipped."
        return [PSCustomObject]@{
            Status = "SkippedNoRemote"
            Message = "No origin remote configured"
            Updated = $false
            Error = $null
        }
    }

    # Step 7: Fetch origin with strict timeout
    Set-SplashProgress 15 "Checking for latest updates..."
    Write-UpdateLog "INFO" "Fetching origin $TargetBranch (timeout: ${FetchTimeoutSeconds}s)..."
    $fetchResult = Invoke-GitCommand -GitPath $GitPath -Arguments @("fetch", "origin", $TargetBranch) -TimeoutSeconds $FetchTimeoutSeconds

    if ($fetchResult.ExitCode -ne 0) {
        $errMsg = if ($fetchResult.StdErr) { $fetchResult.StdErr } else { "Failed to contact remote repository or network timed out." }
        Write-UpdateLog "ERROR" "Fetch failed: $errMsg"
        return [PSCustomObject]@{
            Status = "ErrorFetch"
            Message = "Failed to check for updates from origin"
            Updated = $false
            Error = $errMsg
        }
    }

    # Step 8: Check commit ancestry and alignment
    $localHeadRes = Invoke-GitCommand -GitPath $GitPath -Arguments @("rev-parse", "HEAD")
    $remoteHeadRes = Invoke-GitCommand -GitPath $GitPath -Arguments @("rev-parse", "FETCH_HEAD")
    if ($remoteHeadRes.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($remoteHeadRes.StdOut)) {
        $remoteHeadRes = Invoke-GitCommand -GitPath $GitPath -Arguments @("rev-parse", "origin/$TargetBranch")
    }

    if ($localHeadRes.ExitCode -ne 0 -or $remoteHeadRes.ExitCode -ne 0) {
        Write-UpdateLog "WARN" "Could not resolve commit hashes for HEAD or origin/$TargetBranch."
        return [PSCustomObject]@{
            Status = "ErrorRevParse"
            Message = "Could not resolve branch commit references"
            Updated = $false
            Error = "$($localHeadRes.StdErr) $($remoteHeadRes.StdErr)".Trim()
        }
    }

    $localHead = $localHeadRes.StdOut.Trim()
    $remoteHead = $remoteHeadRes.StdOut.Trim()

    if ($localHead -eq $remoteHead) {
        Write-UpdateLog "INFO" "Local branch '$currentBranch' is already up to date with origin (commit $localHead)."
        Set-SplashProgress 20 "Application is up to date"
        Start-Sleep -Milliseconds 400
        return [PSCustomObject]@{
            Status = "UpToDate"
            Message = "Up to date"
            Updated = $false
            Error = $null
        }
    }

    # Check if local is an ancestor of remote (strictly behind, clean fast-forward)
    $ancestorCheck = Invoke-GitCommand -GitPath $GitPath -Arguments @("merge-base", "--is-ancestor", "HEAD", $remoteHead)
    if ($ancestorCheck.ExitCode -ne 0) {
        # Local HEAD is NOT an ancestor of remote -> Local has unpushed commits or branches diverged
        Write-UpdateLog "WARN" "Local branch '$currentBranch' has unpushed commits or has diverged from origin. Skipping auto-update to prevent merge conflicts."
        Set-SplashProgress 10 "Local branch has unpushed commits; update skipped"
        Start-Sleep -Milliseconds 1500
        return [PSCustomObject]@{
            Status = "SkippedDiverged"
            Message = "Local commits ahead of origin"
            Updated = $false
            Error = "Local branch has unpushed commits or has diverged from origin. Auto-update skipped to protect your work."
        }
    }

    # Step 9: Execute Safe Fast-Forward Pull
    Set-SplashProgress 20 "Downloading latest updates..."
    Write-UpdateLog "INFO" "Pulling latest updates with --ff-only from origin/$TargetBranch..."
    $pullResult = Invoke-GitCommand -GitPath $GitPath -Arguments @("pull", "--ff-only", "origin", $TargetBranch) -TimeoutSeconds 20

    if ($pullResult.ExitCode -ne 0) {
        $pullErr = if ($pullResult.StdErr) { $pullResult.StdErr } else { "Git pull --ff-only failed." }
        Write-UpdateLog "ERROR" "Pull failed: $pullErr"
        return [PSCustomObject]@{
            Status = "ErrorPull"
            Message = "Failed to apply updates from origin"
            Updated = $false
            Error = $pullErr
        }
    }

    Write-UpdateLog "INFO" "Update applied successfully: $($pullResult.StdOut)"
    Set-SplashProgress 30 "Updated successfully"
    Start-Sleep -Milliseconds 500

    return [PSCustomObject]@{
        Status = "Updated"
        Message = "Update applied successfully"
        Updated = $true
        Error = $null
    }
}

# Execute workflow
$workflowResult = Start-AutoUpdateWorkflow

if (-not [string]::IsNullOrWhiteSpace($ResultFile)) {
    try {
        $json = $workflowResult | ConvertTo-Json -Compress
        [System.IO.File]::WriteAllText($ResultFile, $json, [System.Text.Encoding]::UTF8)
    }
    catch {
        Write-UpdateLog "WARN" "Failed to write ResultFile: $($_.Exception.Message)"
    }
}

return $workflowResult


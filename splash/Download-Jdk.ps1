param(
    [Parameter(Mandatory = $true)]
    [string]$DestinationRoot
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

$ProgressFile = $env:TINSIDER_LOADER_PROGRESS_FILE
$ArchivePath = Join-Path $DestinationRoot "jdk.zip"
$Client = $null
$Response = $null
$SourceStream = $null
$FileStream = $null

function Set-LoaderProgress([int]$Percent, [string]$Message) {
    if (-not [string]::IsNullOrWhiteSpace($ProgressFile)) {
        [System.IO.File]::WriteAllText(
            $ProgressFile,
            "$Percent|$Message",
            [System.Text.Encoding]::ASCII
        )
    }
}

try {
    $Architecture = if ($env:PROCESSOR_ARCHITECTURE -eq "ARM64") { "aarch64" } else { "x64" }
    $Url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/$Architecture/jdk/hotspot/normal/eclipse"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    [System.IO.Directory]::CreateDirectory($DestinationRoot) | Out-Null
    Set-LoaderProgress 0 "Downloading JDK (0%)"
    Write-Host "[INFO] Fetching JDK from Adoptium..."

    $DownloadComplete = $false
    $LastDownloadError = "Unknown download error"
    $MaximumAttempts = 3
    for ($Attempt = 1; $Attempt -le $MaximumAttempts; $Attempt++) {
        try {
            $Handler = New-Object System.Net.Http.HttpClientHandler
            $Handler.AllowAutoRedirect = $true
            $Client = [System.Net.Http.HttpClient]::new($Handler)
            $Response = $Client.GetAsync(
                $Url,
                [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
            ).GetAwaiter().GetResult()
            $null = $Response.EnsureSuccessStatusCode()

            $TotalBytes = $Response.Content.Headers.ContentLength
            $SourceStream = $Response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
            $FileStream = [System.IO.File]::Open(
                $ArchivePath,
                [System.IO.FileMode]::Create,
                [System.IO.FileAccess]::Write,
                [System.IO.FileShare]::None
            )
            $Buffer = New-Object byte[] 81920
            $DownloadedBytes = [int64]0
            $LastReportedPercent = -1

            while (($BytesRead = $SourceStream.Read($Buffer, 0, $Buffer.Length)) -gt 0) {
                $FileStream.Write($Buffer, 0, $BytesRead)
                $DownloadedBytes += $BytesRead

                if ($TotalBytes -gt 0) {
                    $Percent = [Math]::Min(70, [Math]::Floor(($DownloadedBytes * 70.0) / $TotalBytes))
                    if ($Percent -ne $LastReportedPercent) {
                        $DownloadPercent = [Math]::Min(100, [Math]::Floor(($DownloadedBytes * 100.0) / $TotalBytes))
                        Set-LoaderProgress $Percent "Downloading JDK ($DownloadPercent%)"
                        $LastReportedPercent = $Percent
                    }
                }
            }

            if ($TotalBytes -gt 0 -and $DownloadedBytes -ne $TotalBytes) {
                throw "Incomplete JDK archive: received $DownloadedBytes of $TotalBytes bytes."
            }
            $DownloadComplete = $true
        }
        catch {
            $LastDownloadError = $_.Exception.Message
        }
        finally {
            if ($FileStream) { $FileStream.Dispose(); $FileStream = $null }
            if ($SourceStream) { $SourceStream.Dispose(); $SourceStream = $null }
            if ($Response) { $Response.Dispose(); $Response = $null }
            if ($Client) { $Client.Dispose(); $Client = $null }
        }

        if ($DownloadComplete) {
            break
        }

        Remove-Item -LiteralPath $ArchivePath -Force -ErrorAction SilentlyContinue
        if ($Attempt -lt $MaximumAttempts) {
            Set-LoaderProgress 0 "Download interrupted; retrying ($Attempt/$MaximumAttempts)"
            Write-Host "[WARN] JDK download attempt $Attempt failed: $LastDownloadError"
            Start-Sleep -Seconds $Attempt
        }
    }

    if (-not $DownloadComplete) {
        throw "JDK download failed after $MaximumAttempts attempts: $LastDownloadError"
    }

    Set-LoaderProgress 72 "Extracting JDK"
    Write-Host "[INFO] Extracting files..."
    Remove-Item (Join-Path $DestinationRoot "jdk-21") -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -LiteralPath $ArchivePath -DestinationPath $DestinationRoot -Force
    Remove-Item -LiteralPath $ArchivePath -Force

    $ExtractedDirectory = Get-ChildItem -LiteralPath $DestinationRoot -Directory |
        Where-Object { $_.Name -ne "jdk-21" } |
        Select-Object -First 1
    if (-not $ExtractedDirectory) {
        throw "Could not find the extracted JDK directory."
    }

    Rename-Item -LiteralPath $ExtractedDirectory.FullName -NewName "jdk-21"
    Set-LoaderProgress 83 "Preparing application"
}
finally {
    if ($FileStream) { $FileStream.Dispose() }
    if ($SourceStream) { $SourceStream.Dispose() }
    if ($Response) { $Response.Dispose() }
    if ($Client) { $Client.Dispose() }
}
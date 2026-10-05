Add-Type -AssemblyName PresentationFramework
Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName WindowsBase

$MutexName = "Local\TInsiderSingleInstance"
$script:SingleInstanceMutex = [System.Threading.Mutex]::new($false, $MutexName)
try {
    $script:OwnsSingleInstanceMutex = $script:SingleInstanceMutex.WaitOne(0)
}
catch [System.Threading.AbandonedMutexException] {
    $script:OwnsSingleInstanceMutex = $true
}

if (-not $script:OwnsSingleInstanceMutex) {
    [System.Windows.MessageBox]::Show(
        "T-Insider is already running.",
        "T-Insider",
        [System.Windows.MessageBoxButton]::OK,
        [System.Windows.MessageBoxImage]::Information
    ) | Out-Null
    $script:SingleInstanceMutex.Dispose()
    exit 0
}

$ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectDirectory = Split-Path -Parent $ScriptDirectory
$RunnerPath = Join-Path $ProjectDirectory "tinsider.cmd"
$LogoPath = Join-Path $ScriptDirectory "splash-icon.png"
$VersionPath = Join-Path $ScriptDirectory "version.txt"
$Version = if (Test-Path $VersionPath) { (Get-Content -LiteralPath $VersionPath -Raw).Trim() } else { "" }
$ProgressFile = Join-Path ([System.IO.Path]::GetTempPath()) ("tinsider-loader-{0}.progress" -f [guid]::NewGuid())
$ReadyFile = "$ProgressFile.ready"
$script:CurrentProgress = 0
$script:Process = $null

[System.IO.File]::WriteAllText($ProgressFile, "0|Preparing to start", [System.Text.Encoding]::ASCII)
$env:TINSIDER_LOADER_PROGRESS_FILE = $ProgressFile

$Window = New-Object System.Windows.Window
$Window.Width = 520
$Window.Height = 350
$Window.WindowStyle = "None"
$Window.ResizeMode = "NoResize"
$Window.WindowStartupLocation = "CenterScreen"
$Window.ShowInTaskbar = $false
$Window.Topmost = $true
$Window.Background = [System.Windows.Media.Brushes]::White
$Window.AllowsTransparency = $true

$Border = New-Object System.Windows.Controls.Border
$Border.Background = [System.Windows.Media.Brushes]::White
$Border.CornerRadius = New-Object System.Windows.CornerRadius(16)
$Border.Padding = New-Object System.Windows.Thickness(24)

$Shadow = New-Object System.Windows.Media.Effects.DropShadowEffect
$Shadow.BlurRadius = 24
$Shadow.ShadowDepth = 4
$Shadow.Opacity = 0.18
$Border.Effect = $Shadow

$Grid = New-Object System.Windows.Controls.Grid
$LogoRow = New-Object System.Windows.Controls.RowDefinition
$LogoRow.Height = New-Object System.Windows.GridLength(1, [System.Windows.GridUnitType]::Star)
$VersionRow = New-Object System.Windows.Controls.RowDefinition
$VersionRow.Height = [System.Windows.GridLength]::Auto
$TextRow = New-Object System.Windows.Controls.RowDefinition
$TextRow.Height = [System.Windows.GridLength]::Auto
$ProgressRow = New-Object System.Windows.Controls.RowDefinition
$ProgressRow.Height = [System.Windows.GridLength]::Auto
$Grid.RowDefinitions.Add($LogoRow)
$Grid.RowDefinitions.Add($VersionRow)
$Grid.RowDefinitions.Add($TextRow)
$Grid.RowDefinitions.Add($ProgressRow)

if (Test-Path $LogoPath) {
    $Image = New-Object System.Windows.Controls.Image
    $Image.Source = New-Object System.Windows.Media.Imaging.BitmapImage([Uri]::new($LogoPath))
    $Image.Width = 280
    $Image.Height = 190
    $Image.Stretch = "Uniform"
    $Image.HorizontalAlignment = "Center"
    $Image.VerticalAlignment = "Center"
    [System.Windows.Controls.Grid]::SetRow($Image, 0)
    $Grid.Children.Add($Image) | Out-Null
}

$VersionText = New-Object System.Windows.Controls.TextBlock
$VersionText.Text = $Version
$VersionText.FontFamily = New-Object System.Windows.Media.FontFamily("Segoe UI")
$VersionText.FontSize = 12
$VersionText.Foreground = New-Object System.Windows.Media.SolidColorBrush(
    [System.Windows.Media.Color]::FromRgb(15, 54, 125)
)
$VersionText.HorizontalAlignment = "Center"
$VersionText.Margin = New-Object System.Windows.Thickness(0, 0, 0, 7)
[System.Windows.Controls.Grid]::SetRow($VersionText, 1)
$Grid.Children.Add($VersionText) | Out-Null

$LoadingText = New-Object System.Windows.Controls.TextBlock
$LoadingText.Text = "Preparing to start"
$LoadingText.FontFamily = New-Object System.Windows.Media.FontFamily("Segoe UI")
$LoadingText.FontSize = 15
$LoadingText.FontWeight = "SemiBold"
$LoadingText.Foreground = New-Object System.Windows.Media.SolidColorBrush(
    [System.Windows.Media.Color]::FromRgb(15, 54, 125)
)
$LoadingText.HorizontalAlignment = "Center"
$LoadingText.Margin = New-Object System.Windows.Thickness(0, 0, 0, 12)
[System.Windows.Controls.Grid]::SetRow($LoadingText, 2)
$Grid.Children.Add($LoadingText) | Out-Null

$ProgressStack = New-Object System.Windows.Controls.StackPanel
$ProgressStack.HorizontalAlignment = "Center"
$ProgressBackground = New-Object System.Windows.Controls.Border
$ProgressBackground.Width = 300
$ProgressBackground.Height = 6
$ProgressBackground.Background = New-Object System.Windows.Media.SolidColorBrush(
    [System.Windows.Media.Color]::FromRgb(235, 238, 245)
)
$ProgressBackground.CornerRadius = New-Object System.Windows.CornerRadius(3)

$Progress = New-Object System.Windows.Controls.Border
$Progress.Width = 0
$Progress.Height = 6
$Progress.Background = New-Object System.Windows.Media.SolidColorBrush(
    [System.Windows.Media.Color]::FromRgb(255, 174, 0)
)
$Progress.CornerRadius = New-Object System.Windows.CornerRadius(3)
$Progress.HorizontalAlignment = "Left"
$ProgressBackground.Child = $Progress
$ProgressStack.Children.Add($ProgressBackground) | Out-Null

$PercentageText = New-Object System.Windows.Controls.TextBlock
$PercentageText.Text = "0%"
$PercentageText.FontFamily = New-Object System.Windows.Media.FontFamily("Segoe UI")
$PercentageText.FontSize = 12
$PercentageText.Foreground = New-Object System.Windows.Media.SolidColorBrush(
    [System.Windows.Media.Color]::FromRgb(90, 100, 115)
)
$PercentageText.HorizontalAlignment = "Center"
$PercentageText.Margin = New-Object System.Windows.Thickness(0, 7, 0, 0)
$ProgressStack.Children.Add($PercentageText) | Out-Null
[System.Windows.Controls.Grid]::SetRow($ProgressStack, 3)
$Grid.Children.Add($ProgressStack) | Out-Null

$Border.Child = $Grid
$Window.Content = $Border

$Timer = New-Object System.Windows.Threading.DispatcherTimer
$Timer.Interval = [TimeSpan]::FromMilliseconds(50)
$Timer.Add_Tick({
    try {
        $ProgressData = [System.IO.File]::ReadAllText($ProgressFile)
        $ProgressParts = $ProgressData -split '\|', 2
        if ($ProgressParts.Count -eq 2) {
            $NextProgress = [Math]::Max(0, [Math]::Min(100, [int]$ProgressParts[0]))
            $script:CurrentProgress = $NextProgress
            $LoadingText.Text = $ProgressParts[1]
            $PercentageText.Text = "$script:CurrentProgress%"
            $Progress.Width = 300 * $script:CurrentProgress / 100
        }
    }
    catch {
    }

    if (Test-Path $ReadyFile) {
        $Timer.Stop()
        $Window.Close()
    }
    elseif ($script:Process -and $script:Process.HasExited) {
        $Timer.Stop()
        $LogPath = Join-Path $ProjectDirectory "logs\start.log"
        $LogTail = ""
        if (Test-Path $LogPath) {
            $LogTail = (Get-Content -LiteralPath $LogPath -Tail 12) -join "`n"
        }
        $ErrorMessage = "T-Insider could not start. See logs\start.log for details."
        if (-not [string]::IsNullOrWhiteSpace($LogTail)) {
            $ErrorMessage += "`n`n$LogTail"
        }
        [System.Windows.MessageBox]::Show(
            $ErrorMessage,
            "T-Insider",
            [System.Windows.MessageBoxButton]::OK,
            [System.Windows.MessageBoxImage]::Error
        ) | Out-Null
        $Window.Close()
    }
})

$Window.Add_ContentRendered({
    $Timer.Start()
    try {
        $Arguments = '/d /c ""{0}" -preloader"' -f $RunnerPath
        $script:Process = Start-Process -FilePath $env:ComSpec -ArgumentList $Arguments -WorkingDirectory $ProjectDirectory -WindowStyle Hidden -PassThru
    }
    catch {
        $Timer.Stop()
        [System.Windows.MessageBox]::Show(
            "Could not start T-Insider.`n`n$($_.Exception.Message)",
            "T-Insider",
            [System.Windows.MessageBoxButton]::OK,
            [System.Windows.MessageBoxImage]::Error
        ) | Out-Null
        $Window.Close()
    }
})

$Window.ShowDialog() | Out-Null
if ($script:Process -and -not $script:Process.HasExited) {
    $script:Process.WaitForExit()
}
Remove-Item $ProgressFile, $ReadyFile -Force -ErrorAction SilentlyContinue
$script:SingleInstanceMutex.ReleaseMutex()
$script:SingleInstanceMutex.Dispose()
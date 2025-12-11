# Release script for Jellyfin Jellyseerr TV (APK)
# Builds the release APK, copies it to dist/, calculates checksum,
# and optionally creates a GitHub release. Artifacts stay in dist/ (ignored by git).
#
# Usage examples:
#   .\release.ps1 -Version 1.2.3
#   .\release.ps1 -Version 1.2.3 -Changelog "Fix playback crash"
#   .\release.ps1 -Version 1.2.3 -Release

param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$Changelog = "Update",
    [switch]$Release  # Create GitHub release
)

$ErrorActionPreference = "Stop"

Write-Host "=== Jellyfin Jellyseerr TV Release ===" -ForegroundColor Cyan
Write-Host "Version: $Version"
Write-Host "Changelog: $Changelog"

# Paths
$Root       = $PSScriptRoot
$OutputDir  = Join-Path $Root "dist"
$ReleaseDir = Join-Path $OutputDir "v$Version"
$ApkName    = "jellyarc-tv-v$Version.apk"
$GradleW    = Join-Path $Root "gradlew.bat"

# Ensure dist is clean for this version
if (Test-Path $ReleaseDir) { Remove-Item $ReleaseDir -Recurse -Force }
New-Item -ItemType Directory -Path $ReleaseDir -Force | Out-Null

# Step 1: Build release APK
Write-Host "`n[1/4] Building release APK..." -ForegroundColor Yellow
$env:JELLYFIN_VERSION = $Version
$gradleArgs = @("assembleRelease", "-Pjellyfin.version=$Version")
& $GradleW @gradleArgs
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

# Step 2: Locate APK
Write-Host "`n[2/4] Locating APK..." -ForegroundColor Yellow
$apk = Get-ChildItem -Path (Join-Path $Root "app/build/outputs/apk") -Recurse -Filter "*release*.apk" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apk) { throw "Release APK not found. Check build output path." }
Write-Host "Found APK: $($apk.FullName)" -ForegroundColor Green

# Step 3: Copy to dist with versioned name
Write-Host "`n[3/4] Copying APK to dist..." -ForegroundColor Yellow
$destApk = Join-Path $ReleaseDir $ApkName
Copy-Item $apk.FullName $destApk -Force

# Step 4: Checksum
Write-Host "`n[4/4] Calculating SHA256..." -ForegroundColor Yellow
$hash = (Get-FileHash $destApk -Algorithm SHA256).Hash.ToLower()
Write-Host "Checksum: $hash" -ForegroundColor Green

# Optional GitHub release
if ($Release) {
    Write-Host "`n[GitHub] Creating/uploading release..." -ForegroundColor Yellow

    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $gh) {
        $candidate = Join-Path ${env:ProgramFiles} "GitHub CLI\gh.exe"
        if (Test-Path $candidate) { $gh = $candidate }
    }
    if (-not $gh) { throw "gh CLI not found. Install with: winget install GitHub.cli" }

    $repo = "Serekay/jellyfin-jellyserr-tv"
    $tag = "v$Version"

    # Check if release exists
    $ErrorActionPreference = "SilentlyContinue"
    $null = & $gh release view $tag --repo $repo 2>$null
    $exists = $LASTEXITCODE -eq 0
    $ErrorActionPreference = "Stop"

    if ($exists) {
        Write-Host "Release $tag exists, uploading asset..." -ForegroundColor Yellow
        & $gh release upload $tag $destApk --repo $repo --clobber
        if ($LASTEXITCODE -ne 0) { throw "GitHub upload failed" }
    } else {
        Write-Host "Creating new release $tag..." -ForegroundColor Yellow
        & $gh release create $tag $destApk `
            --repo $repo `
            --title $tag `
            --target "master" `
            --notes "## Changes`n- $Changelog" `
            --latest
        if ($LASTEXITCODE -ne 0) { throw "GitHub release create failed" }
    }
    Write-Host "GitHub release ready: https://github.com/$repo/releases/tag/$tag" -ForegroundColor Green
} else {
    Write-Host "`nSkip GitHub release (use -Release to create it)" -ForegroundColor Gray
}

Write-Host "`n=== Done ===" -ForegroundColor Green
Write-Host "APK: $destApk"
Write-Host "SHA256: $hash"
Write-Host "dist/ is git-ignored; artifacts won't be committed."

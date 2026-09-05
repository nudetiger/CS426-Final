# Assembles the graded submission archive: 24125006_24125009.zip
#
# Layout required by docs/final_requirements.md section 3:
#   24125006_24125009/
#   |-- README.md
#   |-- src/            full source, including .git, excluding build output
#   |-- apk/app-release.apk
#   |-- report/report.pdf
#   \-- video/demo-link.txt
#
# Run from the repository root:  powershell -ExecutionPolicy Bypass -File tools\package-submission.ps1

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$ids = '24125006_24125009'
$dist = Join-Path $root 'dist'
$stage = Join-Path $dist $ids
$src = Join-Path $stage 'src'

if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Path $src, (Join-Path $stage 'apk'), (Join-Path $stage 'report'), (Join-Path $stage 'video') -Force | Out-Null

# --- source tree -----------------------------------------------------------
# Excluded: build output, IDE state, node_modules, and every secret. The
# DeepSeek key (backend\.env), the signing keystore and local machine paths
# must never travel inside the archive.
$exclude = @(
    '.gradle', 'build', '.idea', '.cxx', 'node_modules', 'dist',
    'keystore', '.git\modules'
)
$excludeFiles = @(
    '.env', 'local.properties', 'keystore.properties',
    '*.jks', '*.keystore', '*.apk', '*.aab', '*.iml', '*.log',
    # LaTeX intermediates are build output too; report.pdf itself is kept.
    '*.aux', '*.toc', '*.out', '*.fls', '*.fdb_latexmk', '*.synctex.gz'
)

robocopy $root $src /MIR /NFL /NDL /NJH /NJS /NP `
    /XD $($exclude | ForEach-Object { Join-Path $root $_ }) `
        $(Join-Path $root 'app\build') $(Join-Path $root 'app\.cxx') `
        $(Join-Path $root 'backend\node_modules') `
    /XF $excludeFiles | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy failed with exit code $LASTEXITCODE" }

# robocopy /MIR keeps directory shells; prune anything excluded that survived.
Get-ChildItem $src -Recurse -Directory -Force |
    Where-Object { $_.Name -in @('build', '.gradle', 'node_modules', '.idea', '.cxx', 'dist', 'keystore') } |
    Sort-Object FullName -Descending |
    ForEach-Object { Remove-Item $_.FullName -Recurse -Force }

# --- secret sweep ----------------------------------------------------------
# Fail loudly rather than shipping a live API key.
$leaks = Get-ChildItem $src -Recurse -File -Force |
    Where-Object { $_.Name -in @('.env', 'keystore.properties', 'local.properties') -or $_.Extension -in @('.jks', '.keystore') }
if ($leaks) {
    $leaks | ForEach-Object { Write-Host "LEAK: $($_.FullName)" }
    throw 'Secret files reached the staging directory; aborting.'
}
$keyHits = Get-ChildItem $src -Recurse -File -Force -Include *.js, *.kt, *.java, *.md, *.xml, *.properties, *.json |
    Select-String -Pattern 'sk-[0-9a-f]{32}' -List
if ($keyHits) {
    $keyHits | ForEach-Object { Write-Host "LEAK: $($_.Path)" }
    throw 'A DeepSeek-shaped API key is present in the staged source; aborting.'
}

# --- git log fallback ------------------------------------------------------
# The requirements accept either the .git folder or an exported log.
if (-not (Test-Path (Join-Path $src '.git'))) {
    git -C $root log --stat > (Join-Path $src 'git-log.txt')
}

# --- artefacts -------------------------------------------------------------
$apk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "Missing $apk -- run .\gradlew.bat assembleRelease first." }
Copy-Item $apk (Join-Path $stage 'apk\app-release.apk')

$pdf = Join-Path $root 'report\report.pdf'
if (-not (Test-Path $pdf)) { throw "Missing $pdf -- build the report first." }
Copy-Item $pdf (Join-Path $stage 'report\report.pdf')

Copy-Item (Join-Path $root 'README.md') (Join-Path $stage 'README.md')

$video = Join-Path $root 'video\demo-link.txt'
if (-not (Test-Path $video)) {
    throw "Missing $video -- the demo link is a required deliverable."
}
# A link that cannot be opened counts as no video at all, so refuse to build an
# archive that still carries the placeholder instead of warning about it.
$videoText = Get-Content $video -Raw
if ($videoText -match 'TODO' -or $videoText -notmatch 'https?://') {
    throw "video\demo-link.txt has no video URL in it yet. Paste the unlisted YouTube / Google Drive link (shared so anyone with the link can view) and run this again."
}
Copy-Item $video (Join-Path $stage 'video\demo-link.txt')

# --- zip -------------------------------------------------------------------
$zip = Join-Path $dist "$ids.zip"
Compress-Archive -Path $stage -DestinationPath $zip -Force

$size = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "Built $zip ($size MB)"
Write-Host 'Contents:'
Expand-Archive -Path $zip -DestinationPath (Join-Path $dist 'verify') -Force
Get-ChildItem (Join-Path $dist "verify\$ids") | Select-Object Mode, Name | Format-Table | Out-String | Write-Host
Remove-Item (Join-Path $dist 'verify') -Recurse -Force

[CmdletBinding()]
param([Parameter(Mandatory)][string]$BackupPath)

$ErrorActionPreference = "Stop"
$backup = [System.IO.Path]::GetFullPath($BackupPath)
$manifestPath = Join-Path $backup "manifest.json"
$databasePath = Join-Path $backup "postgres.dump"
if (!(Test-Path -LiteralPath $manifestPath) -or !(Test-Path -LiteralPath $databasePath)) {
    throw "Backup is missing manifest.json or postgres.dump"
}

$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
foreach ($entry in $manifest) {
    $file = Join-Path $backup $entry.path
    if (!(Test-Path -LiteralPath $file)) { throw "Missing backup file: $($entry.path)" }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash.ToLowerInvariant()
    if ($actual -ne $entry.sha256) { throw "Checksum mismatch: $($entry.path)" }
}

docker run --rm -v "${backup}:/backup:ro" postgres:16-alpine pg_restore --list /backup/postgres.dump | Out-Null
if ($LASTEXITCODE -ne 0) { throw "PostgreSQL archive validation failed" }
Write-Host "Backup checksums and PostgreSQL archive are valid: $backup"

[CmdletBinding()]
param(
    [string]$BackupRoot = (Join-Path $PSScriptRoot "..\.backups"),
    [ValidateRange(1, 3650)][int]$RetentionDays = 30
)

$ErrorActionPreference = "Stop"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$root = [System.IO.Path]::GetFullPath($BackupRoot)
$destination = Join-Path $root $timestamp
New-Item -ItemType Directory -Force -Path $destination | Out-Null

$containerDump = "/tmp/grace-$timestamp.dump"
docker compose exec -T postgres pg_dump --username grace --dbname grace --format custom --file $containerDump
if ($LASTEXITCODE -ne 0) { throw "PostgreSQL backup failed" }
docker cp "grace-postgres:$containerDump" (Join-Path $destination "postgres.dump")
if ($LASTEXITCODE -ne 0) { throw "Could not copy PostgreSQL backup" }
docker compose exec -T postgres rm -f $containerDump

docker cp "grace-minio:/data/." (Join-Path $destination "minio")
if ($LASTEXITCODE -ne 0) { throw "MinIO backup failed" }

$manifest = Get-ChildItem -Path $destination -Recurse -File | ForEach-Object {
    [pscustomobject]@{
        path = [System.IO.Path]::GetRelativePath($destination, $_.FullName)
        bytes = $_.Length
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant()
    }
}
$manifest | ConvertTo-Json -Depth 3 | Set-Content -Encoding utf8 (Join-Path $destination "manifest.json")

$cutoff = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem -Path $root -Directory | Where-Object {
    $_.FullName.StartsWith($root, [StringComparison]::OrdinalIgnoreCase) -and $_.LastWriteTime -lt $cutoff
} | Remove-Item -Recurse -Force

Write-Host "Backup created and checksummed: $destination"

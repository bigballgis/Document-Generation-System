<#
.SYNOPSIS
Delete all templates for the current tenant, then import one composite template ZIP.

.DESCRIPTION
Use this when you want a clean slate and a single "golden" composite template from disk.

Typical flow (recommended — export gold ZIP before deleting anything):
  pwsh -File .\reset-all-templates-and-import.ps1 -ExportGoldFromTemplateId 31 -Force

Or import an existing ZIP:
  pwsh -File .\reset-all-templates-and-import.ps1 -ZipPath "D:\path\to\composite-template.zip" -Force

Environment:
  DOCGEN_BACKEND_URL (default http://localhost:8080)
  DOCGEN_USERNAME    (default sunsun)
  DOCGEN_PASSWORD    (required)

Parameters:
  -ZipPath                    Path to composite ZIP for import (optional if -ExportGoldFromTemplateId is used)
  -ExportGoldFromTemplateId  If set, exports this template to ..\out\international-bank-fol-final.zip first
  -Force                      Skip interactive confirmation (required for automation)

WARNING: This deletes every template returned by GET /api/templates for your account/tenant.
#>

param(
  [string]$ZipPath = '',
  [int]$ExportGoldFromTemplateId = 0,
  [switch]$Force
)

$ErrorActionPreference = 'Stop'

$backendBaseUrl = $env:DOCGEN_BACKEND_URL
if (-not $backendBaseUrl) { $backendBaseUrl = 'http://localhost:8080' }
$username = $env:DOCGEN_USERNAME
if (-not $username) { $username = 'sunsun' }
$password = $env:DOCGEN_PASSWORD
if (-not $password) { throw 'DOCGEN_PASSWORD is not set.' }

if (-not $Force) {
  $confirm = Read-Host 'This deletes ALL templates for your tenant. Type DELETE-ALL to continue'
  if ($confirm -ne 'DELETE-ALL') { throw 'Aborted.' }
}

Invoke-RestMethod -Method GET -Uri ($backendBaseUrl + '/actuator/health/ping') | Out-Null

$login = Invoke-RestMethod -Method POST -Uri ($backendBaseUrl + '/api/auth/login') -ContentType 'application/json' `
  -Body (@{ username = $username; password = $password } | ConvertTo-Json)
$token = $login.accessToken
if (-not $token) { throw 'Login failed.' }

$headers = @{ Authorization = 'Bearer ' + $token }
$outDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\out'))
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$defaultGoldZip = Join-Path $outDir 'international-bank-fol-final.zip'

if ($ExportGoldFromTemplateId -gt 0) {
  $exportUrl = $backendBaseUrl + '/api/composite-templates/' + $ExportGoldFromTemplateId + '/export'
  Write-Host "Exporting template $ExportGoldFromTemplateId to $defaultGoldZip ..."
  $resp = Invoke-WebRequest -Method GET -Uri $exportUrl -Headers $headers -UseBasicParsing
  [IO.File]::WriteAllBytes($defaultGoldZip, $resp.Content)
  $ZipPath = $defaultGoldZip
}

if (-not $ZipPath) {
  if (Test-Path $defaultGoldZip) {
    $ZipPath = $defaultGoldZip
  } else {
    $candidates = @(Get-ChildItem -Path $outDir -Filter 'international-bank-fol_template_*.zip' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)
    if ($candidates.Count -ge 1) {
      $ZipPath = $candidates[0].FullName
      Write-Host "Using newest export ZIP: $ZipPath"
    }
  }
}

if (-not $ZipPath -or -not (Test-Path $ZipPath)) {
  throw 'No import ZIP found. Use -ExportGoldFromTemplateId <id> before wipe, or pass -ZipPath to a composite export .zip file.'
}

Write-Host 'Listing all templates...'
$allIds = [System.Collections.Generic.List[int64]]::new()
$page = 0
$pageSize = 100
while ($true) {
  $url = $backendBaseUrl + '/api/templates?page=' + $page + '&size=' + $pageSize + '&sort=id,desc'
  $pageResult = Invoke-RestMethod -Method GET -Uri $url -Headers $headers
  $content = @($pageResult.content)
  if ($content.Count -eq 0) { break }
  foreach ($t in $content) {
    if ($null -ne $t.id) { $allIds.Add([int64]$t.id) | Out-Null }
  }
  if ($pageResult.last -eq $true) { break }
  $page++
}

Write-Host ('Deleting ' + $allIds.Count + ' template(s)...')
foreach ($id in $allIds) {
  try {
    Invoke-RestMethod -Method DELETE -Uri ($backendBaseUrl + '/api/templates/' + $id) -Headers $headers | Out-Null
    Write-Host ('  deleted id=' + $id)
  } catch {
    Write-Warning ('Failed to delete id=' + $id + ': ' + $_.Exception.Message)
    throw
  }
}

Write-Host ('Importing ZIP: ' + $ZipPath)
$import = Invoke-RestMethod -Method POST -Uri ($backendBaseUrl + '/api/composite-templates/import') -Headers $headers `
  -Form @{ file = Get-Item -LiteralPath $ZipPath }

$newId = $import.id
if (-not $newId) { throw 'Import response missing template id.' }

$handoff = Join-Path $outDir 'last-template-id.txt'
[IO.File]::WriteAllText($handoff, "$newId", [System.Text.UTF8Encoding]::new($false))
Write-Host ('Done. New template id=' + $newId + ' (written to ' + $handoff + ')')

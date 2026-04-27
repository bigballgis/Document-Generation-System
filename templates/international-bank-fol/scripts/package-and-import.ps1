<#
.SYNOPSIS
Create a system-compatible Composite Template ZIP and import it back.

.DESCRIPTION
This script:
1) Runs the local bootstrap to create the demo composite template (blank segments + parameters + tests).
2) Downloads the system ZIP via GET /api/composite-templates/{id}/export
3) Imports the ZIP via POST /api/composite-templates/import (multipart/form-data)

Why this approach?
- The backend has strict expectations for ZIP structure (config.json + segments/*.docx + headers/*.docx + footers/*.docx + parameters.json + test-data.json).
- Exporting from the system guarantees the ZIP is importable.

Requirements:
- Local stack running (backend on http://localhost:8080)
- Environment variables for non-interactive login:
  - DOCGEN_USERNAME (default: sunsun)
  - DOCGEN_PASSWORD (required)
  - DOCGEN_BACKEND_URL (default: http://localhost:8080)
#>

$ErrorActionPreference = "Stop"

$backendBaseUrl = $env:DOCGEN_BACKEND_URL
if (-not $backendBaseUrl) { $backendBaseUrl = "http://localhost:8080" }

$username = $env:DOCGEN_USERNAME
if (-not $username) { $username = "sunsun" }

$password = $env:DOCGEN_PASSWORD
if (-not $password) {
  throw "DOCGEN_PASSWORD is not set. Set it for this PowerShell session then rerun."
}

function Invoke-Json {
  param(
    [Parameter(Mandatory=$true)][string]$Method,
    [Parameter(Mandatory=$true)][string]$Url,
    [Parameter(Mandatory=$false)][object]$Body = $null,
    [Parameter(Mandatory=$false)][string]$Token = $null
  )

  $headers = @{ "Content-Type" = "application/json" }
  if ($Token) { $headers["Authorization"] = "Bearer $Token" }

  if ($Body -ne $null) {
    $json = $Body | ConvertTo-Json -Depth 100
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -Body $json
  }
  return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers
}

# Health
Invoke-RestMethod -Method "GET" -Uri "$backendBaseUrl/actuator/health/ping" | Out-Null

# Login for token (also used for import)
$login = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/auth/login" -Body @{ username=$username; password=$password }
$token = $login.accessToken
if (-not $token) { throw "Login failed: accessToken missing in response." }

Write-Host "Login OK. Bootstrapping demo template..."

$templateId = $null

# Prefer file-based handoff (host output streams are not reliably capturable from nested shells).
$handoffPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\\out\\last-template-id.txt"))
if (Test-Path $handoffPath) {
  $text = [IO.File]::ReadAllText((Resolve-Path -Path $handoffPath)).Trim()
  if ($text -match "^(\\d+)$") {
    $templateId = [int64]$Matches[1]
  }
}

if (-not $templateId) {
  # Run bootstrap (must succeed and write out/last-template-id.txt)
  $bootstrapPath = Join-Path $PSScriptRoot "demo-bootstrap.ps1"
  $runner = "powershell"
  try {
    $pwsh = Get-Command pwsh -ErrorAction Stop
    if ($pwsh -and $pwsh.Source) {
      $runner = "pwsh"
    }
  } catch {
    # keep default powershell
  }

  $bootstrapOut = & $runner -NoProfile -ExecutionPolicy Bypass -File $bootstrapPath 2>&1
  $bootstrapText = ($bootstrapOut | Out-String)
  Write-Host $bootstrapText

  if (Test-Path $handoffPath) {
    $text = [IO.File]::ReadAllText((Resolve-Path -Path $handoffPath)).Trim()
    if ($text -match "^(\\d+)$") {
      $templateId = [int64]$Matches[1]
    }
  }
}

if (-not $templateId) {
  throw "Bootstrap did not yield a TemplateId. Cannot continue."
}
Write-Host "Bootstrap created templateId=$templateId"

# Export system ZIP
$exportUrl = "$backendBaseUrl/api/composite-templates/$templateId/export"
$zipPath = Join-Path $PSScriptRoot ("..\\out\\international-bank-fol_template_" + $templateId + ".zip")
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $zipPath) | Out-Null

Write-Host "Downloading system export ZIP..."
$zipBytes = Invoke-WebRequest -Method "GET" -Uri $exportUrl -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing
[IO.File]::WriteAllBytes($zipPath, $zipBytes.Content)
Write-Host "Saved ZIP: $zipPath"

# Import ZIP back into the system
Write-Host "Importing ZIP back into the system..."
$form = @{
  file = Get-Item $zipPath
}
$import = Invoke-RestMethod -Method "POST" -Uri "$backendBaseUrl/api/composite-templates/import" -Headers @{ Authorization = "Bearer $token" } -Form $form

Write-Host ("Imported template id={0}, name='{1}'" -f $import.id, $import.name)
Write-Host ("Export ZIP: {0}" -f $zipPath)


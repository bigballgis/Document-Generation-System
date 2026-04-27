<#
.SYNOPSIS
Bootstraps the "International Bank FOL — Full Demo" composite template for local showcase.

.DESCRIPTION
This script:
- Logs in to the backend and obtains a JWT access token.
- Creates a COMPOSITE template.
- Creates blank segment DOCX files (and optional blank headers/footers) in MinIO.
- Applies the reference assembly config (positions, conditions, data scopes).
- Imports the parameter table from parameters.json (including DERIVED and validation rules).
- Imports demo test cases and runs them.

Prerequisites:
- Local docker compose stack is running (backend on http://localhost:8080).
- You have a SUPER_ADMIN or TENANT_ADMIN user. (For local demo: sunsun was promoted to SUPER_ADMIN.)

Notes:
- This is a local showcase helper. Do not use in production environments.
#>

$ErrorActionPreference = "Stop"

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

function Load-JsonFile {
  param([Parameter(Mandatory=$true)][string]$Path)
  if (-not (Test-Path $Path)) { throw "File not found: $Path" }
  return Get-Content -Raw -Path $Path | ConvertFrom-Json -Depth 100
}

function Resolve-TestDataTemplate {
  param(
    [Parameter(Mandatory=$true)][object]$TestDataTemplate,
    [Parameter(Mandatory=$true)][object]$SampleData
  )

  # Allows demo-test-cases.json to reference sample-data.json via {"__use":".../sample-data.json", ...overrides }
  if ($TestDataTemplate.PSObject.Properties.Name -contains "__use") {
    $copy = $SampleData | ConvertTo-Json -Depth 100 | ConvertFrom-Json -Depth 100
    foreach ($p in $TestDataTemplate.PSObject.Properties) {
      if ($p.Name -eq "__use") { continue }
      $copy | Add-Member -NotePropertyName $p.Name -NotePropertyValue $p.Value -Force
    }
    return $copy
  }
  return $TestDataTemplate
}

$root = Split-Path -Parent $PSScriptRoot
$dir = $PSScriptRoot

$backendBaseUrl = $env:DOCGEN_BACKEND_URL
if (-not $backendBaseUrl) { $backendBaseUrl = "http://localhost:8080" }

$username = $env:DOCGEN_USERNAME
if (-not $username) { $username = "sunsun" }

$password = $env:DOCGEN_PASSWORD
if (-not $password) {
  $secure = Read-Host "Password for '$username'" -AsSecureString
  $password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  )
}

Write-Host "Backend: $backendBaseUrl"
Write-Host "User:    $username"

# 0) Health preflight (fail fast)
try {
  $ping = Invoke-RestMethod -Method "GET" -Uri "$backendBaseUrl/actuator/health/ping"
  Write-Host "Backend health OK."
} catch {
  throw "Backend is not reachable/healthy at $backendBaseUrl. Ensure docker compose is up and the health endpoint is enabled."
}

# 1) Login
$login = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/auth/login" -Body @{ username=$username; password=$password }
$token = $login.accessToken
if (-not $token) { throw "Login failed: accessToken missing in response." }
Write-Host "Login OK."

# 2) Create composite template
$templateName = "International Bank Commercial Loan — Facility Offer Letter (FOL) — Full Demo"
$create = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/composite-templates" -Token $token -Body @{
  name = $templateName
  description = "Enterprise demo composite template: parameters + derived + assembly + OnlyOffice + tests"
  outputFormat = "WORD"
}

$templateId = $create.id
if (-not $templateId) { throw "Template create failed: id missing." }
Write-Host "Created composite template: id=$templateId"

# 3) Create blank headers/footers (re-used across segments)
$header = (Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/composite-templates/$templateId/create-blank-header-footer" -Token $token -Body @{ type="header" }).filePath
$footer = (Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/composite-templates/$templateId/create-blank-header-footer" -Token $token -Body @{ type="footer" }).filePath
Write-Host "Blank header/footer created."

# 4) Create blank segments according to reference assembly-config.json
$assemblyRef = Load-JsonFile -Path (Join-Path $dir "assembly-config.json")
$segments = @()

foreach ($seg in $assemblyRef.segments) {
  $created = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/composite-templates/$templateId/create-blank-segment" -Token $token -Body @{
    name = $seg.name
    segmentType = $seg.segmentType
  }

  $entry = [ordered]@{
    name = $seg.name
    filePath = $created.filePath
    segmentType = $seg.segmentType
    position = $seg.position
    enabled = $true
    pageBreakBefore = [bool]$seg.pageBreakBefore
    conditionExpression = $seg.conditionExpression
    dataScope = $seg.dataScope
    headerFilePath = $seg.headerFilePath
    footerFilePath = $seg.footerFilePath
    pageNumberFormat = $seg.pageNumberFormat
    pageNumberStart = $seg.pageNumberStart
  }

  # Use the newly-created blank header/footer unless this segment explicitly sets null.
  if (-not $entry.headerFilePath) { $entry.headerFilePath = $header }
  if (-not $entry.footerFilePath) { $entry.footerFilePath = $footer }

  $segments += $entry
}

# Sort by position (defensive)
$segments = $segments | Sort-Object -Property position

# 5) Apply assembly config
Invoke-Json -Method "PUT" -Url "$backendBaseUrl/api/composite-templates/$templateId/assembly-config" -Token $token -Body @{ segments = $segments } | Out-Null
Write-Host "Assembly config applied."

# 6) Import parameter table using parameters.json (create tree with validation + derived expressions)
$parametersSpec = Load-JsonFile -Path (Join-Path $dir "parameters.json")

function Create-ParamTree {
  param(
    [Parameter(Mandatory=$true)][long]$TemplateId,
    [Parameter(Mandatory=$true)][object[]]$Params,
    [Parameter(Mandatory=$false)][Nullable[long]]$ParentId = $null
  )

  $sort = 0
  foreach ($p in $Params) {
    $payload = @{
      name = $p.name
      parentId = $ParentId
      parameterType = $p.parameterType
      dataType = $p.dataType
      required = [bool]$p.required
      defaultValue = $p.defaultValue
      description = $p.description
      sortOrder = $sort
      expressionText = $p.expressionText
      expressionType = $p.expressionType
      validationRules = $p.validationRules
    }
    $created = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/templates/$TemplateId/parameters" -Token $token -Body $payload
    $sort++

    if ($p.children -and $p.children.Count -gt 0) {
      Create-ParamTree -TemplateId $TemplateId -Params $p.children -ParentId $created.id
    }
  }
}

Create-ParamTree -TemplateId $templateId -Params $parametersSpec.parameters -ParentId $null
Write-Host "Parameter table imported."

# 6.5) Parameter coverage scan (placeholder vs parameter table)
try {
  $scan = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/templates/$templateId/parameters/scan" -Token $token
  Write-Host "Parameter scan complete."
  if ($scan.unmatchedPlaceholders -and $scan.unmatchedPlaceholders.Count -gt 0) {
    Write-Host "WARN: Unmatched placeholders detected (authoring may be incomplete):"
    $scan.unmatchedPlaceholders | ForEach-Object { Write-Host ("- " + $_) }
  }
} catch {
  Write-Host "WARN: Parameter scan failed (likely because segments are still blank). Proceeding."
}

# 6.6) Export backup artifacts (config + zip) for demo recovery
try {
  $configBytes = Invoke-WebRequest -Method "GET" -Uri "$backendBaseUrl/api/composite-templates/$templateId/export-config" -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing
  $zipBytes = Invoke-WebRequest -Method "GET" -Uri "$backendBaseUrl/api/composite-templates/$templateId/export" -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing
  $outDir = Join-Path $dir ("out_" + $templateId)
  New-Item -ItemType Directory -Force -Path $outDir | Out-Null
  [IO.File]::WriteAllBytes((Join-Path $outDir "composite-config.json"), $configBytes.Content)
  [IO.File]::WriteAllBytes((Join-Path $outDir "composite-template.zip"), $zipBytes.Content)
  Write-Host "Exported composite backup to $outDir"
} catch {
  Write-Host "WARN: Export backup failed. Proceeding."
}

# 7) Import demo test cases (resolve __use against sample-data.json)
$sampleData = Load-JsonFile -Path (Join-Path $dir "sample-data.json")
$testCases = Load-JsonFile -Path (Join-Path $dir "demo-test-cases.json")

$importPayload = @()
foreach ($tc in $testCases) {
  $td = ($tc.testData | ConvertFrom-Json -Depth 100)
  $resolved = Resolve-TestDataTemplate -TestDataTemplate $td -SampleData $sampleData

  $importPayload += @{
    name = $tc.name
    testData = ($resolved | ConvertTo-Json -Depth 100)
    expectedResult = $tc.expectedResult
    compareMode = $tc.compareMode
  }
}

$importJson = $importPayload | ConvertTo-Json -Depth 100
Invoke-RestMethod -Method "POST" -Uri "$backendBaseUrl/api/templates/$templateId/test-cases/import" -Headers @{ "Content-Type"="application/json" } -Body $importJson | Out-Null
Write-Host "Test cases imported."

# 8) Run all tests
$report = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/templates/$templateId/test-cases/run-all" -Token $token
Write-Host ("Test report: total={0}, passed={1}, failed={2}" -f $report.totalCount, $report.passedCount, $report.failedCount)

# 9) Print next steps for document authoring
Write-Host ""
Write-Host "Next authoring step (OnlyOffice):"
Write-Host "- Open Admin/Template Workspace and locate the new composite template."
Write-Host "- For each segment, open the OnlyOffice editor and paste the corresponding content from template-content.md."
Write-Host "- Then preview/generate using render-request-example.json (watermark/barcode/qrcode)."
Write-Host ""
Write-Host "TemplateId=$templateId"


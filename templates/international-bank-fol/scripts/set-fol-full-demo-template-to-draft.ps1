<#
.SYNOPSIS
Moves the International Bank FOL Full Demo composite template to DRAFT using public API transitions.

.DESCRIPTION
Resolves the template by exact name, then:
- ACTIVE: POST /api/templates/{id}/create-draft-version (version snapshot + DRAFT)
- IN_TEST or ARCHIVED: POST /api/templates/{id}/return-design (state machine -> DRAFT)
- REVIEWED: POST activate then create-draft-version
- PENDING_REVIEW: PUT reject on the first PENDING template review (caller must be the assigned reviewer), then POST return-design
- DRAFT: no-op

Environment (same as demo-bootstrap.ps1):
- DOCGEN_BACKEND_URL (default http://localhost:8080)
- DOCGEN_USERNAME (default sunsun)
- DOCGEN_PASSWORD (prompt if unset)
#>

$ErrorActionPreference = "Stop"

function Invoke-Json {
  param(
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$Url,
    [Parameter(Mandatory = $false)][object]$Body = $null,
    [Parameter(Mandatory = $false)][string]$Token = $null
  )

  $headers = @{ "Content-Type" = "application/json" }
  if ($Token) { $headers["Authorization"] = "Bearer $Token" }

  if ($null -ne $Body) {
    $json = $Body | ConvertTo-Json -Depth 100
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -Body $json
  }
  return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers
}

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

$templateName = "International Bank Commercial Loan — Facility Offer Letter (FOL) — Full Demo"

try {
  Invoke-RestMethod -Method "GET" -Uri "$backendBaseUrl/actuator/health/ping" | Out-Null
} catch {
  throw "Backend not reachable at $backendBaseUrl. Start local stack first."
}

$login = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/auth/login" -Body @{ username = $username; password = $password }
$token = $login.accessToken
if (-not $token) { throw "Login failed: missing accessToken." }

function Find-TemplateByName {
  param([string]$Token)
  $page = 0
  $size = 100
  $kw = [uri]::EscapeDataString("International Bank")
  while ($true) {
    $url = "$backendBaseUrl/api/templates?keyword=$kw&page=$page&size=$size"
    $resp = Invoke-Json -Method "GET" -Url $url -Token $Token
    foreach ($t in $resp.content) {
      if ($t.name -eq $templateName) { return $t }
    }
    if ($resp.last -eq $true -or $resp.numberOfElements -eq 0) { return $null }
    $page++
  }
}

$t = Find-TemplateByName -Token $token
if (-not $t) {
  throw "No template with exact name: $templateName"
}

$id = $t.id
$status = $t.status
Write-Host "Found template id=$id status=$status"

if ($status -eq "DRAFT") {
  Write-Host "Already DRAFT. Nothing to do."
  exit 0
}

if ($status -eq "ACTIVE") {
  $out = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/templates/$id/create-draft-version" -Token $token
  Write-Host "OK -> DRAFT (create-draft-version). New status: $($out.status)"
  exit 0
}

if ($status -eq "IN_TEST" -or $status -eq "ARCHIVED") {
  $out = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/templates/$id/return-design" -Token $token
  Write-Host "OK -> DRAFT (return-design). New status: $($out.status)"
  exit 0
}

if ($status -eq "REVIEWED") {
  Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/templates/$id/activate" -Token $token | Out-Null
  $out = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/templates/$id/create-draft-version" -Token $token
  Write-Host "OK -> DRAFT (activate + create-draft-version). New status: $($out.status)"
  exit 0
}

if ($status -eq "PENDING_REVIEW") {
  $revUrl = "$backendBaseUrl/api/templates/$id/reviews?page=0&size=20"
  $revPage = Invoke-Json -Method "GET" -Url $revUrl -Token $token
  $pendingReview = $null
  foreach ($r in $revPage.content) {
    if ($r.status -eq "PENDING") { $pendingReview = $r; break }
  }
  if (-not $pendingReview) {
    throw "Template is PENDING_REVIEW but no PENDING review row was found. Resolve reviews in the UI or database."
  }
  $rejectBody = @{ comment = "Return to DRAFT for demo (automated script)" }
  Invoke-Json -Method "PUT" -Url "$backendBaseUrl/api/reviews/$($pendingReview.id)/reject" -Token $token -Body $rejectBody | Out-Null
  $out = Invoke-Json -Method "POST" -Url "$backendBaseUrl/api/templates/$id/return-design" -Token $token
  Write-Host "OK -> DRAFT (reject review $($pendingReview.id) + return-design). New status: $($out.status)"
  exit 0
}

throw "Unsupported status for automatic reset: $status"

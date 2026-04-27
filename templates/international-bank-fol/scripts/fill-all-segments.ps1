<#
.SYNOPSIS
Auto-fill all COMPOSITE template segments with tag content.

.DESCRIPTION
- Reads tag specs from: templates/international-bank-fol/docs/template-content.md
- Generates styled DOCX (styles, optional header/footer, box tables, optional Word TOC on segment 02)
- Uploads each segment DOCX via /api/composite-templates/{id}/upload-segment
- Updates assembly config to point to the uploaded filePath for every segment

Env:
- DOCGEN_BACKEND_URL (default http://localhost:8080)
- DOCGEN_USERNAME (default sunsun)
- DOCGEN_PASSWORD (required)
#>

$ErrorActionPreference = 'Stop'

$backendBaseUrl = $env:DOCGEN_BACKEND_URL
if (-not $backendBaseUrl) { $backendBaseUrl = 'http://localhost:8080' }
$username = $env:DOCGEN_USERNAME
if (-not $username) { $username = 'sunsun' }
$password = $env:DOCGEN_PASSWORD
if (-not $password) { throw 'DOCGEN_PASSWORD is not set.' }

function Invoke-Json {
  param([string]$Method, [string]$Url, $Body = $null, [string]$Token = $null)
  $headers = @{ 'Content-Type' = 'application/json' }
  if ($Token) { $headers['Authorization'] = 'Bearer ' + $Token }
  if ($null -ne $Body) {
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -Body ($Body | ConvertTo-Json -Depth 80)
  }
  return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers
}

function Get-Token {
  $resp = Invoke-RestMethod -Method 'POST' -Uri ($backendBaseUrl + '/api/auth/login') -ContentType 'application/json' `
    -Body (@{ username = $username; password = $password } | ConvertTo-Json)
  if (-not $resp.accessToken) { throw 'Login failed.' }
  return $resp.accessToken
}

function Read-SegmentCodeBlock {
  param([string]$MarkdownText, [string]$SegmentNumber)

  $lines = $MarkdownText -split "\r?\n"
  $headingPrefix = '## Segment ' + $SegmentNumber + ':'

  $headingIdx = -1
  for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i].TrimStart().StartsWith($headingPrefix)) {
      $headingIdx = $i
      break
    }
  }
  if ($headingIdx -lt 0) { return $null }

  $openIdx = -1
  for ($i = $headingIdx; $i -lt $lines.Length; $i++) {
    if ($lines[$i].Trim() -eq '```') { $openIdx = $i; break }
  }
  if ($openIdx -lt 0) { return $null }

  $closeIdx = -1
  for ($i = $openIdx + 1; $i -lt $lines.Length; $i++) {
    if ($lines[$i].Trim() -eq '```') { $closeIdx = $i; break }
  }
  if ($closeIdx -lt 0 -or $closeIdx -le $openIdx) { return $null }

  return (($lines[($openIdx + 1)..($closeIdx - 1)] -join "`n")).Trim()
}

function ConvertTo-XmlText([string]$s) {
  if ($null -eq $s) { return '' }
  $e = [System.Security.SecurityElement]::Escape($s)
  if ($null -eq $e) { $e = '' }
  $dq = [string][char]34
  $sq = [string][char]39
  return ($e -replace $dq, '&quot;' -replace $sq, '&apos;')
}

function New-StyledDocxBytesFromText([string]$Text, [string]$SegmentId) {
  Add-Type -AssemblyName 'System.IO.Compression'
  Add-Type -AssemblyName 'System.IO.Compression.FileSystem'

  $crlf = ([string][char]13) + ([string][char]10)
  $lf = [string][char]10
  $lines = $Text.Replace($crlf, $lf).Split($lf)

  function Para([string]$text, [string]$style = $null, [string]$jc = $null, [bool]$pageBreakBefore = $false, [bool]$rule = $false) {
    $pPr = @()
    if ($style) { $pPr += ('<w:pStyle w:val="' + $style + '"/>') }
    if ($jc) { $pPr += ('<w:jc w:val="' + $jc + '"/>') }
    if ($pageBreakBefore) { $pPr += '<w:pageBreakBefore/>' }
    if ($rule) {
      $pPr += '<w:pBdr><w:bottom w:val="single" w:sz="12" w:space="8" w:color="B0B0B0"/></w:pBdr>'
    }
    $pPrXml = ''
    if ($pPr.Count -gt 0) { $pPrXml = '<w:pPr>' + ($pPr -join '') + '</w:pPr>' }
    $t = ConvertTo-XmlText $text
    return '<w:p>' + $pPrXml + '<w:r><w:t xml:space="preserve">' + $t + '</w:t></w:r></w:p>'
  }

  function Build-TableXml([string[]]$tableLines) {
    # tableLines include the top/bottom border lines.
    $rows = @()
    foreach ($l in $tableLines) {
      $t = ($l ?? '').Trim()
      if ($t.StartsWith('│')) { $rows += $t }
    }
    if ($rows.Count -lt 1) { return $null }

    # Determine max columns by splitting on '│'
    $parsedRows = @()
    $maxCols = 0
    foreach ($r in $rows) {
      $cells = ($r -split '│') | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
      $parsedRows += ,$cells
      if ($cells.Count -gt $maxCols) { $maxCols = $cells.Count }
    }
    if ($maxCols -lt 1) { return $null }

    $grid = @()
    for ($i = 0; $i -lt $maxCols; $i++) { $grid += '<w:gridCol w:w="4320"/>' } # ~3 inches each; Word will auto-fit.

    $tblRows = @()
    foreach ($cells in $parsedRows) {
      $tcs = @()
      for ($c = 0; $c -lt $maxCols; $c++) {
        $val = ''
        if ($c -lt $cells.Count) { $val = [string]$cells[$c] }
        $valEsc = ConvertTo-XmlText $val
        $tcs += (
          '<w:tc>' +
            '<w:tcPr><w:tcW w:w="0" w:type="auto"/></w:tcPr>' +
            '<w:p><w:pPr><w:spacing w:after="0"/></w:pPr><w:r><w:t xml:space="preserve">' + $valEsc + '</w:t></w:r></w:p>' +
          '</w:tc>'
        )
      }
      $tblRows += ('<w:tr>' + ($tcs -join '') + '</w:tr>')
    }

    return (
      '<w:tbl>' +
        '<w:tblPr>' +
          '<w:tblW w:w="0" w:type="auto"/>' +
          '<w:tblBorders>' +
            '<w:top w:val="single" w:sz="8" w:space="0" w:color="B0B0B0"/>' +
            '<w:left w:val="single" w:sz="8" w:space="0" w:color="B0B0B0"/>' +
            '<w:bottom w:val="single" w:sz="8" w:space="0" w:color="B0B0B0"/>' +
            '<w:right w:val="single" w:sz="8" w:space="0" w:color="B0B0B0"/>' +
            '<w:insideH w:val="single" w:sz="6" w:space="0" w:color="D0D0D0"/>' +
            '<w:insideV w:val="single" w:sz="6" w:space="0" w:color="D0D0D0"/>' +
          '</w:tblBorders>' +
        '</w:tblPr>' +
        '<w:tblGrid>' + ($grid -join '') + '</w:tblGrid>' +
        ($tblRows -join '') +
      '</w:tbl>'
    )
  }

  $bodyXml = @()
  $i = 0
  while ($i -lt $lines.Length) {
    $ln = [string]$lines[$i]
    $trim = ($ln ?? '').Trim()

    # Detect box-drawing table block
    if ($trim.StartsWith('┌')) {
      $tblLines = @()
      $tblLines += $lines[$i]
      $j = $i + 1
      while ($j -lt $lines.Length) {
        $tblLines += $lines[$j]
        if (([string]$lines[$j]).Trim().StartsWith('└')) { break }
        $j++
      }
      $tblXml = Build-TableXml -tableLines $tblLines
      if ($tblXml) {
        $bodyXml += $tblXml
        $i = $j + 1
        continue
      }
      # fallback: treat as plain lines
    }

    # Horizontal rule line using box-drawing chars
    if ($trim -match '^[━]{8,}$') {
      $bodyXml += (Para '' $null 'center' $false $true)
      $i++
      continue
    }

    # Title-like lines
    if ($trim -eq 'FACILITY OFFER LETTER' -or $trim -eq 'TABLE OF CONTENTS') {
      $bodyXml += (Para $trim 'DocGenTitle' 'center')
      $i++
      continue
    }

    # Section headings inside segments
    if ($trim -match '^(PART|APPENDIX)\b' -or $trim -match '^[0-9]+\.\s' -or $trim -match '^SIGNATURE PAGE\b') {
      $bodyXml += (Para $trim 'DocGenHeading1')
      $i++
      continue
    }

    # Cover-page centered blocks (heuristic: large left padding or logo placeholder)
    $leadingSpaces = ($ln -replace '(^\s*).*','$1').Length
    if ($leadingSpaces -ge 18 -or $trim -match '^\{\%bank_logo\}$') {
      $bodyXml += (Para $ln $null 'center')
      $i++
      continue
    }

    # Default paragraph
    $bodyXml += (Para $ln)
    $i++
  }

  # Word TOC (segment 02): insert after "TABLE OF CONTENTS" title; keeps static list below for demo/backup
  if ($SegmentId -eq '02') {
    $nl = [Environment]::NewLine
    $notePara =
      '<w:p><w:pPr><w:spacing w:before="0" w:after="80"/></w:pPr>' +
      '<w:r><w:rPr><w:sz w:val="18"/><w:color w:val="666666"/></w:rPr>' +
      '<w:t xml:space="preserve">Auto ToC field (in Word, right-click and Update Field; after a full one-file merge it lists outlined headings. This segment may show a short list until merge).</w:t></w:r></w:p>'
    $tocFieldPara =
      '<w:p><w:pPr><w:spacing w:after="160"/></w:pPr>' +
      '<w:r><w:fldChar w:fldCharType="begin"/></w:r>' +
      '<w:r><w:instrText xml:space="preserve"> TOC \o "1-3" \h \z \u </w:instrText></w:r>' +
      '<w:r><w:fldChar w:fldCharType="separate"/></w:r>' +
      '<w:r><w:t>Update field</w:t></w:r>' +
      '<w:r><w:fldChar w:fldCharType="end"/></w:r></w:p>'
    $ins = $false
    $merged = [System.Collections.Generic.List[string]]::new()
    foreach ($b in $bodyXml) {
      $merged.Add($b) | Out-Null
      if (-not $ins -and $b -match 'TABLE OF CONTENTS' -and $b -match 'DocGenTitle') {
        $merged.Add($notePara) | Out-Null
        $merged.Add($tocFieldPara) | Out-Null
        $ins = $true
      }
    }
    if (-not $ins) { $bodyXml = @($notePara, $tocFieldPara) + $bodyXml } else { $bodyXml = $merged }
  }

  $nl = [Environment]::NewLine
  $includeHeaderFooter = ($SegmentId -ne '01')

  $rootNs = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'
  if ($includeHeaderFooter) { $rootNs += ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"' }
  $sectPrInner =
    '      <w:pgSz w:w="11906" w:h="16838"/>' + $nl + # A4
    '      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>' + $nl
  if ($includeHeaderFooter) {
    $sectPrInner =
    '      <w:headerReference w:type="default" r:id="rId2"/>' + $nl +
    '      <w:footerReference w:type="default" r:id="rId3"/>' + $nl + $sectPrInner
  }

  $documentXml =
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' + $nl +
    '<w:document ' + $rootNs + '>' + $nl +
    '  <w:body>' + $nl +
    ($bodyXml -join $nl) + $nl +
    '    <w:sectPr>' + $nl + $sectPrInner +
    '    </w:sectPr>' + $nl +
    '  </w:body>' + $nl +
    '</w:document>'

  $contentTypes =
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' + $nl +
    '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' + $nl +
    '  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' + $nl +
    '  <Default Extension="xml" ContentType="application/xml"/>' + $nl +
    '  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>' + $nl +
    '  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>' + $nl
  if ($includeHeaderFooter) {
    $contentTypes +=
      '  <Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>' + $nl +
      '  <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>' + $nl
  }
  $contentTypes += '</Types>'

  $rels =
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' + $nl +
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' + $nl +
    '  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>' + $nl +
    '</Relationships>'

  $docRels =
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' + $nl +
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' + $nl +
    '  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>' + $nl
  if ($includeHeaderFooter) {
    $docRels +=
    '  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>' + $nl +
    '  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>' + $nl
  }
  $docRels += '</Relationships>'

  # Normal + Title + Heading1; Heading1 has outline level 0 for Word/merged-document TOC
  $styles =
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' + $nl +
    '<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">' + $nl +
    '  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">' + $nl +
    '    <w:name w:val="Normal"/>' + $nl +
    '    <w:rPr><w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/><w:sz w:val="22"/></w:rPr>' + $nl +
    '    <w:pPr><w:spacing w:line="276" w:lineRule="auto" w:after="120"/></w:pPr>' + $nl +
    '  </w:style>' + $nl +
    '  <w:style w:type="paragraph" w:styleId="DocGenTitle">' + $nl +
    '    <w:name w:val="DocGenTitle"/>' + $nl +
    '    <w:basedOn w:val="Normal"/>' + $nl +
    '    <w:pPr><w:jc w:val="center"/><w:spacing w:before="240" w:after="240"/></w:pPr>' + $nl +
    '    <w:rPr><w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/><w:b/><w:sz w:val="36"/></w:rPr>' + $nl +
    '  </w:style>' + $nl +
    '  <w:style w:type="paragraph" w:styleId="DocGenHeading1">' + $nl +
    '    <w:name w:val="DocGenHeading1"/>' + $nl +
    '    <w:basedOn w:val="Normal"/>' + $nl +
    '    <w:pPr><w:outlineLvl w:val="0"/><w:keepNext/><w:spacing w:before="240" w:after="120"/></w:pPr>' + $nl +
    '    <w:rPr><w:b/><w:sz w:val="26"/></w:rPr>' + $nl +
    '  </w:style>' + $nl +
    '</w:styles>'

  $header =
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' + $nl +
    '<w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">' + $nl +
    '  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t xml:space="preserve">{bank.legal_name | upper}</w:t></w:r></w:p>' + $nl +
    '  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t xml:space="preserve">FACILITY OFFER LETTER</w:t></w:r></w:p>' + $nl +
    '  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:color w:val="C0C0C0"/><w:sz w:val="16"/></w:rPr><w:t xml:space="preserve">CONFIDENTIAL</w:t></w:r></w:p>' + $nl +
    '</w:hdr>'

  $footer =
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' + $nl +
    '<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">' + $nl +
    '  <w:p>' +
    '    <w:pPr><w:jc w:val="center"/></w:pPr>' +
    '    <w:r><w:t xml:space="preserve">{confidentiality_level | upper}  -  Page </w:t></w:r>' +
    '    <w:fldSimple w:instr="PAGE \\* MERGEFORMAT"><w:r><w:t>1</w:t></w:r></w:fldSimple>' +
    '  </w:p>' + $nl +
    '</w:ftr>'

  $ms = New-Object System.IO.MemoryStream
  $zip = New-Object System.IO.Compression.ZipArchive($ms, [System.IO.Compression.ZipArchiveMode]::Create, $true)

  $entries = [System.Collections.Generic.List[object]]::new()
  $entries.Add(@{ Path = '[Content_Types].xml'; Content = $contentTypes }) | Out-Null
  $entries.Add(@{ Path = '_rels/.rels'; Content = $rels }) | Out-Null
  $entries.Add(@{ Path = 'word/document.xml'; Content = $documentXml }) | Out-Null
  $entries.Add(@{ Path = 'word/styles.xml'; Content = $styles }) | Out-Null
  $entries.Add(@{ Path = 'word/_rels/document.xml.rels'; Content = $docRels }) | Out-Null
  if ($includeHeaderFooter) {
    $entries.Add(@{ Path = 'word/header1.xml'; Content = $header }) | Out-Null
    $entries.Add(@{ Path = 'word/footer1.xml'; Content = $footer }) | Out-Null
  }

  foreach ($pair in $entries) {
    $entry = $zip.CreateEntry($pair.Path)
    $sw = New-Object System.IO.StreamWriter($entry.Open())
    $sw.Write($pair.Content)
    $sw.Dispose()
  }

  $zip.Dispose()
  $bytes = $ms.ToArray()
  $ms.Dispose()
  return $bytes
}

$templateIdStr = Read-Host 'Target composite templateId to fill (e.g. 31)'
if (-not ($templateIdStr -match '^(\d+)$')) { throw 'Invalid templateId' }
$templateId = [int64]$Matches[1]

$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tagSpecPath = Join-Path $root 'docs\template-content.md'
if (-not (Test-Path $tagSpecPath)) { throw ('Missing tag spec: ' + $tagSpecPath) }
$md = Get-Content -Raw -Path $tagSpecPath

$token = Get-Token
$cfg = Invoke-RestMethod -Method 'GET' -Uri ($backendBaseUrl + '/api/composite-templates/' + $templateId + '/assembly-config') `
  -Headers @{ Authorization = 'Bearer ' + $token }
if (-not $cfg.segments -or $cfg.segments.Count -lt 1) { throw 'Template has no segments' }

$map = @{
  'Cover Page' = '01'
  'Table of Contents' = '02'
  'Part A - Definitions & Interpretation' = '03'
  'Part B - Facility Details' = '04'
  'Part C - Interest & Fees' = '05'
  'Part D - Repayment Schedule' = '06'
  'Part E - Conditions Precedent' = '07'
  'Part F - Representations & Warranties' = '08'
  'Part G - Covenants' = '09'
  'Part H - Security & Collateral' = '10'
  'Part I - Guarantee' = '11'
  'Part J - Events of Default' = '12'
  'Part K - Governing Law & Jurisdiction' = '13'
  'Part L - Miscellaneous' = '14'
  'Appendix A - Compliance Certificate' = '15'
  'Appendix B - Drawdown Notice' = '16'
  'Appendix C - Fee Schedule' = '17'
  'Signature Page' = '18'
}

$updatedSegments = @()
foreach ($seg in ($cfg.segments | Sort-Object position)) {
  $name = [string]$seg.name
  $nn = $map[$name]
  if (-not $nn) { throw ('No mapping for segment name: ' + $name) }

  $block = Read-SegmentCodeBlock -MarkdownText $md -SegmentNumber $nn
  if (-not $block) { throw ('No code block found for Segment ' + $nn + ' (' + $name + ')') }

  $docxBytes = New-StyledDocxBytesFromText -Text $block -SegmentId $nn
  $tmp = Join-Path $env:TEMP ('fol_seg_' + $nn + '.docx')
  [IO.File]::WriteAllBytes($tmp, $docxBytes)

  $uploadUrl = $backendBaseUrl + '/api/composite-templates/' + $templateId + '/upload-segment?name=' +
    [uri]::EscapeDataString($name) + '&segmentType=BODY'

  $upload = Invoke-RestMethod -Method 'POST' -Uri $uploadUrl -Headers @{ Authorization = 'Bearer ' + $token } `
    -Form @{ file = Get-Item $tmp }

  $seg.filePath = $upload.filePath
  $updatedSegments += $seg
  Write-Host ('filled: ' + $name)
}

Invoke-Json -Method 'PUT' -Url ($backendBaseUrl + '/api/composite-templates/' + $templateId + '/assembly-config') `
  -Token $token -Body @{ segments = $updatedSegments } | Out-Null

Write-Host 'All segments filled and assembly config updated.'

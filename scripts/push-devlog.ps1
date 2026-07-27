param(
    [Parameter(Mandatory = $true)]
    [string]$SummaryFile,
    [string]$ServerUrl = "http://localhost:8080",
    [string]$Token = $env:PERSONAL_ASSISTANT_PAT
)

$ErrorActionPreference = "Stop"

if (-not $Token) {
    throw "缺少 PAT。请传入 -Token，或设置 PERSONAL_ASSISTANT_PAT 环境变量。"
}
if (-not $Token.StartsWith("pa_pat_")) {
    throw "PAT 格式不正确。"
}

$summaryPath = (Resolve-Path -LiteralPath $SummaryFile).Path
$summary = Get-Content -Raw -Encoding UTF8 -LiteralPath $summaryPath | ConvertFrom-Json
$requiredFields = @("title", "taskGoal", "coreChanges", "markdownContent")
foreach ($field in $requiredFields) {
    if (-not $summary.$field) {
        throw "总结文件缺少必填字段: $field"
    }
}

$repositoryRoot = (& git rev-parse --show-toplevel 2>$null)
if (-not $repositoryRoot) {
    throw "当前目录不在 Git 仓库中。"
}
$projectName = Split-Path -Leaf $repositoryRoot
$branchName = (& git branch --show-current 2>$null)
$commitHash = (& git rev-parse HEAD 2>$null)
$remoteUrl = (& git remote get-url origin 2>$null)
$occurredAt = if ($summary.occurredAt) { $summary.occurredAt } else { Get-Date -Format "yyyy-MM-ddTHH:mm:ss" }
$fingerprintSource = "$projectName|$branchName|$commitHash|$($summary.title)|$($summary.taskGoal)"
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $fingerprint = ([BitConverter]::ToString($sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($fingerprintSource)))).Replace("-", "").ToLowerInvariant()
} finally {
    $sha256.Dispose()
}

$payload = @{
    fingerprint = $fingerprint
    title = $summary.title
    projectName = $projectName
    repository = $remoteUrl
    branchName = $branchName
    commitHash = $commitHash
    taskGoal = $summary.taskGoal
    coreChanges = $summary.coreChanges
    technicalDecisions = $summary.technicalDecisions
    problemSolution = $summary.problemSolution
    verificationResult = $summary.verificationResult
    tags = if ($summary.tags -is [array]) { $summary.tags -join "," } else { $summary.tags }
    source = "CODEX"
    occurredAt = $occurredAt
    markdownContent = $summary.markdownContent
} | ConvertTo-Json -Depth 5

$headers = @{ Authorization = "Bearer $Token" }
$endpoint = "$($ServerUrl.TrimEnd('/'))/api/devlogs/ingest"
$response = Invoke-RestMethod -Method Post -Uri $endpoint -Headers $headers -ContentType "application/json; charset=utf-8" -Body $payload
Write-Host "开发记录推送成功，记录 ID: $($response.data)"

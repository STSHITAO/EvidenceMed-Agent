param(
    [string]$BaseUrl = "http://127.0.0.1:9100",
    [string]$Username = "medical-user",
    [Parameter(Mandatory = $true)][string]$Password
)

$health = Invoke-RestMethod -Uri "$BaseUrl/api/health" -Method Get
if ($health.status -ne "UP") { throw "Health check failed" }

$pair = "${Username}:${Password}"
$token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $token" }
$form = @{ question = "肺结节随访通常需要关注哪些信息？" }
$answer = Invoke-RestMethod -Uri "$BaseUrl/api/v1/consultations" -Method Post -Headers $headers -Form $form
if (-not $answer.traceId -or -not $answer.answer) { throw "Consultation smoke test failed" }

Write-Output "Smoke test passed. traceId=$($answer.traceId)"

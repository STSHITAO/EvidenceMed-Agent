param([int]$Port = 19100)

$ErrorActionPreference = "Stop"
$jar = Get-ChildItem "$PSScriptRoot\..\target\medical-agent-java-*.jar" |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $jar) { throw "请先执行 mvn package" }

$env:SERVER_PORT = "$Port"
$env:DB_URL = "jdbc:h2:mem:smoke;MODE=MySQL"
$env:MILVUS_ENABLED = "false"
$env:DEMO_USERS_ENABLED = "false"
$stdout = "$PSScriptRoot\..\target\smoke.out.log"
$stderr = "$PSScriptRoot\..\target\smoke.err.log"
$process = Start-Process java -ArgumentList "-jar", $jar -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr

try {
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        Start-Sleep -Milliseconds 500
        try {
            $response = Invoke-RestMethod "http://127.0.0.1:$Port/api/health" -TimeoutSec 2
            if ($response.status -eq "UP") {
                $response | ConvertTo-Json -Compress
                exit 0
            }
        } catch { }
    }
    Get-Content $stderr -Tail 80
    throw "本地健康检查未通过"
} finally {
    if (-not $process.HasExited) { Stop-Process -Id $process.Id }
}

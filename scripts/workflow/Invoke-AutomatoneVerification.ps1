[CmdletBinding()]
param(
    [string] $TaskId = 'BOOTSTRAP',
    [string] $Profile = 'bootstrap',
    [string] $ReportPath
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $PSScriptRoot 'VerificationWorkflow.psm1') -Force

if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $root ".agents/evidence/bootstrap/tooling/$TaskId-$Profile-report.json"
} elseif (-not [IO.Path]::IsPathRooted($ReportPath)) {
    $ReportPath = Join-Path $root $ReportPath
}

try {
    $result = Invoke-VerificationController -Root $root -TaskId $TaskId -Profile $Profile -ReportPath $ReportPath
    Write-Output "task=$TaskId profile=$Profile verdict=$($result.Report.verdict) exit_code=$($result.ExitCode)"
    Write-Output "report=$($result.ReportPath) schema_valid=$($result.Validation.Valid)"
    foreach ($sensor in @($result.Report.sensors)) {
        Write-Output "sensor=$($sensor.id) status=$($sensor.status) summary=$($sensor.summary)"
    }
    exit $result.ExitCode
} catch {
    [Console]::Error.WriteLine("ERROR: $($_.Exception.Message)")
    exit 3
}

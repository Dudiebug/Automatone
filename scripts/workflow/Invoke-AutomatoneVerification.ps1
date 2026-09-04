[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $TaskId,
    [string[]] $Profile = @('default'),
    [ValidateSet('Task', 'Milestone')] [string] $Scope = 'Task',
    [string[]] $GradleTasks = @(),
    [string[]] $TestFilter = @(),
    [string] $RiskReason,
    [switch] $FreshContext,
    [string] $ReportPath
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $PSScriptRoot 'VerificationWorkflow.psm1') -Force

if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $root ".agents/evidence/$TaskId/$Scope-report.json"
} elseif (-not [IO.Path]::IsPathRooted($ReportPath)) {
    $ReportPath = Join-Path $root $ReportPath
}

try {
    $result = Invoke-VerificationController -Root $root -TaskId $TaskId -Profile $Profile -ReportPath $ReportPath `
        -Scope $Scope -GradleTasks $GradleTasks -TestFilter $TestFilter -RiskReason $RiskReason -FreshContext:$FreshContext
    Write-Output "task=$TaskId scope=$Scope check_verdict=$($result.Report.verdict) exit_code=$($result.ExitCode)"
    Write-Output "report=$($result.ReportPath) schema_valid=$($result.Validation.Valid)"
    foreach ($sensor in @($result.Report.sensors)) {
        Write-Output "sensor=$($sensor.id) status=$($sensor.status) summary=$($sensor.summary)"
    }
    foreach ($check in @($result.Report.deferred_checks)) {
        Write-Output "milestone_check=$($check.id) status=PENDING"
    }
    exit $result.ExitCode
} catch {
    [Console]::Error.WriteLine("ERROR: $($_.Exception.Message)")
    exit 3
}

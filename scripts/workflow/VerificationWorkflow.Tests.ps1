Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot 'VerificationWorkflow.psm1') -Force

function Assert-Equal {
    param(
        [Parameter(Mandatory)] $Expected,
        [Parameter(Mandatory)] $Actual,
        [Parameter(Mandatory)] [string] $Message
    )

    if ($Expected -ne $Actual) {
        throw "$Message. Expected '$Expected', got '$Actual'."
    }
}

function Assert-True {
    param(
        [Parameter(Mandatory)] [bool] $Condition,
        [Parameter(Mandatory)] [string] $Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Assert-Throws {
    param(
        [Parameter(Mandatory)] [scriptblock] $Script,
        [Parameter(Mandatory)] [string] $Message
    )

    try {
        & $Script
    } catch {
        return
    }
    throw "Expected an exception: $Message"
}

$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) "automatone-workflow-$([guid]::NewGuid())"
New-Item -ItemType Directory -Path $testRoot | Out-Null
try {
    New-Item -ItemType Directory -Path (Join-Path $testRoot '.agents/tasks') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $testRoot '.agents/verification') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $testRoot 'config/verification') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $testRoot 'tracked.txt') -Value 'source' -NoNewline
    Set-Content -LiteralPath (Join-Path $testRoot '.agents/tasks/BOOTSTRAP.md') -Value '# BOOTSTRAP' -NoNewline
    Set-Content -LiteralPath (Join-Path $testRoot 'config/verification/profiles.json') -Value '{"profiles":{"default":{"gradle_tasks":["sensorAll"],"sensors":["compile"]},"bootstrap":{"gradle_tasks":["sensorAll"],"sensors":["compile"]}}}'
    $schemaPath = Join-Path $testRoot '.agents/verification/report.schema.json'
    $repositorySchemaPath = Join-Path $PSScriptRoot '../../.agents/verification/report.schema.json'
    Set-Content -LiteralPath $schemaPath -Value (Get-Content -LiteralPath $repositorySchemaPath -Raw)

    $fingerprint = Get-SourceFingerprint -Root $testRoot
    New-Item -ItemType Directory -Path (Join-Path $testRoot '.agents/evidence/bootstrap/independent') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $testRoot '.agents/evidence/bootstrap/independent/runner-report.json') -Value '{"verdict":"INCOMPLETE"}'
    Assert-Equal -Expected $fingerprint -Actual (Get-SourceFingerprint -Root $testRoot) -Message 'generated evidence must not change the source fingerprint'
    Set-Content -LiteralPath (Join-Path $testRoot 'tracked.txt') -Value 'source-edited' -NoNewline
    Assert-True -Condition ($fingerprint -ne (Get-SourceFingerprint -Root $testRoot)) -Message 'tracked source edits must change the source fingerprint'
    Set-Content -LiteralPath (Join-Path $testRoot 'tracked.txt') -Value 'source' -NoNewline
    if ((Get-Content -LiteralPath (Join-Path $testRoot 'tracked.txt') -Raw) -cne 'source') { throw 'Fixture content measurement failed' }
    $fixtureAcceptance = @([ordered]@{ id = 'fixture-content'; status = 'PASS'; evidence = "Read tracked.txt and matched exact expected content 'source'. SHA256=$((Get-FileHash (Join-Path $testRoot 'tracked.txt')).Hash)" })
    $sensors = @(
        [ordered]@{ id = 'compile'; required = $true; status = 'PASS'; command = 'compile'; summary = 'ok' },
        [ordered]@{ id = 'runtime'; required = $true; status = 'UNVERIFIED'; command = 'runtime'; summary = 'missing' }
    )
    $report = New-VerificationReport -Root $testRoot -TaskId 'BOOTSTRAP' -Candidate 'dirty' -Baseline 'UNACCEPTED' -FreshContext $false -Sensors $sensors -Acceptance @() -Failures @() -Unverified @('runtime')
    Assert-Equal -Expected 'INCOMPLETE' -Actual $report.verdict -Message 'required UNVERIFIED must normalize to INCOMPLETE'
    Assert-Equal -Expected 2 -Actual (Get-VerificationExitCode -Report $report) -Message 'incomplete report must be nonzero'

    $passSensors = @([ordered]@{ id = 'compile'; required = $true; status = 'PASS'; command = 'compile'; summary = 'ok' })
    $passReport = New-VerificationReport -Root $testRoot -TaskId 'FIXTURE' -Candidate 'dirty' -Baseline 'UNACCEPTED' -FreshContext $false -Sensors $passSensors -Acceptance $fixtureAcceptance -Failures @()
    Assert-Equal -Expected 0 -Actual (Get-VerificationExitCode -Report $passReport) -Message 'all-pass report should have a successful validation exit code'

    $warnSensors = @([ordered]@{ id = 'advisory'; required = $true; status = 'WARN'; command = 'advisory'; summary = 'visible finding' })
    $warnReport = New-VerificationReport -Root $testRoot -TaskId 'FIXTURE' -Candidate 'dirty' -Baseline 'UNACCEPTED' -FreshContext $false -Sensors $warnSensors -Acceptance $fixtureAcceptance -Failures @()
    Assert-Equal -Expected 'PASS' -Actual $warnReport.verdict -Message 'required WARN is non-blocking under the configured policy'

    $failSensors = @([ordered]@{ id = 'architecture'; required = $true; status = 'FAIL'; command = 'arch'; summary = 'finding' })
    $failReport = New-VerificationReport -Root $testRoot -TaskId 'BOOTSTRAP' -Candidate 'dirty' -Baseline 'UNACCEPTED' -FreshContext $false -Sensors $failSensors -Acceptance @() -Failures @()
    Assert-Equal -Expected 'FAIL' -Actual $failReport.verdict -Message 'required FAIL must normalize to FAIL'
    Assert-Equal -Expected 1 -Actual (Get-VerificationExitCode -Report $failReport) -Message 'failed report must be nonzero'

    $reportPath = Join-Path $testRoot '.agents/evidence/bootstrap/tooling/report.json'
    Write-VerificationReport -Report $passReport -Path $reportPath
    $validation = Test-VerificationReport -ReportPath $reportPath -Root $testRoot -SchemaPath $schemaPath
    Assert-True -Condition $validation.Valid -Message 'a schema-valid, fingerprint-bound report should validate'

    $mismatched = $passReport | ConvertTo-Json -Depth 20 | ConvertFrom-Json
    $mismatched.source_fingerprint = ('0' * 64)
    Set-Content -LiteralPath $reportPath -Value ($mismatched | ConvertTo-Json -Depth 20)
    Assert-Throws -Script { Test-VerificationReport -ReportPath $reportPath -Root $testRoot -SchemaPath $schemaPath -ThrowOnInvalid } -Message 'fingerprint mismatch must fail closed'

    $invalidReport = $passReport | ConvertTo-Json -Depth 20 | ConvertFrom-Json
    $invalidReport.verdict = 'NOT_A_VERDICT'
    Set-Content -LiteralPath $reportPath -Value ($invalidReport | ConvertTo-Json -Depth 20)
    Assert-Throws -Script { Test-VerificationReport -ReportPath $reportPath -Root $testRoot -SchemaPath $schemaPath -ThrowOnInvalid } -Message 'schema enum violations must fail closed'

    $resolved = Resolve-WorkflowTask -Root $testRoot -TaskId 'BOOTSTRAP'
    Assert-Equal -Expected (Join-Path $testRoot '.agents/tasks/BOOTSTRAP.md') -Actual $resolved.Path -Message 'bootstrap uses explicit frontmatter-free task path'
    Assert-Throws -Script { Resolve-WorkflowTask -Root $testRoot -TaskId 'M1.1' } -Message 'missing product task must not be selected'
    Assert-Throws -Script { Resolve-WorkflowTask -Root $testRoot -TaskId '../BOOTSTRAP' } -Message 'path traversal task input must be rejected'

    $profile = Resolve-WorkflowProfile -Root $testRoot -Profile 'bootstrap'
    Assert-Equal -Expected 'sensorAll' -Actual $profile.GradleTasks[0] -Message 'configured profile must select its declared Gradle task'
    Assert-Throws -Script { Resolve-WorkflowProfile -Root $testRoot -Profile 'unknown' } -Message 'unknown profile must be rejected'

    $failedOutput = @(
        '> Task :compileJava FAILED'
        'https://errorprone.info/bugpattern/FormatString'
        'error: [FormatString] missing argument'
    ) -join "`n"
    $failedRun = [pscustomobject]@{
        task = 'sensorAll'
        exit_code = 1
        output = $failedOutput
        markers = @{}
        artifact = 'failed-gradle.log'
    }
    $failedProfile = [pscustomobject]@{ GradleTasks = @('sensorAll'); SensorIds = @('compile', 'error_prone', 'product_source_hashes') }
    $failedRecords = @(Get-TaskSensorRecords -Profile $failedProfile -Runs @($failedRun) -Root $testRoot)
    Assert-Equal -Expected 'FAIL' -Actual (@($failedRecords | Where-Object id -eq 'compile')[0].status) -Message 'failed Gradle compile without marker must be FAIL'
    Assert-Equal -Expected 'FAIL' -Actual (@($failedRecords | Where-Object id -eq 'error_prone')[0].status) -Message 'executed Error Prone failure without marker must be FAIL'
    Assert-Equal -Expected 0 -Actual @($failedRecords | Where-Object id -eq 'product_source_hashes').Count -Message 'controller-only source hash sensor must not be duplicated by Gradle record parsing'
    Assert-Equal -Expected @($failedRecords.id).Count -Actual @(@($failedRecords.id) | Select-Object -Unique).Count -Message 'sensor IDs from one Gradle run must be unique'

    Set-Content -LiteralPath (Join-Path $testRoot '.agents/tasks/QUALITY-CLEANUP.md') -Value '# QUALITY-CLEANUP' -NoNewline
    Set-Content -LiteralPath (Join-Path $testRoot 'gradlew.bat') -Value "@echo off`necho AUTOMATONE_SENSOR id=compile status=PASS summary=fixture`nexit /b 0" -NoNewline
    $statePath = Join-Path $testRoot '.agents/STATE.yaml'
    Set-Content -LiteralPath $statePath -Value 'accepted_baseline: null' -NoNewline
    $stateBefore = Get-Content -LiteralPath $statePath -Raw
    $measurement = Invoke-VerificationController -Root $testRoot -TaskId 'QUALITY-CLEANUP' -Profile @('bootstrap') -GradleTasks @('compileJava') -ReportPath (Join-Path $testRoot '.agents/evidence/measurement.json')
    Assert-Equal -Expected 'PASS' -Actual (@($measurement.Report.sensors | Where-Object id -eq 'compile')[0].status) -Message 'authorized task measurement must retain the selected check result'
    Assert-Equal -Expected $stateBefore -Actual (Get-Content -LiteralPath $statePath -Raw) -Message 'measurement must not mutate acceptance state'

    Write-Output 'VerificationWorkflow tests: PASS (21 assertions)'
    & (Join-Path $PSScriptRoot 'VerificationWorkflow.Repair.Tests.ps1')
    & (Join-Path $PSScriptRoot 'VerificationWorkflow.Proportional.Tests.ps1')
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}

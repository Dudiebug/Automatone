Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot 'VerificationWorkflow.psm1') -Force

$script:passed = 0
$script:failed = 0

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

function Check {
    param(
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [scriptblock] $Body
    )

    try {
        & $Body
        $script:passed++
        Write-Output "PASS: $Name"
    } catch {
        $script:failed++
        Write-Output "FAIL: ${Name}: $($_.Exception.Message)"
    }
}

function Get-PropertyValue {
    param(
        [Parameter(Mandatory)] $Object,
        [Parameter(Mandatory)] [string] $Name
    )

    if ($null -eq $Object) {
        return $null
    }
    if ($Object -is [System.Collections.IDictionary]) {
        if ($Object.Contains($Name)) {
            return $Object[$Name]
        }
        return $null
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function New-WorkflowFixture {
    param([switch] $Git)

    $root = Join-Path ([IO.Path]::GetTempPath()) "automatone-proportional-$([guid]::NewGuid())"
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    foreach ($relative in @(
            '.agents/tasks',
            '.agents/verification',
            '.agents/evidence',
            'config/verification')) {
        New-Item -ItemType Directory -Path (Join-Path $root $relative) -Force | Out-Null
    }

    Set-Content -LiteralPath (Join-Path $root '.agents/tasks/QUALITY-CLEANUP.md') -Value '# QUALITY-CLEANUP' -NoNewline
    Set-Content -LiteralPath (Join-Path $root '.agents/tasks/BOOTSTRAP.md') -Value '# BOOTSTRAP' -NoNewline
    Set-Content -LiteralPath (Join-Path $root '.agents/STATE.yaml') -Value 'accepted_baseline: null' -NoNewline
    Set-Content -LiteralPath (Join-Path $root '.gitignore') -Value '.agents/evidence/*' -NoNewline
    Set-Content -LiteralPath (Join-Path $root 'tracked.txt') -Value 'fixture-source' -NoNewline

    $profiles = [ordered]@{
        profiles = [ordered]@{
            default = [ordered]@{
                gradle_tasks = @('compileJava', 'sensorCheck')
                sensors = @('compile', 'unit_tests', 'checkstyle', 'error_prone', 'spotbugs', 'archunit', 'duplication')
            }
            architecture_sensitive = [ordered]@{
                gradle_tasks = @('sensorCheck', 'cpdCheck')
                sensors = @('archunit', 'duplication')
            }
        }
    }
    Set-Content -LiteralPath (Join-Path $root 'config/verification/profiles.json') -Value ($profiles | ConvertTo-Json -Depth 10)
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../../.agents/verification/report.schema.json') -Destination (Join-Path $root '.agents/verification/report.schema.json')

    $wrapper = @'
@echo off
setlocal
set "ARGS_FILE=%~dp0.agents\evidence\wrapper-args.txt"
set "FIRST=%~1"
set "SECOND=%~2"
>> "%ARGS_FILE%" echo CALL
:next
if "%~1"=="" goto sensors
>> "%ARGS_FILE%" echo ARG=%~1
shift
goto next
:sensors
rem Focused calls expose only the check they actually selected. A combined
rem milestone call exposes the full profile marker set.
if "%FIRST%"=="test" goto unit_tests
if "%FIRST%"=="help" goto markerless
if "%FIRST%"=="compileJava" if /I "%SECOND%"=="sensorCheck" goto all_sensors
if "%FIRST%"=="compileJava" goto compile
goto all_sensors
:compile
echo AUTOMATONE_SENSOR id=compile status=PASS summary=fixture
goto done
:unit_tests
echo AUTOMATONE_SENSOR id=unit_tests status=PASS summary=fixture
goto done
:markerless
goto done
:all_sensors
echo AUTOMATONE_SENSOR id=compile status=PASS summary=fixture
echo AUTOMATONE_SENSOR id=unit_tests status=PASS summary=fixture
echo AUTOMATONE_SENSOR id=checkstyle status=PASS summary=fixture
echo AUTOMATONE_SENSOR id=error_prone status=PASS summary=fixture
echo AUTOMATONE_SENSOR id=spotbugs status=PASS summary=fixture
echo AUTOMATONE_SENSOR id=archunit status=PASS summary=fixture
echo AUTOMATONE_SENSOR id=duplication status=PASS summary=fixture
:done
exit /b 0
'@
    Set-Content -LiteralPath (Join-Path $root 'gradlew.bat') -Value $wrapper -Encoding utf8NoBOM -NoNewline

    if ($Git) {
        & git -C $root init --quiet
        if ($LASTEXITCODE -ne 0) { throw 'git init failed for fixture' }
        & git -C $root config user.email fixture@example.invalid
        & git -C $root config user.name fixture
        & git -C $root add .
        if ($LASTEXITCODE -ne 0) { throw 'git add failed for fixture' }
        & git -C $root commit --quiet -m fixture
        if ($LASTEXITCODE -ne 0) { throw 'git commit failed for fixture' }
    }

    return [pscustomobject]@{
        Root = $root
        StatePath = (Join-Path $root '.agents/STATE.yaml')
        ArgumentsPath = (Join-Path $root '.agents/evidence/wrapper-args.txt')
        ReportPath = (Join-Path $root '.agents/evidence/task-report.json')
    }
}

function Get-WrapperArguments {
    param([Parameter(Mandatory)] [string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return @()
    }
    return @(Get-Content -LiteralPath $Path | Where-Object { $_ -like 'ARG=*' } | ForEach-Object { $_.Substring(4) })
}

function Get-WrapperCallCount {
    param([Parameter(Mandatory)] [string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return 0
    }
    return @((Get-Content -LiteralPath $Path | Where-Object { $_ -eq 'CALL' })).Count
}

$fixture = New-WorkflowFixture
try {
    Check 'QUALITY-CLEANUP is an eligible measurement task' {
        $task = Resolve-WorkflowTask -Root $fixture.Root -TaskId 'QUALITY-CLEANUP'
        Assert-Equal -Expected (Join-Path $fixture.Root '.agents/tasks/QUALITY-CLEANUP.md') -Actual $task.Path -Message 'cleanup task path was not resolved'
    }

    Check 'default profile is available and explicit profile arrays resolve' {
        $profile = Resolve-WorkflowProfile -Root $fixture.Root -Profile 'default'
        Assert-Equal -Expected 'compileJava' -Actual $profile.GradleTasks[0] -Message 'default profile task was not loaded'
        Assert-Equal -Expected 'compile' -Actual $profile.SensorIds[0] -Message 'default profile sensor was not loaded'
    }

    Check 'unknown profile is rejected' {
        Assert-Throws -Script {
            Resolve-WorkflowProfile -Root $fixture.Root -Profile 'missing-profile'
        } -Message 'unknown profile'
    }

    Check 'Task requires an explicit nonempty Gradle task selector' {
        Assert-Throws -Script {
            Invoke-VerificationController -Root $fixture.Root -TaskId 'QUALITY-CLEANUP' -Profile @('default') -ReportPath $fixture.ReportPath
        } -Message 'profile tasks must not be selected implicitly for a Task run'
        Assert-Equal -Expected 0 -Actual (Get-WrapperCallCount -Path $fixture.ArgumentsPath) -Message 'implicit profile selection launched the wrapper'
    }

    Check 'blank and malformed task selectors are rejected before execution' {
        foreach ($selector in @(@(''), @('bad/name'))) {
            Assert-Throws -Script {
                Invoke-VerificationController -Root $fixture.Root -TaskId 'QUALITY-CLEANUP' -Profile @('default') -GradleTasks $selector -ReportPath $fixture.ReportPath
            } -Message "invalid selector '$selector'"
        }
        Assert-Equal -Expected 0 -Actual (Get-WrapperCallCount -Path $fixture.ArgumentsPath) -Message 'invalid selector launched the wrapper'
    }

    Check 'broad task choices require a risk reason' {
        foreach ($taskName in @('sensorCheck', 'sensorAll', 'test', 'runGameTestServer', 'sensorArchunit', 'spotbugsMain', 'cpdCheck')) {
            $parameters = @{
                Root = $fixture.Root
                TaskId = 'QUALITY-CLEANUP'
                Profile = @('default')
                GradleTasks = @($taskName)
                ReportPath = $fixture.ReportPath
            }
            Assert-Throws -Script {
                Invoke-VerificationController @parameters
            } -Message "broad task '$taskName' without risk reason"
        }
        Assert-Equal -Expected 0 -Actual (Get-WrapperCallCount -Path $fixture.ArgumentsPath) -Message 'risk-gated task launched the wrapper'
    }

    Check 'task filters are forwarded as separate Gradle argv values' {
        $result = Invoke-VerificationController -Root $fixture.Root -TaskId 'QUALITY-CLEANUP' -Profile @('default') -GradleTasks @('test') -TestFilter @('baritone.FirstTest', 'baritone.SecondTest') -ReportPath $fixture.ReportPath
        Assert-True -Condition ($null -ne $result) -Message 'filtered task returned no result'
        $arguments = @(Get-WrapperArguments -Path $fixture.ArgumentsPath)
        $expected = @('--tests', 'baritone.FirstTest', '--tests', 'baritone.SecondTest')
        $found = $false
        for ($index = 0; $index -le $arguments.Count - $expected.Count; $index++) {
            $slice = @($arguments[$index..($index + $expected.Count - 1)])
            if ($slice.Count -eq $expected.Count) {
                $matches = $true
                for ($offset = 0; $offset -lt $expected.Count; $offset++) {
                    if ($slice[$offset] -cne $expected[$offset]) {
                        $matches = $false
                        break
                    }
                }
                if ($matches) {
                    $found = $true
                    break
                }
            }
        }
        Assert-True -Condition $found -Message 'test filters were not emitted as separate --tests argv pairs'
        Assert-True -Condition (-not ($arguments -contains 'baritone.FirstTest,baritone.SecondTest')) -Message 'test filters were joined into one argv value'
    }

    Check 'Task report keeps selected checks and marks deferred profile checks PENDING' {
        Remove-Item -LiteralPath $fixture.ArgumentsPath -Force -ErrorAction SilentlyContinue
        $stateBefore = Get-Content -LiteralPath $fixture.StatePath -Raw
        $result = Invoke-VerificationController -Root $fixture.Root -TaskId 'QUALITY-CLEANUP' -Profile @('default') -GradleTasks @('compileJava') -ReportPath $fixture.ReportPath
        $markerSensors = @($result.Report.sensors | Where-Object { $_.id -ne 'selected_checks' })
        Assert-Equal -Expected 1 -Actual $markerSensors.Count -Message 'Task report included unselected checks as executed sensors'
        Assert-Equal -Expected 'compile' -Actual $markerSensors[0].id -Message 'Task report omitted the selected marker'
        Assert-Equal -Expected 'PASS' -Actual $markerSensors[0].status -Message 'selected marker result was not preserved'
        $selectedChecks = @($result.Report.sensors | Where-Object id -eq 'selected_checks')
        Assert-Equal -Expected 0 -Actual $selectedChecks.Count -Message 'markerful focused task gained a duplicate selected_checks record'
        $deferred = @(Get-PropertyValue -Object $result.Report -Name 'deferred_checks')
        Assert-Equal -Expected 6 -Actual $deferred.Count -Message 'full profile deferrals were not recorded'
        Assert-Equal -Expected 6 -Actual @($deferred | Where-Object { $_.status -eq 'PENDING' }).Count -Message 'deferred checks were not PENDING'
        Assert-Equal -Expected 0 -Actual @($deferred | Where-Object { $_.status -eq 'PASS' }).Count -Message 'deferred checks were incorrectly marked PASS'
        Assert-Equal -Expected 0 -Actual @($deferred | Where-Object id -eq 'compile').Count -Message 'measured marker was incorrectly deferred'
        Assert-True -Condition (@($deferred | Where-Object { [string]::IsNullOrWhiteSpace([string] $_.reason) }).Count -eq 0) -Message 'deferred check omitted its reason'
        Assert-True -Condition ($null -eq $result.Report.accepted_baseline) -Message 'Task measurement invented an accepted baseline'
        Assert-True -Condition ($result.Report.verdict -ne 'COMPLETE' -and $result.Report.verdict -ne 'ACCEPTED') -Message 'Task measurement claimed completion or acceptance'
        Assert-Equal -Expected $stateBefore -Actual (Get-Content -LiteralPath $fixture.StatePath -Raw) -Message 'Task measurement mutated acceptance state'
    }

    Check 'markerless focused task records a required selected_checks process result' {
        Remove-Item -LiteralPath $fixture.ArgumentsPath -Force -ErrorAction SilentlyContinue
        $reportPath = Join-Path (Split-Path $fixture.ReportPath -Parent) 'markerless.json'
        $result = Invoke-VerificationController -Root $fixture.Root -TaskId 'QUALITY-CLEANUP' -Profile @('default') -GradleTasks @('help') -ReportPath $reportPath
        $sensors = @($result.Report.sensors)
        Assert-Equal -Expected 1 -Actual $sensors.Count -Message 'markerless task fabricated sensor results'
        Assert-Equal -Expected 'selected_checks' -Actual $sensors[0].id -Message 'markerless task omitted selected_checks'
        Assert-True -Condition $sensors[0].required -Message 'selected_checks process measurement was advisory'
        Assert-Equal -Expected 'PASS' -Actual $sensors[0].status -Message 'markerless selected check process result was not preserved'
        $deferred = @(Get-PropertyValue -Object $result.Report -Name 'deferred_checks')
        Assert-Equal -Expected 7 -Actual $deferred.Count -Message 'markerless task did not defer every unmeasured profile sensor'
        Assert-Equal -Expected 7 -Actual @($deferred | Where-Object { $_.status -eq 'PENDING' }).Count -Message 'markerless deferred checks were not PENDING'
    }

    Check 'SpotBugs disposition gate failure overrides a premature PASS marker' {
        $output = @(
            'AUTOMATONE_SENSOR id=spotbugs status=PASS summary=marker emitted too early'
            '> Task :verifyMainSpotBugsDispositions FAILED'
        ) -join "`n"
        $run = [pscustomobject]@{
            task = 'sensorCheck'
            exit_code = 1
            output = $output
            markers = (Get-GradleSensorMarkers -Output $output)
            artifact = 'spotbugs-gate.log'
        }
        $profile = [pscustomobject]@{ GradleTasks = @('sensorCheck'); SensorIds = @('spotbugs') }
        $records = @(Get-TaskSensorRecords -Profile $profile -Runs @($run) -Root $fixture.Root)
        Assert-Equal -Expected 'FAIL' -Actual $records[0].status -Message 'SpotBugs gate failure was hidden by an earlier PASS marker'
    }

    Check 'Milestone requires fresh context and a clean git candidate' {
        $clean = New-WorkflowFixture -Git
        try {
            Assert-Throws -Script {
                Invoke-VerificationController -Root $clean.Root -TaskId 'QUALITY-CLEANUP' -Profile @('default') -Scope Milestone -RiskReason 'fixture milestone gate' -ReportPath $clean.ReportPath
            } -Message 'Milestone without FreshContext'
            Set-Content -LiteralPath (Join-Path $clean.Root 'tracked.txt') -Value 'dirty' -NoNewline
            Assert-Throws -Script {
                Invoke-VerificationController -Root $clean.Root -TaskId 'QUALITY-CLEANUP' -Profile @('default') -Scope Milestone -RiskReason 'fixture milestone gate' -FreshContext -ReportPath $clean.ReportPath
            } -Message 'Milestone on a dirty git candidate'
        } finally {
            if (Test-Path -LiteralPath $clean.Root) { Remove-Item -LiteralPath $clean.Root -Recurse -Force }
        }
    }

    Check 'Milestone rejects task and filter overrides' {
        $clean = New-WorkflowFixture -Git
        try {
            Assert-Throws -Script {
                Invoke-VerificationController -Root $clean.Root -TaskId 'QUALITY-CLEANUP' -Profile @('default') -Scope Milestone -GradleTasks @('compileJava') -RiskReason 'fixture milestone gate' -FreshContext -ReportPath $clean.ReportPath
            } -Message 'Milestone task override'
            Assert-Throws -Script {
                Invoke-VerificationController -Root $clean.Root -TaskId 'QUALITY-CLEANUP' -Profile @('default') -Scope Milestone -TestFilter @('baritone.FilteredTest') -RiskReason 'fixture milestone gate' -FreshContext -ReportPath $clean.ReportPath
            } -Message 'Milestone test-filter override'
        } finally {
            if (Test-Path -LiteralPath $clean.Root) { Remove-Item -LiteralPath $clean.Root -Recurse -Force }
        }
    }

    Check 'Milestone deduplicates profile tasks and emits no deferred checks' {
        $clean = New-WorkflowFixture -Git
        try {
            $stateBefore = Get-Content -LiteralPath $clean.StatePath -Raw
            $beforeCommits = @(& git -C $clean.Root rev-list --count HEAD)
            $result = Invoke-VerificationController -Root $clean.Root -TaskId 'QUALITY-CLEANUP' -Profile @('default', 'architecture_sensitive') -Scope Milestone -RiskReason 'fixture milestone gate' -FreshContext -ReportPath $clean.ReportPath
            Assert-Equal -Expected 1 -Actual (Get-WrapperCallCount -Path $clean.ArgumentsPath) -Message 'combined milestone tasks were executed more than once'
            $arguments = @(Get-WrapperArguments -Path $clean.ArgumentsPath)
            foreach ($taskName in @('compileJava', 'sensorCheck', 'cpdCheck')) {
                Assert-Equal -Expected 1 -Actual @($arguments | Where-Object { $_ -ceq $taskName }).Count -Message "combined task '$taskName' was not deduplicated"
            }
            $deferred = @(Get-PropertyValue -Object $result.Report -Name 'deferred_checks')
            Assert-Equal -Expected 0 -Actual $deferred.Count -Message 'Milestone report contains deferred checks'
            Assert-Equal -Expected 7 -Actual @($result.Report.sensors).Count -Message 'Milestone report omitted a profile sensor'
            Assert-Equal -Expected 0 -Actual @($result.Report.sensors | Where-Object { $_.status -ne 'PASS' }).Count -Message 'Milestone report contains a non-PASS selected sensor'
            Assert-True -Condition ($null -eq $result.Report.accepted_baseline) -Message 'Milestone measurement invented an accepted baseline'
            Assert-Equal -Expected $stateBefore -Actual (Get-Content -LiteralPath $clean.StatePath -Raw) -Message 'Milestone measurement mutated acceptance state'
            Assert-Equal -Expected $beforeCommits -Actual @(& git -C $clean.Root rev-list --count HEAD) -Message 'controller created a commit during measurement'
        } finally {
            if (Test-Path -LiteralPath $clean.Root) { Remove-Item -LiteralPath $clean.Root -Recurse -Force }
        }
    }

    Check 'invalid scope is rejected' {
        Assert-Throws -Script {
            Invoke-VerificationController -Root $fixture.Root -TaskId 'QUALITY-CLEANUP' -Profile @('default') -Scope 'Unknown' -GradleTasks @('compileJava') -ReportPath $fixture.ReportPath
        } -Message 'invalid scope'
    }

    Write-Output "Proportional workflow tests: $script:passed passed, $script:failed failed"
    if ($script:failed) {
        throw 'Proportional workflow tests failed.'
    }
} finally {
    if (Test-Path -LiteralPath $fixture.Root) {
        Remove-Item -LiteralPath $fixture.Root -Recurse -Force
    }
}

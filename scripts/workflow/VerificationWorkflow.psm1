Set-StrictMode -Version Latest

$script:VerificationStatuses = @('PASS', 'FAIL', 'WARN', 'UNVERIFIED', 'SKIPPED')
$script:Verdicts = @('PASS', 'FAIL', 'INCOMPLETE', 'BLOCKED_RECOMMENDED')

function Get-ObjectProperty {
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
    if ($null -ne $property) {
        return $property.Value
    }
    return $null
}

function Get-SourceFingerprint {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string] $Root)

    if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
        throw "Source root does not exist: $Root"
    }
    $rootPath = (Resolve-Path -LiteralPath $Root).Path
    $files = Get-ChildItem -LiteralPath $rootPath -Recurse -File -Force | Where-Object {
        $relative = [IO.Path]::GetRelativePath($rootPath, $_.FullName).Replace('\', '/')
        $relative -notmatch '^(?:\.git|\.gradle|build|graphify-out|src/graphify-out)(?:/|$)' -and
            $relative -ne '.agents/STATE.yaml' -and
            $relative -notmatch '^\.agents/evidence(?:/|$)'
    } | Sort-Object { [IO.Path]::GetRelativePath($rootPath, $_.FullName).Replace('\', '/') }

    $hasher = [Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA256)
    try {
        $encoding = [Text.UTF8Encoding]::new($false)
        foreach ($file in $files) {
            $relative = [IO.Path]::GetRelativePath($rootPath, $file.FullName).Replace('\', '/')
            $fileHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToUpperInvariant()
            $hasher.AppendData($encoding.GetBytes("$relative`0$fileHash`n"))
        }
        return ([BitConverter]::ToString($hasher.GetHashAndReset())).Replace('-', '')
    } finally {
        $hasher.Dispose()
    }
}

function Get-NormalizedVerdict {
    param(
        [Parameter(Mandatory)] [object[]] $Sensors,
        [object[]] $Failures = @(),
        [AllowNull()] [AllowEmptyCollection()] [object[]] $Acceptance = @()
    )

    if ($Sensors.Count -eq 0) {
        throw 'At least one sensor is required; an empty sensor set cannot pass.'
    }
    $requiredSensors = @($Sensors | Where-Object { [bool] (Get-ObjectProperty $_ 'required') })
    if ($requiredSensors.Count -eq 0) {
        throw 'At least one required sensor is required; an advisory-only report cannot pass.'
    }
    $checkAcceptance = $PSBoundParameters.ContainsKey('Acceptance')
    if (@($Failures | Where-Object { (Get-ObjectProperty $_ 'classification') -ne 'ENVIRONMENT_FAILURE' }).Count -gt 0) {
        return 'FAIL'
    }
    if (@($requiredSensors | Where-Object { (Get-ObjectProperty $_ 'status') -eq 'FAIL' }).Count -gt 0) {
        return 'FAIL'
    }
    if ($checkAcceptance -and @($Acceptance | Where-Object { $null -ne $_ -and (Get-ObjectProperty $_ 'status') -eq 'FAIL' }).Count -gt 0) {
        return 'FAIL'
    }
    # This CLI has no applicability-proof verifier. A required skip cannot be auto-waived.
    if ($Failures.Count -gt 0 -or @($requiredSensors | Where-Object { (Get-ObjectProperty $_ 'status') -in @('UNVERIFIED', 'SKIPPED') }).Count -gt 0) {
        return 'INCOMPLETE'
    }
    if (@($requiredSensors | Where-Object { (Get-ObjectProperty $_ 'status') -notin @('PASS', 'WARN', 'SKIPPED') }).Count -gt 0) {
        throw 'A required sensor has an unknown or unsupported status.'
    }
    if ($checkAcceptance -and (@($Acceptance).Count -eq 0 -or @($Acceptance | Where-Object {
        $null -eq $_ -or (Get-ObjectProperty $_ 'status') -ne 'PASS' -or
            [string]::IsNullOrWhiteSpace([string] (Get-ObjectProperty $_ 'evidence'))
    }).Count -gt 0)) {
        return 'INCOMPLETE'
    }
    return 'PASS'
}

function New-VerificationReport {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string] $TaskId,
        [Parameter(Mandatory)] [string] $Candidate,
        [Parameter(Mandatory)] [string] $Baseline,
        [Parameter(Mandatory)] [bool] $FreshContext,
        [Parameter(Mandatory)] [object[]] $Sensors,
        [AllowNull()] [AllowEmptyCollection()] [object[]] $Acceptance = @(),
        [Parameter(Mandatory)] [AllowEmptyCollection()] [object[]] $Failures,
        [string[]] $Warnings = @(),
        [string[]] $Unverified = @(),
        [string] $SourceFingerprint,
        [ValidateSet('Task', 'Milestone')] [string] $Scope = 'Task',
        [object[]] $DeferredChecks = @()
    )

    $normalizedSensors = @($Sensors)
    if ($null -eq $Acceptance) { $Acceptance = @() }
    foreach ($sensor in $normalizedSensors) {
        $status = [string] (Get-ObjectProperty $sensor 'status')
        if ($status -notin $script:VerificationStatuses) {
            throw "Unsupported sensor status '$status'."
        }
    }
    $verdict = Get-NormalizedVerdict -Sensors $normalizedSensors -Failures $Failures -Acceptance $Acceptance
    if ([string]::IsNullOrWhiteSpace($SourceFingerprint)) {
        $SourceFingerprint = Get-SourceFingerprint -Root $Root
    }
    $unverifiedIds = @($normalizedSensors | Where-Object { (Get-ObjectProperty $_ 'status') -eq 'UNVERIFIED' } | ForEach-Object { [string] (Get-ObjectProperty $_ 'id') })
    $allUnverified = @($Unverified) + $unverifiedIds | Sort-Object -Unique

    return [ordered]@{
        task_id = $TaskId
        candidate = $Candidate
        baseline = $Baseline
        accepted_baseline = $null
        verdict = $verdict
        fresh_context = $FreshContext
        verification_scope = $Scope
        deferred_checks = @($DeferredChecks)
        source_fingerprint = $SourceFingerprint
        sensors = $normalizedSensors
        acceptance = @($Acceptance)
        failures = @($Failures)
        warnings = @($Warnings)
        unverified = @($allUnverified)
    }
}

function Get-VerificationExitCode {
    param([Parameter(Mandatory)] $Report)

    switch ([string] (Get-ObjectProperty $Report 'verdict')) {
        'PASS' { return 0 }
        'FAIL' { return 1 }
        'INCOMPLETE' { return 2 }
        'BLOCKED_RECOMMENDED' { return 3 }
        default { return 3 }
    }
}

function Write-VerificationReport {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Report,
        [Parameter(Mandatory)] [string] $Path
    )

    $parent = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $json = $Report | ConvertTo-Json -Depth 50
    Set-Content -LiteralPath $Path -Value $json -Encoding utf8NoBOM
    return (Resolve-Path -LiteralPath $Path).Path
}

function Test-VerificationReport {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $ReportPath,
        [Parameter(Mandatory)] [string] $Root,
        [string] $SchemaPath,
        [switch] $ThrowOnInvalid
    )

    try {
        if (-not (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
            throw "Report does not exist: $ReportPath"
        }
        if ([string]::IsNullOrWhiteSpace($SchemaPath)) {
            $SchemaPath = Join-Path $Root '.agents/verification/report.schema.json'
        }
        if (-not (Test-Path -LiteralPath $SchemaPath -PathType Leaf)) {
            throw "Report schema does not exist: $SchemaPath"
        }
        $reportJson = Get-Content -LiteralPath $ReportPath -Raw
        if (-not (Test-Json -Json $reportJson -SchemaFile $SchemaPath)) {
            throw 'Report does not satisfy the configured JSON schema.'
        }
        $report = $reportJson | ConvertFrom-Json -Depth 100
        if ((Get-ObjectProperty $report 'verification_scope') -eq 'Milestone' -and
            (-not $report.fresh_context -or @(Get-ObjectProperty $report 'deferred_checks' | Where-Object { $null -ne $_ }).Count -gt 0)) {
            throw 'Milestone evidence requires a fresh context and no deferred checks.'
        }
        if ($null -eq (Get-ObjectProperty $report 'accepted_baseline') -and $report.baseline -ne 'UNACCEPTED') {
            throw 'A report without an accepted baseline must use the UNACCEPTED baseline sentinel.'
        }
        $fingerprint = [string] (Get-ObjectProperty $report 'source_fingerprint')
        if ($fingerprint -notmatch '^[0-9A-Fa-f]{64}$') {
            throw 'Report source_fingerprint must be a 64-character SHA-256 hex value.'
        }
        $currentFingerprint = Get-SourceFingerprint -Root $Root
        if ($fingerprint.ToUpperInvariant() -ne $currentFingerprint.ToUpperInvariant()) {
            throw "Report source fingerprint does not match the current source tree."
        }
        $sensors = @((Get-ObjectProperty $report 'sensors'))
        $expectedVerdict = Get-NormalizedVerdict -Sensors $sensors -Failures @((Get-ObjectProperty $report 'failures')) -Acceptance @((Get-ObjectProperty $report 'acceptance'))
        if ([string] (Get-ObjectProperty $report 'verdict') -ne $expectedVerdict) {
            throw 'Report verdict is inconsistent with sensors, acceptance criteria, or recorded failures.'
        }
        return [pscustomobject]@{ Valid = $true; Report = $report; Errors = @() }
    } catch {
        if ($ThrowOnInvalid) {
            throw
        }
        return [pscustomobject]@{ Valid = $false; Report = $null; Errors = @($_.Exception.Message) }
    }
}

function Resolve-WorkflowTask {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string] $TaskId
    )

    if ([string]::IsNullOrWhiteSpace($TaskId) -or $TaskId -notmatch '^[A-Z][A-Z0-9]*(?:[.-][A-Z0-9]+)*$') {
        throw "Unknown or invalid task ID '$TaskId'."
    }
    if ($TaskId -eq 'BOOTSTRAP') {
        $relative = '.agents/tasks/BOOTSTRAP.md'
        $format = 'explicit-bootstrap-markdown'
    } else {
        $relative = ".agents/tasks/$TaskId.md"
        $format = 'frontmatter-task-spec'
    }
    $path = Join-Path $Root $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Task spec is not available: $relative"
    }
    return [pscustomobject]@{ Id = $TaskId; Path = (Resolve-Path -LiteralPath $path).Path; RelativePath = $relative; Format = $format }
}

function Resolve-WorkflowProfile {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string] $Profile,
        [string] $ConfigPath
    )

    if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
        $ConfigPath = Join-Path $Root 'config/verification/profiles.json'
    }
    if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
        throw "Verification profile config is not available: $ConfigPath"
    }
    $config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json -Depth 20
    $profiles = Get-ObjectProperty $config 'profiles'
    $selected = Get-ObjectProperty $profiles $Profile
    if ($null -eq $selected) {
        throw "Unknown verification profile '$Profile'."
    }
    $gradleTasks = @((Get-ObjectProperty $selected 'gradle_tasks'))
    $sensorIds = @((Get-ObjectProperty $selected 'sensors'))
    if ($gradleTasks.Count -eq 0 -or $sensorIds.Count -eq 0) {
        throw "Profile '$Profile' must define at least one Gradle task and sensor."
    }
    $arguments = @(Get-ObjectProperty $selected 'gradle_arguments' | Where-Object { $null -ne $_ })
    return [pscustomobject]@{ Name = $Profile; GradleTasks = $gradleTasks; SensorIds = $sensorIds; Arguments = $arguments; ConfigPath = (Resolve-Path -LiteralPath $ConfigPath).Path }
}

function Get-GradleSensorMarkers {
    param([Parameter(Mandatory)] [AllowEmptyString()] [string] $Output)

    $markers = @{}
    $pattern = 'AUTOMATONE_SENSOR\s+id=(?<id>[A-Za-z0-9_.-]+)\s+status=(?<status>PASS|FAIL|WARN|UNVERIFIED|SKIPPED)\s+summary=(?<summary>.*)'
    foreach ($match in [regex]::Matches($Output, $pattern)) {
        $markers[$match.Groups['id'].Value] = [pscustomobject]@{
            id = $match.Groups['id'].Value
            status = $match.Groups['status'].Value
            summary = $match.Groups['summary'].Value.Trim()
        }
    }
    return $markers
}

function Invoke-GradleSensor {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string[]] $GradleTask,
        [Parameter(Mandatory)] [string] $RawOutputPath,
        [string[]] $TestFilter = @(),
        [string[]] $GradleArguments = @()
    )

    $wrapper = Join-Path $Root 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
        throw "Gradle wrapper is not available: $wrapper"
    }
    $parent = Split-Path -Parent $RawOutputPath
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    Push-Location $Root
    try {
        $arguments = @()
        foreach ($name in $GradleTask) {
            $arguments += $name
            if ($name -match '(?:^|:)(?:test|sensorTest)$') {
                foreach ($filter in $TestFilter) { $arguments += @('--tests', $filter) }
            }
        }
        $arguments += $GradleArguments
        $output = (& $wrapper @arguments '--no-daemon' '--continue' '--console=plain' 2>&1 | Out-String)
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { [int] $LASTEXITCODE }
    } finally {
        Pop-Location
    }
    Set-Content -LiteralPath $RawOutputPath -Value $output -Encoding utf8NoBOM
    $markers = Get-GradleSensorMarkers -Output $output
    $status = if ($exitCode -eq 0) { 'PASS' }
        elseif (@($markers.Values | Where-Object { $_.status -eq 'FAIL' }).Count -gt 0) { 'FAIL' }
        elseif (@($markers.Values | Where-Object { $_.status -eq 'UNVERIFIED' }).Count -gt 0) { 'UNVERIFIED' }
        else { 'FAIL' }
    return [pscustomobject]@{
        task = $GradleTask -join ', '
        exit_code = $exitCode
        status = $status
        output = $output
        markers = $markers
        artifact = $RawOutputPath
    }
}

function Get-TaskCandidateLabel {
    param([Parameter(Mandatory)] [string] $Root)

    $head = (& git -C $Root rev-parse HEAD 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($head)) {
        return 'working-tree'
    }
    $changes = (& git -C $Root status --porcelain --untracked-files=all 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -eq 0 -and [string]::IsNullOrWhiteSpace($changes)) {
        return $head
    }
    return "$head-dirty"
}

function Get-TaskSensorRecords {
    param(
        [Parameter(Mandatory)] $Profile,
        [Parameter(Mandatory)] [object[]] $Runs,
        [Parameter(Mandatory)] [string] $Root
    )

    $markers = @{}
    foreach ($run in $Runs) {
        foreach ($key in $run.markers.Keys) {
            $markers[$key] = $run.markers[$key]
        }
    }
    $taskBySensor = @{
        compile = 'sensorCompile'
        unit_tests = 'sensorUnitTests'
        checkstyle = 'sensorCheckstyle'
        error_prone = 'sensorErrorProne'
        spotbugs = 'sensorSpotBugs'
        archunit = 'sensorArchunit'
        duplication = 'cpdCheck'
        neoforge_gametest = 'sensorIntegration'
        spark_server_health = 'sensorServerRuntime'
        dependency_vulnerability = 'sensorDependency'
        dependency_resolution = 'sensorDependency'
    }
    $records = @()
    foreach ($sensorId in @($Profile.SensorIds)) {
        $marker = if ($markers.ContainsKey([string] $sensorId)) { $markers[[string] $sensorId] } else { $null }
        $taskName = if ($taskBySensor.ContainsKey([string] $sensorId)) { $taskBySensor[[string] $sensorId] } else { [string] $sensorId }
        $runtimeFailed = [string] $sensorId -eq 'neoforge_gametest' -and
            @($Runs | Where-Object { $_.output -match '(?m):runGameTestServer FAILED' }).Count -gt 0
        $underlyingFailed = switch ([string] $sensorId) {
            'compile' { @($Runs | Where-Object { $_.output -match '(?m):compile(?:Java|TestJava|SensorTestJava) FAILED' }).Count -gt 0 }
            'unit_tests' { @($Runs | Where-Object { $_.output -match '(?m):test FAILED' }).Count -gt 0 }
            'checkstyle' { @($Runs | Where-Object { $_.output -match '(?m):checkstyle(?:Main|Test|SensorTest) FAILED' }).Count -gt 0 }
            'error_prone' { @($Runs | Where-Object { $_.output -match '(?m):compile(?:Java|TestJava|SensorTestJava) FAILED' -and $_.output -match '(?i)errorprone\.info/bugpattern/' }).Count -gt 0 }
            'spotbugs' { @($Runs | Where-Object { $_.output -match '(?m):(?:spotbugs(?:Main|Test|SensorTest)|verifyMainSpotBugsDispositions|analyzeRawMainSpotBugs) FAILED' }).Count -gt 0 }
            'archunit' { @($Runs | Where-Object { $_.output -match '(?m):sensor(?:Test|Archunit) FAILED' }).Count -gt 0 }
            'duplication' { @($Runs | Where-Object { $_.output -match '(?m):cpdCheck FAILED' }).Count -gt 0 }
            'neoforge_gametest' { $runtimeFailed }
            default { @($Runs | Where-Object { $_.output -match "(?m):$taskName FAILED" }).Count -gt 0 }
        }
        $underlyingFailed = $underlyingFailed -or @($Runs | Where-Object { $_.output -match "(?m):$taskName FAILED" }).Count -gt 0
        # UNVERIFIED tasks intentionally exit nonzero when their measurement is unavailable.
        $failedMeasurement = $runtimeFailed -or ($underlyingFailed -and ($null -eq $marker -or $marker.status -ne 'UNVERIFIED'))
        $status = if ($failedMeasurement) { 'FAIL' } elseif ($null -ne $marker) { $marker.status } else { 'UNVERIFIED' }
        $summary = if ($failedMeasurement) {
            if ([string] $sensorId -eq 'error_prone') {
                'Error Prone diagnostic output was observed and the Java compile task failed.'
            } else {
                'A required Gradle task failed; any earlier successful completion marker is invalid.'
            }
        } elseif ($null -ne $marker) {
            $marker.summary
        } else {
            'No machine-readable completion marker was observed.'
        }
        $command = ($Profile.GradleTasks -join ', ')
        $artifact = if ($Runs.Count -gt 0) { $Runs[0].artifact } else { $null }
        $records += [ordered]@{ id = [string] $sensorId; required = $true; status = $status; command = $command; summary = $summary; artifact = $artifact; new_findings = $null }
    }
    return $records
}

function Invoke-VerificationController {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string] $TaskId,
        [string[]] $Profile = @('default'),
        [Parameter(Mandatory)] [string] $ReportPath,
        [ValidateSet('Task', 'Milestone')] [string] $Scope = 'Task',
        [string[]] $GradleTasks = @(),
        [string[]] $TestFilter = @(),
        [string] $RiskReason,
        [switch] $FreshContext
    )

    $rootPath = (Resolve-Path -LiteralPath $Root).Path
    $task = Resolve-WorkflowTask -Root $rootPath -TaskId $TaskId
    if ($Profile.Count -eq 0) { throw 'Select the applicable milestone profiles explicitly.' }
    $profiles = @($Profile | Select-Object -Unique | ForEach-Object {
        Resolve-WorkflowProfile -Root $rootPath -Profile $_
    })
    $selectedProfile = [pscustomobject]@{
        Name = $Profile -join ', '
        GradleTasks = @($profiles.GradleTasks | Select-Object -Unique)
        SensorIds = @($profiles.SensorIds | Select-Object -Unique)
    }
    $candidate = Get-TaskCandidateLabel -Root $rootPath
    $arguments = @()
    if ($Scope -eq 'Milestone') {
        if (-not $FreshContext -or $candidate -notmatch '^[0-9a-f]{40,64}$') {
            throw 'Milestone verification requires a fresh independent context and a clean committed candidate (including staged and untracked files).'
        }
        if ($GradleTasks.Count -gt 0 -or $TestFilter.Count -gt 0) {
            throw 'Milestone verification cannot use focused task or test-filter overrides.'
        }
        $GradleTasks = $selectedProfile.GradleTasks
        $arguments = @($profiles | ForEach-Object { $_.Arguments } | Select-Object -Unique)
    } elseif ($GradleTasks.Count -eq 0) {
        throw 'Task verification requires explicit focused -GradleTasks; a profile is not an automatic task run.'
    }
    foreach ($name in $GradleTasks) {
        if ($name -notmatch '^:?[A-Za-z][A-Za-z0-9_.-]*(?::[A-Za-z][A-Za-z0-9_.-]*)*$') {
            throw "Invalid Gradle task name: $name"
        }
    }
    if ($TestFilter.Count -gt 0 -and
        (@($GradleTasks | Where-Object { $_ -match '(?:^|:)(?:test|sensorTest)$' }).Count -eq 0 -or
         @($TestFilter | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0)) {
        throw 'Test filters must be nonblank and accompany a test or sensorTest task.'
    }
    if ($Scope -eq 'Task' -and [string]::IsNullOrWhiteSpace($RiskReason)) {
        $focusedFilter = $TestFilter.Count -gt 0 -and '*' -notin $TestFilter
        $broad = @($GradleTasks | Where-Object {
            ($_ -match '(?:^|:)(?:sensor.*|check|build|runGameTestServer|spotbugs.*|checkstyle.*|cpdCheck|analyzeRawMainSpotBugs|verifyMainSpotBugsDispositions|dependencyCheckAnalyze)$' -and
                -not ($_ -match '(?:^|:)sensorTest$' -and $focusedFilter)) -or
            ($_ -match '(?:^|:)test$' -and -not $focusedFilter)
        })
        if ($broad.Count -gt 0) { throw "Earlier broad checks require -RiskReason: $($broad -join ', ')" }
    }

    $ReportPath = [IO.Path]::GetFullPath($ReportPath, $rootPath)
    $rawDirectory = Join-Path (Split-Path -Parent $ReportPath) "$([IO.Path]::GetFileNameWithoutExtension($ReportPath)).raw"
    $runs = @(Invoke-GradleSensor -Root $rootPath -GradleTask @($GradleTasks | Select-Object -Unique) `
        -TestFilter $TestFilter -GradleArguments $arguments -RawOutputPath (Join-Path $rawDirectory 'checks.txt'))
    $deferred = @()
    if ($Scope -eq 'Task') {
        # Preserve actual emitted markers; markerless focused tests still have a process result.
        $measuredProfile = [pscustomobject]@{ GradleTasks = $GradleTasks; SensorIds = @($runs[0].markers.Keys) }
        $records = @(Get-TaskSensorRecords -Profile $measuredProfile -Runs $runs -Root $rootPath)
        if ($records.Count -eq 0) {
            $records += [ordered]@{
                id = 'selected_checks'; required = $true; status = $runs[0].status
                command = ($GradleTasks + $TestFilter) -join ' '; summary = "Selected check process exited $($runs[0].exit_code)."
                artifact = $runs[0].artifact; new_findings = $null
            }
        }
        $deferred = @($selectedProfile.SensorIds | Where-Object { $_ -notin @($records.id) } | ForEach-Object {
            [ordered]@{ id = $_; status = 'PENDING'; reason = 'Not measured by this task run; required at the milestone gate.' }
        })
    } else {
        $records = @(Get-TaskSensorRecords -Profile $selectedProfile -Runs $runs -Root $rootPath)

    }
    $failures = @($records | Where-Object { $_.required -and $_.status -in @('FAIL', 'UNVERIFIED') } | ForEach-Object {
        [ordered]@{
            id = "sensor-$($_.id)"; sensor = $_.id
            classification = if ($_.status -eq 'UNVERIFIED') { 'ENVIRONMENT_FAILURE' } else { 'LOCAL_DEFECT' }
            expected = 'PASS'; observed = $_.summary; reproduction = $_.command
        }
    })
    if ($runs[0].exit_code -ne 0 -and @($records | Where-Object { $_.status -in @('FAIL', 'UNVERIFIED') }).Count -eq 0) {
        $failures += [ordered]@{
            id = 'gradle-process'; classification = 'HARNESS_OR_SENSOR_DEFECT'
            expected = 'Successful process exit'; observed = "Gradle exited $($runs[0].exit_code); successful markers cannot hide process failure."
            reproduction = $runs[0].task
        }
    }
    if ($Scope -eq 'Milestone' -and (Get-TaskCandidateLabel -Root $rootPath) -ne $candidate) {
        $failures += [ordered]@{
            id = 'candidate-changed'; classification = 'HARNESS_OR_SENSOR_DEFECT'
            expected = 'The clean candidate remains unchanged by verification.'
            observed = 'Tracked or untracked candidate files changed during the run; keep generated evidence in ignored output paths.'
        }
    }
    # This criterion measures only execution, not all product acceptance criteria.
    $acceptance = @([ordered]@{
        id = 'selected-check-execution'; status = $runs[0].status
        evidence = "Selected checks exited $($runs[0].exit_code); raw=$($runs[0].artifact). Product/task criteria still require controller review."
    })
    $report = New-VerificationReport -Root $rootPath -TaskId $TaskId -Candidate $candidate -Baseline UNACCEPTED `
        -FreshContext $FreshContext -Sensors $records -Acceptance $acceptance -Failures $failures `
        -Scope $Scope -DeferredChecks $deferred -Warnings @('Measurement only: no task completion, milestone acceptance, baseline or STATE mutation.')
    $report['risk_reason'] = $RiskReason
    $writtenPath = Write-VerificationReport -Report $report -Path $ReportPath
    $validation = Test-VerificationReport -ReportPath $writtenPath -Root $rootPath
    if (-not $validation.Valid) {
        throw "Verification report failed validation: $($validation.Errors -join '; ')"
    }
    return [pscustomobject]@{
        Task = $task; Profile = $selectedProfile; Report = $report; ReportPath = $writtenPath
        Validation = $validation; Runs = $runs; ExitCode = Get-VerificationExitCode -Report $report
    }
}
Export-ModuleMember -Function Get-SourceFingerprint, Get-NormalizedVerdict, New-VerificationReport, Get-VerificationExitCode, Write-VerificationReport, Test-VerificationReport, Resolve-WorkflowTask, Resolve-WorkflowProfile, Get-GradleSensorMarkers, Invoke-GradleSensor, Get-TaskCandidateLabel, Get-TaskSensorRecords, Invoke-VerificationController

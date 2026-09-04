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
        [string] $SourceFingerprint
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

    if ([string]::IsNullOrWhiteSpace($TaskId) -or $TaskId -notmatch '^(?:BOOTSTRAP|M[1-6]\.[1-5])$') {
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
    return [pscustomobject]@{ Name = $Profile; GradleTasks = $gradleTasks; SensorIds = $sensorIds; ConfigPath = (Resolve-Path -LiteralPath $ConfigPath).Path }
}

function Get-GradleSensorMarkers {
    param([Parameter(Mandatory)] [string] $Output)

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
        [Parameter(Mandatory)] [string] $GradleTask,
        [Parameter(Mandatory)] [string] $RawOutputPath
    )

    $wrapper = Join-Path $Root 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
        throw "Gradle wrapper is not available: $wrapper"
    }
    $parent = Split-Path -Parent $RawOutputPath
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    Push-Location $Root
    try {
        $output = (& $wrapper $GradleTask '--no-daemon' '--continue' '--console=plain' 2>&1 | Out-String)
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { [int] $LASTEXITCODE }
    } finally {
        Pop-Location
    }
    Set-Content -LiteralPath $RawOutputPath -Value $output -Encoding utf8NoBOM
    $markers = Get-GradleSensorMarkers -Output $output
    $status = if ($exitCode -eq 0) { 'PASS' } elseif ($output -match '(?i)\bUNVERIFIED\b') { 'UNVERIFIED' } else { 'FAIL' }
    return [pscustomobject]@{
        task = $GradleTask
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
    if ([string]::IsNullOrWhiteSpace($head)) {
        return 'working-tree'
    }
    & git -C $Root diff --quiet 2>$null
    if ($LASTEXITCODE -eq 0) {
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
        if ([string] $sensorId -eq 'product_source_hashes') {
            continue
        }
        $marker = if ($markers.ContainsKey([string] $sensorId)) { $markers[[string] $sensorId] } else { $null }
        $taskName = if ($taskBySensor.ContainsKey([string] $sensorId)) { $taskBySensor[[string] $sensorId] } else { [string] $sensorId }
        $underlyingFailed = switch ([string] $sensorId) {
            'compile' { @($Runs | Where-Object { $_.output -match '(?m):compile(?:Java|TestJava|SensorTestJava) FAILED' }).Count -gt 0 }
            'unit_tests' { @($Runs | Where-Object { $_.output -match '(?m):test FAILED' }).Count -gt 0 }
            'checkstyle' { @($Runs | Where-Object { $_.output -match '(?m):checkstyle(?:Main|Test|SensorTest) FAILED' }).Count -gt 0 }
            'error_prone' { @($Runs | Where-Object { $_.output -match '(?m):compile(?:Java|TestJava|SensorTestJava) FAILED' -and $_.output -match '(?i)errorprone\.info/bugpattern/' }).Count -gt 0 }
            'spotbugs' { @($Runs | Where-Object { $_.output -match '(?m):spotbugs(?:Main|Test|SensorTest) FAILED' }).Count -gt 0 }
            'archunit' { @($Runs | Where-Object { $_.output -match '(?m):sensor(?:Test|Archunit) FAILED' }).Count -gt 0 }
            'duplication' { @($Runs | Where-Object { $_.output -match '(?m):cpdCheck FAILED' }).Count -gt 0 }
            default { @($Runs | Where-Object { $_.output -match "(?m):$taskName FAILED" }).Count -gt 0 }
        }
        $underlyingFailed = $underlyingFailed -or @($Runs | Where-Object { $_.output -match "(?m):$taskName FAILED" }).Count -gt 0
        # UNVERIFIED tasks intentionally exit nonzero when their measurement is unavailable.
        $failedMeasurement = $underlyingFailed -and ($null -eq $marker -or $marker.status -ne 'UNVERIFIED')
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

function Test-ProductSourceHashes {
    param([Parameter(Mandatory)] [string] $Root)

    $beforePath = Join-Path $Root '.agents/evidence/bootstrap/before.json'
    if (-not (Test-Path -LiteralPath $beforePath -PathType Leaf)) {
        return [pscustomobject]@{ Status = 'UNVERIFIED'; Summary = 'before.json is missing.' }
    }
    $before = Get-Content -LiteralPath $beforePath -Raw | ConvertFrom-Json -Depth 100
    $hashes = Get-ObjectProperty $before 'product_hashes'
    if ($null -eq $hashes) {
        return [pscustomobject]@{ Status = 'UNVERIFIED'; Summary = 'before.json has no product_hashes object.' }
    }
    $missing = @()
    $changed = @()
    $expectedPaths = @($hashes.PSObject.Properties.Name)
    if ($expectedPaths.Count -eq 0) {
        return [pscustomobject]@{ Status = 'UNVERIFIED'; Summary = 'before.json has an empty product inventory.' }
    }
    $sourcePath = Join-Path $Root 'src'
    $actualPaths = @(if (Test-Path -LiteralPath $sourcePath -PathType Container) {
        Get-ChildItem -LiteralPath $sourcePath -File -Recurse -Force -ErrorAction Stop | ForEach-Object {
            [IO.Path]::GetRelativePath($Root, $_.FullName).Replace('\', '/')
        } | Where-Object { $_ -notmatch '^src/sensorTest/' -and $_ -notmatch '(?:^|/)graphify-out/' }
    })
    $extra = @($actualPaths | Where-Object { $expectedPaths -cnotcontains $_ })
    foreach ($entry in $hashes.PSObject.Properties) {
        if (-not (Test-Path -LiteralPath (Join-Path $Root $entry.Name) -PathType Leaf)) {
            $missing += $entry.Name
            continue
        }
        $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $Root $entry.Name)).Hash
        if ($actual.ToUpperInvariant() -ne ([string] $entry.Value).ToUpperInvariant()) {
            $changed += $entry.Name
        }
    }
    if ($changed.Count -gt 0 -or $missing.Count -gt 0 -or $extra.Count -gt 0) {
        return [pscustomobject]@{ Status = 'FAIL'; Summary = "Product inventory mismatch. Missing: [$($missing -join ', ')]; changed: [$($changed -join ', ')]; extra: [$($extra -join ', ')]." }
    }
    $hashCount = @($hashes.PSObject.Properties).Count
    return [pscustomobject]@{ Status = 'PASS'; Summary = "Verified $hashCount product source hashes." }
}

function Invoke-VerificationController {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string] $TaskId,
        [Parameter(Mandatory)] [string] $Profile,
        [Parameter(Mandatory)] [string] $ReportPath,
        [switch] $FreshContext
    )

    $rootPath = (Resolve-Path -LiteralPath $Root).Path
    $task = Resolve-WorkflowTask -Root $rootPath -TaskId $TaskId
    if ($TaskId -ne 'BOOTSTRAP') {
        throw "Controller eligibility refuses '$TaskId'; only the explicitly authorized BOOTSTRAP path is runnable."
    }
    $selectedProfile = Resolve-WorkflowProfile -Root $rootPath -Profile $Profile
    $ReportPath = [IO.Path]::GetFullPath($ReportPath, $rootPath)
    $rawDirectory = Join-Path (Split-Path -Parent $ReportPath) "$([IO.Path]::GetFileNameWithoutExtension($ReportPath)).raw"
    New-Item -ItemType Directory -Path $rawDirectory -Force | Out-Null

    $runs = @()
    foreach ($gradleTask in @($selectedProfile.GradleTasks)) {
        if ([string] $gradleTask -notmatch '^[A-Za-z0-9_.-]+$') {
            throw "Invalid Gradle task name in profile '$Profile': $gradleTask"
        }
        $rawPath = Join-Path $rawDirectory "$TaskId-$Profile-$gradleTask.txt"
        $runs += Invoke-GradleSensor -Root $rootPath -GradleTask ([string] $gradleTask) -RawOutputPath $rawPath
    }

    $records = @(Get-TaskSensorRecords -Profile $selectedProfile -Runs $runs -Root $rootPath)
    $hashResult = Test-ProductSourceHashes -Root $rootPath
    if (@($selectedProfile.SensorIds) -contains 'product_source_hashes') {
        $records += [ordered]@{
            id = 'product_source_hashes'
            required = $true
            status = $hashResult.Status
            command = '.agents/evidence/bootstrap/before.json'
            summary = $hashResult.Summary
            artifact = (Join-Path $rootPath '.agents/evidence/bootstrap/before.json')
            new_findings = $null
        }
    }
    $records += [ordered]@{
        id = 'report_schema'
        required = $true
        status = 'UNVERIFIED'
        command = 'PowerShell Test-Json'
        summary = 'Report schema validation is pending.'
        artifact = (Join-Path $rootPath '.agents/verification/report.schema.json')
        new_findings = $null
    }

    # BOOTSTRAP has no accepted baseline. Captured HEAD identifies the candidate only.
    $baseline = 'UNACCEPTED'

    $acceptance = @(
        [ordered]@{ id = 'AC-1'; status = 'UNVERIFIED'; evidence = 'Task path resolved, but this CLI does not measure graph/source-map grounding.' },
        [ordered]@{ id = 'AC-2'; status = 'UNVERIFIED'; evidence = 'Task inventory comparison was not performed by this small controller.' },
        [ordered]@{ id = 'AC-3'; status = 'UNVERIFIED'; evidence = 'Individual run results are recorded; this CLI does not measure the full entrypoint/execution/unavailable-sensor criterion.' },
        [ordered]@{ id = 'AC-4'; status = 'UNVERIFIED'; evidence = 'This CLI does not verify architecture control behavior or non-vacuity; test-name text is not proof.' },
        [ordered]@{ id = 'AC-5'; status = 'UNVERIFIED'; evidence = 'Schema validation alone does not measure the full controller eligibility, negative-control, exit-code and state-preservation criterion.' },
        [ordered]@{ id = 'AC-6'; status = $hashResult.Status; evidence = $hashResult.Summary },
        [ordered]@{ id = 'AC-7'; status = 'UNVERIFIED'; evidence = 'Role-definition comparison is reserved for independent verification.' },
        [ordered]@{ id = 'AC-8'; status = 'UNVERIFIED'; evidence = 'No accepted baseline exists (UNACCEPTED; accepted_baseline=null). Candidate fingerprint and raw runs are recorded; independent clean-state verification is pending.' }
    )

    $failures = @($records | Where-Object { $_.required -and $_.id -ne 'report_schema' -and $_.status -in @('FAIL', 'UNVERIFIED') } | ForEach-Object {
        $classification = if ($_.id -eq 'archunit' -and $_.status -eq 'FAIL') { 'ARCHITECTURE_VIOLATION' } elseif ($_.status -eq 'UNVERIFIED') { 'ENVIRONMENT_FAILURE' } else { 'LOCAL_DEFECT' }
        [ordered]@{
            id = "sensor-$($_.id)"
            classification = $classification
            sensor = $_.id
            expected = 'PASS'
            observed = $_.summary
            reproduction = $_.command
        }
    })
    foreach ($run in $runs) {
        $runRecords = @(Get-TaskSensorRecords -Profile $selectedProfile -Runs @($run) -Root $rootPath)
        if ($run.exit_code -ne 0 -and @($runRecords | Where-Object { $_.status -in @('FAIL', 'UNVERIFIED') }).Count -eq 0) {
            $failures += [ordered]@{
                id = "gradle-run-$($run.task)"
                classification = 'HARNESS_OR_SENSOR_DEFECT'
                expected = 'Successful process exit or a blocking selected sensor result.'
                observed = "Gradle exited $($run.exit_code) despite nonblocking selected sensor results; successful sensor results are retained, but the run cannot pass. Raw: $($run.artifact)"
                reproduction = $run.task
            }
        }
    }

    $report = New-VerificationReport -Root $rootPath -TaskId $TaskId -Candidate (Get-TaskCandidateLabel -Root $rootPath) -Baseline $baseline -FreshContext $FreshContext -Sensors $records -Acceptance $acceptance -Failures $failures -Warnings @('Controller does not dispatch agents, repair code, commit changes, or alter acceptance state.')
    $writtenPath = Write-VerificationReport -Report $report -Path $ReportPath
    $validation = Test-VerificationReport -ReportPath $writtenPath -Root $rootPath
    $schemaSensor = @($records | Where-Object { $_.id -eq 'report_schema' })[0]
    $schemaAcceptance = @($acceptance | Where-Object { $_.id -eq 'AC-5' })[0]
    if ($validation.Valid) {
        $schemaSensor.status = 'PASS'
        $schemaSensor.summary = 'PowerShell Test-Json accepted the configured report schema.'
    } else {
        $schemaSensor.status = 'FAIL'
        $schemaSensor.summary = ($validation.Errors -join '; ')
        $schemaAcceptance.status = 'FAIL'
        $schemaAcceptance.evidence = ($validation.Errors -join '; ')
        $failures += [ordered]@{
            id = 'report-schema'
            classification = 'HARNESS_OR_SENSOR_DEFECT'
            sensor = 'report_schema'
            expected = 'schema-valid report'
            observed = $schemaSensor.summary
            reproduction = 'PowerShell Test-Json'
        }
    }

    $report = New-VerificationReport -Root $rootPath -TaskId $TaskId -Candidate (Get-TaskCandidateLabel -Root $rootPath) -Baseline $baseline -FreshContext $FreshContext -Sensors $records -Acceptance $acceptance -Failures $failures -Warnings @('Controller does not dispatch agents, repair code, commit changes, or alter acceptance state.')
    $writtenPath = Write-VerificationReport -Report $report -Path $writtenPath
    $validation = Test-VerificationReport -ReportPath $writtenPath -Root $rootPath
    return [pscustomobject]@{
        Task = $task
        Profile = $selectedProfile
        Report = $report
        ReportPath = $writtenPath
        Validation = $validation
        Runs = $runs
        ExitCode = Get-VerificationExitCode -Report $report
    }
}

Export-ModuleMember -Function Get-SourceFingerprint, Get-NormalizedVerdict, New-VerificationReport, Get-VerificationExitCode, Write-VerificationReport, Test-VerificationReport, Resolve-WorkflowTask, Resolve-WorkflowProfile, Get-GradleSensorMarkers, Invoke-GradleSensor, Get-TaskCandidateLabel, Get-TaskSensorRecords, Test-ProductSourceHashes, Invoke-VerificationController

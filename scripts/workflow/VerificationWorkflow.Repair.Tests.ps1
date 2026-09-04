Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'VerificationWorkflow.psm1') -Force
$script:failed = 0
$script:passed = 0
function Check($Name, [scriptblock] $Body) {
    try { & $Body; $script:passed++; Write-Output "PASS: $Name" }
    catch { $script:failed++; Write-Output "FAIL: ${Name}: $($_.Exception.Message)" }
}
function Expect($Condition, $Message) {
    if (-not $Condition) { throw $Message }
}
function Records($Output, $Ids) {
    $run = [pscustomobject]@{ task = 'sensorAll'; exit_code = 1; output = $Output; markers = (Get-GradleSensorMarkers -Output $Output); artifact = 'fixture.log' }
    @(Get-TaskSensorRecords -Profile ([pscustomobject]@{ GradleTasks = @('sensorAll'); SensorIds = $Ids }) -Runs @($run) -Root $testRoot)
}
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "automatone-repair-$([guid]::NewGuid())"
New-Item -ItemType Directory -Path $testRoot | Out-Null
try {
    foreach ($dir in @('src/main', 'src/sensorTest', 'src/graphify-out', '.agents/tasks', '.agents/verification', '.agents/evidence/bootstrap', 'config/verification')) {
        New-Item -ItemType Directory -Path (Join-Path $testRoot $dir) -Force | Out-Null
    }
    $productPath = Join-Path $testRoot 'src/main/product.txt'
    Set-Content $productPath 'original'
    $before = @{ head = 'not-an-accepted-commit'; product_hashes = @{ 'src/main/product.txt' = (Get-FileHash $productPath).Hash } }
    Set-Content (Join-Path $testRoot '.agents/evidence/bootstrap/before.json') ($before | ConvertTo-Json)
    Set-Content (Join-Path $testRoot '.agents/STATE.yaml') 'accepted_baseline: null'
    Set-Content (Join-Path $testRoot '.agents/tasks/BOOTSTRAP.md') '# BOOTSTRAP'
    Copy-Item (Join-Path $PSScriptRoot '../../.agents/verification/report.schema.json') (Join-Path $testRoot '.agents/verification/report.schema.json')
    Set-Content (Join-Path $testRoot 'config/verification/profiles.json') '{"profiles":{"bootstrap":{"gradle_tasks":["sensorAll"],"sensors":["compile","error_prone"]}}}'
    # Mock only the external Gradle process, not controller/report logic.
    $wrapper = Join-Path $testRoot 'gradlew.bat'
    Set-Content $wrapper "@echo off`necho AUTOMATONE_SENSOR id=compile status=PASS summary=compiled`necho AUTOMATONE_SENSOR id=error_prone status=PASS summary=checked`nexit /b 1"

    Check 'H-002 unchanged exact inventory passes' {
        Expect ((Test-ProductSourceHashes $testRoot).Status -eq 'PASS') 'unchanged inventory did not pass'
    }
    Check 'H-002 extra product file fails' {
        $extra = Join-Path $testRoot 'src/main/unauthorized.txt'
        try {
            Set-Content $extra 'extra'
            Expect ((Test-ProductSourceHashes $testRoot).Status -eq 'FAIL') 'extra product file was not FAIL'
        } finally { Remove-Item -LiteralPath $extra -Force }
    }
    Check 'H-002 missing product file fails' {
        try {
            Remove-Item -LiteralPath $productPath
            Expect ((Test-ProductSourceHashes $testRoot).Status -eq 'FAIL') 'missing product file was not FAIL'
        } finally { Set-Content $productPath 'original' }
    }
    Check 'H-002 changed product file fails' {
        try {
            Set-Content $productPath 'changed'
            Expect ((Test-ProductSourceHashes $testRoot).Status -eq 'FAIL') 'changed product file was not FAIL'
        } finally { Set-Content $productPath 'original' }
    }
    Check 'H-002 only approved sensor/graph outputs excluded' {
        Set-Content (Join-Path $testRoot 'src/sensorTest/fixture.java') 'verification'
        Set-Content (Join-Path $testRoot 'src/graphify-out/graph.json') '{}'
        Expect ((Test-ProductSourceHashes $testRoot).Status -eq 'PASS') 'approved verification/generated files affected inventory'
        $extra = Join-Path $testRoot 'src/other-product.txt'
        try {
            Set-Content $extra 'not an approved source set'
            Expect ((Test-ProductSourceHashes $testRoot).Status -eq 'FAIL') 'unknown src directory was excluded'
        } finally { Remove-Item -LiteralPath $extra -Force }
    }
    Check 'H-001 failed task overrides premature PASS marker' {
        $records = @(Records "AUTOMATONE_SENSOR id=compile status=PASS summary=too early`n> Task :compileJava FAILED" @('compile'))
        Expect ($records[0].status -eq 'FAIL') 'premature PASS won over known failed task'
    }
    Check 'H-001 other failure preserves successful compile/ErrorProne' {
        $records = Records "AUTOMATONE_SENSOR id=compile status=PASS summary=compiled`nAUTOMATONE_SENSOR id=error_prone status=PASS summary=checked`n> Task :cpdCheck FAILED" @('compile', 'error_prone', 'duplication')
        Expect ($records[0].status -eq 'PASS' -and $records[1].status -eq 'PASS' -and $records[2].status -eq 'FAIL') 'partial success was not preserved'
    }
    Check 'H-001 explicit unavailable marker stays UNVERIFIED' {
        $records = @(Records "AUTOMATONE_SENSOR id=neoforge_gametest status=UNVERIFIED summary=missing`n> Task :sensorIntegration FAILED" @('neoforge_gametest'))
        Expect ($records[0].status -eq 'UNVERIFIED') 'unavailable measurement was mislabeled'
    }

    $firstPath = Join-Path $testRoot '.agents/evidence/bootstrap/independent/first.json'
    $secondPath = Join-Path $testRoot '.agents/evidence/bootstrap/independent/second.json'
    $stateBefore = Get-Content (Join-Path $testRoot '.agents/STATE.yaml') -Raw
    $first = Invoke-VerificationController -Root $testRoot -TaskId BOOTSTRAP -Profile @('bootstrap') -GradleTasks @('sensorAll') -RiskReason 'fixture broad sensor coverage' -ReportPath $firstPath
    $firstRawHash = (Get-FileHash $first.Runs[0].artifact).Hash
    Check 'H-001 all-selected PASS plus unexplained process failure blocks report' {
        Expect ($first.Report.verdict -eq 'FAIL' -and $first.ExitCode -eq 1) 'failed process yielded green report'
        Expect (@($first.Report.sensors | Where-Object { $_.id -in @('compile', 'error_prone') -and $_.status -eq 'PASS' }).Count -eq 2) 'successful sensor markers were discarded'
        Expect $first.Validation.Valid 'blocking execution report failed validation'
    }
    Check 'H-004 no accepted baseline is invented from HEAD' {
        Expect ($first.Report.baseline -eq 'UNACCEPTED') 'baseline was not UNACCEPTED'
        Expect ($first.Report.Contains('accepted_baseline') -and $null -eq $first.Report.accepted_baseline) 'explicit null accepted_baseline metadata missing'
        Expect (@($first.Report.acceptance | Where-Object { $_.id -match '^AC-' }).Count -eq 0) 'controller fabricated product acceptance stubs'
        Expect ($first.Report.Contains('deferred_checks')) 'deferred_checks metadata missing'
        Expect (@($first.Report.deferred_checks).Count -eq 0) 'emitted sensor checks were incorrectly deferred'
        Expect ((Get-Content (Join-Path $testRoot '.agents/STATE.yaml') -Raw) -ceq $stateBefore) 'controller mutated acceptance state'
    }
    Check 'H-004 report validator rejects null accepted baseline with commit baseline' {
        $inconsistent = $first.Report | ConvertTo-Json -Depth 50 | ConvertFrom-Json
        $inconsistent.baseline = 'not-an-accepted-commit'
        $inconsistentPath = Join-Path (Split-Path $firstPath -Parent) 'inconsistent.json'
        Write-VerificationReport -Report $inconsistent -Path $inconsistentPath | Out-Null
        Expect (-not (Test-VerificationReport -ReportPath $inconsistentPath -Root $testRoot).Valid) 'inconsistent baseline metadata validated'
    }
    Set-Content $wrapper "@echo off`necho AUTOMATONE_SENSOR id=compile status=PASS summary=second run`necho AUTOMATONE_SENSOR id=error_prone status=PASS summary=checked`necho ClientDependentFixture serverCoreMustNotDependOnClientClasses`nexit /b 0"
    $second = Invoke-VerificationController -Root $testRoot -TaskId BOOTSTRAP -Profile @('bootstrap') -GradleTasks @('sensorAll') -RiskReason 'fixture broad sensor coverage' -ReportPath $secondPath
    Check 'I-001 destination and report stem isolate raw outputs' {
        $expected = Join-Path (Split-Path $firstPath -Parent) 'first.raw'
        Expect ((Split-Path $first.Runs[0].artifact -Parent) -eq $expected) 'raw directory was not derived from report destination and stem'
        Expect ($first.Runs[0].artifact -ne $second.Runs[0].artifact) 'distinct reports shared raw artifact'
        Expect ((Get-FileHash $first.Runs[0].artifact).Hash -eq $firstRawHash) 'second report overwrote first raw evidence'
        Expect (-not (Test-Path (Join-Path $testRoot '.agents/evidence/bootstrap/tooling'))) 'controller wrote to builder evidence directory'
    }
    Check 'H-003 provisional PASS remains allowed without acceptance mutation' {
        Expect ((Get-Content (Join-Path $testRoot '.agents/STATE.yaml') -Raw).Trim() -eq 'accepted_baseline: null') 'controller mutated acceptance state'
        $inventory = Test-ProductSourceHashes $testRoot
        Expect ($inventory.Status -eq 'PASS') 'fixture inventory failed'
        # Complete contract for this synthetic task: preserved inventory and unchanged STATE.
        # This is not invented evidence for BOOTSTRAP's graph/runtime/role criteria.
        $fixtureAcceptance = @(
            @{ id = 'fixture-inventory'; status = 'PASS'; evidence = $inventory.Summary },
            @{ id = 'fixture-state'; status = 'PASS'; evidence = 'Read STATE after two controller invocations; exact accepted_baseline: null content is unchanged.' }
        )
        $provisional = New-VerificationReport -Root $testRoot -TaskId FIXTURE -Candidate fixture-dirty -Baseline UNACCEPTED -FreshContext $false -Sensors @(@{ id = 'inventory'; required = $true; status = $inventory.Status }) -Acceptance $fixtureAcceptance -Failures @()
        Expect ($provisional.verdict -eq 'PASS' -and (Get-VerificationExitCode $provisional) -eq 0) 'legitimate complete-evidence provisional pass prohibited'
    }
    Check 'H-008 focused measurements do not claim task or milestone acceptance' {
        Expect ($second.Report.verification_scope -eq 'Task') 'focused measurement was not labeled Task scope'
        Expect ($second.Report.baseline -eq 'UNACCEPTED') 'focused measurement invented a baseline'
        Expect ($null -eq $second.Report.accepted_baseline) 'focused measurement invented an accepted baseline'
        Expect (@($second.Report.acceptance | Where-Object { $_.id -match '^AC-' }).Count -eq 0) 'focused measurement fabricated product acceptance stubs'
        Expect (@($second.Report.deferred_checks).Count -eq 0) 'emitted sensor checks were incorrectly deferred'
        Expect ($second.Report.verdict -eq 'PASS' -and $second.ExitCode -eq 0) 'successful focused measurement did not report its selected checks'
        Expect ((Get-Content (Join-Path $testRoot '.agents/STATE.yaml') -Raw) -ceq $stateBefore) 'focused measurement mutated acceptance state'
    }

    # Acceptance evidence is measured against this fixture's complete contract, not BOOTSTRAP.
    $inventory = Test-ProductSourceHashes $testRoot
    Expect ($inventory.Status -eq 'PASS') 'fixture inventory failed before semantics controls'
    $complete = @(@{ id = 'fixture-inventory'; status = 'PASS'; evidence = $inventory.Summary })
    $passSensors = @(@{ id = 'inventory'; required = $true; status = 'PASS' })
    $cases = @(
        @{ name = 'acceptance FAIL beats all-PASS sensors'; acceptance = @(@{ id = 'F'; status = 'FAIL'; evidence = 'Known fixture violation' }); expected = 'FAIL' },
        @{ name = 'acceptance UNVERIFIED prevents PASS'; acceptance = @(@{ id = 'F'; status = 'UNVERIFIED'; evidence = 'Not measured' }); expected = 'INCOMPLETE' },
        @{ name = 'empty acceptance prevents PASS'; acceptance = @(); expected = 'INCOMPLETE' },
        @{ name = 'omitted acceptance prevents PASS'; omit = $true; expected = 'INCOMPLETE' },
        @{ name = 'null acceptance prevents PASS'; acceptance = $null; expected = 'INCOMPLETE' },
        @{ name = 'unevidenced PASS acceptance is incomplete'; acceptance = @(@{ id = 'F'; status = 'PASS'; evidence = ' ' }); expected = 'INCOMPLETE' },
        @{ name = 'required SKIPPED without applicability proof'; sensors = @(@{ id = 'inventory'; required = $true; status = 'SKIPPED' }); expected = 'INCOMPLETE' },
        @{ name = 'optional SKIPPED remains nonblocking'; sensors = $passSensors + @(@{ id = 'optional'; required = $false; status = 'SKIPPED' }); expected = 'PASS' },
        @{ name = 'complete evidenced dirty provisional PASS'; expected = 'PASS' },
        @{ name = 'required FAIL beats missing acceptance'; sensors = @(@{ id = 'inventory'; required = $true; status = 'FAIL' }); acceptance = @(); expected = 'FAIL' },
        @{ name = 'required FAIL beats UNVERIFIED acceptance'; sensors = @(@{ id = 'inventory'; required = $true; status = 'FAIL' }); acceptance = @(@{ id = 'F'; status = 'UNVERIFIED'; evidence = 'Unknown' }); expected = 'FAIL' },
        @{ name = 'acceptance FAIL beats required UNVERIFIED'; sensors = @(@{ id = 'inventory'; required = $true; status = 'UNVERIFIED' }); acceptance = @(@{ id = 'F'; status = 'FAIL'; evidence = 'Known violation' }); expected = 'FAIL' },
        @{ name = 'acceptance FAIL beats another UNVERIFIED criterion'; acceptance = @(@{ id = 'F'; status = 'FAIL'; evidence = 'Known violation' }, @{ id = 'U'; status = 'UNVERIFIED'; evidence = 'Unknown' }); expected = 'FAIL' }
    )
    foreach ($classification in @('HARNESS_OR_SENSOR_DEFECT', 'SCOPE_VIOLATION', 'ARCHITECTURE_VIOLATION', 'LOCAL_DEFECT', 'REPEATED_FAILURE', 'ASSUMPTION_DISPROVEN', 'ENVIRONMENT_FAILURE')) {
        $cases += @{ name = "failure record $classification with PASS sensors"; failures = @(@{ id = 'failure'; classification = $classification; expected = 'Measurable success'; observed = 'Recorded blocking or unavailable result' }); expected = if ($classification -eq 'ENVIRONMENT_FAILURE') { 'INCOMPLETE' } else { 'FAIL' } }
    }
    $cases += @{ name = 'required FAIL overrides environment classification'; sensors = @(@{ id = 'inventory'; required = $true; status = 'FAIL' }); failures = @(@{ id = 'environment'; classification = 'ENVIRONMENT_FAILURE'; expected = 'Available'; observed = 'Unavailable' }); expected = 'FAIL' }
    foreach ($case in $cases) {
        Check "C2 $($case.name)" {
            $parameters = @{ Root = $testRoot; TaskId = 'FIXTURE'; Candidate = 'fixture-dirty'; Baseline = 'UNACCEPTED'; FreshContext = $false; Sensors = $passSensors; Acceptance = $complete; Failures = @() }
            foreach ($key in @('sensors', 'acceptance', 'failures')) { if ($case.ContainsKey($key)) { $parameters[$key] = $case[$key] } }
            if ($case.ContainsKey('omit')) { $parameters.Remove('Acceptance') }
            $result = New-VerificationReport @parameters
            Expect ($result.verdict -eq $case.expected) "NewReport expected $($case.expected), got $($result.verdict)"
            $expectedExit = @{ PASS = 0; FAIL = 1; INCOMPLETE = 2 }[$case.expected]
            Expect ((Get-VerificationExitCode $result) -eq $expectedExit) 'report exit code disagrees'
            $path = Join-Path $testRoot '.agents/evidence/bootstrap/semantics.json'
            Write-VerificationReport -Report $result -Path $path | Out-Null
            Expect (Test-VerificationReport -ReportPath $path -Root $testRoot).Valid 'matching report verdict failed validation'
            if ($case.expected -ne 'PASS') {
                $result.verdict = 'PASS'
                Write-VerificationReport -Report $result -Path $path | Out-Null
                Expect (-not (Test-VerificationReport -ReportPath $path -Root $testRoot).Valid) 'validator accepted conflicting PASS verdict'
            }
        }
    }
    Check 'C2 sensor-only helper remains usable without acceptance' {
        Expect ((Get-NormalizedVerdict -Sensors $passSensors) -eq 'PASS') 'sensor-only helper acquired report-only acceptance requirement'
    }
    Check 'C2 schema rejects report with missing acceptance property' {
        $result = New-VerificationReport -Root $testRoot -TaskId FIXTURE -Candidate fixture-dirty -Baseline UNACCEPTED -FreshContext $false -Sensors $passSensors -Acceptance $complete -Failures @()
        $result.Remove('acceptance')
        $path = Join-Path $testRoot '.agents/evidence/bootstrap/missing-acceptance.json'
        Write-VerificationReport -Report $result -Path $path | Out-Null
        Expect (-not (Test-VerificationReport -ReportPath $path -Root $testRoot).Valid) 'schema accepted missing acceptance property'
    }
    Check 'C2 actual schema failure blocks report validation' {
        $schemaPath = Join-Path $testRoot '.agents/verification/report.schema.json'
        $originalSchema = Get-Content -LiteralPath $schemaPath -Raw
        try {
            Set-Content -LiteralPath $schemaPath -Value '{"not":{}}'
            $threw = $false
            try {
                Invoke-VerificationController -Root $testRoot -TaskId BOOTSTRAP -Profile @('bootstrap') -GradleTasks @('sensorAll') -RiskReason 'fixture broad sensor coverage' -ReportPath (Join-Path $testRoot '.agents/evidence/bootstrap/schema-failure.json') | Out-Null
            } catch {
                $threw = $true
                Expect ($_.Exception.Message -match 'validation|schema') 'schema failure raised an unrelated error'
            }
            Expect $threw 'schema failure did not block report validation'
        } finally { Set-Content -LiteralPath $schemaPath -Value $originalSchema -NoNewline }
    }
    Write-Output "Repair regression tests: $script:passed passed, $script:failed failed"
    if ($script:failed) { throw 'Repair regression tests failed.' }
} finally {
    # This generated absolute path is confined to a single test-owned temp directory.
    if ((Split-Path $testRoot -Parent) -ne ([IO.Path]::GetTempPath()).TrimEnd('\', '/')) { throw 'Unsafe fixture cleanup path' }
    Remove-Item -LiteralPath $testRoot -Recurse -Force
}

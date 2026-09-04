# Bounded manual mutations in disposable copies; never edit the working candidate.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$fixture = Join-Path ([IO.Path]::GetTempPath()) "automatone-mutants-$([guid]::NewGuid())"
$workflow = Join-Path $fixture 'scripts/workflow'
New-Item -ItemType Directory -Path $workflow -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $fixture '.agents/verification') -Force | Out-Null
try {
    Copy-Item (Join-Path $PSScriptRoot 'VerificationWorkflow.Repair.Tests.ps1') $workflow
    Copy-Item (Join-Path $PSScriptRoot '../../.agents/verification/report.schema.json') (Join-Path $fixture '.agents/verification/report.schema.json')
    $source = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'VerificationWorkflow.psm1') -Raw
    $mutants = @(
        @{ name = 'ignore extra product files'; old = ' -or $extra.Count -gt 0'; new = '' },
        @{ name = 'trust premature marker'; old = "`$status = if (`$failedMeasurement) { 'FAIL' } elseif (`$null -ne `$marker) { `$marker.status }"; new = "`$status = if (`$null -ne `$marker) { `$marker.status } elseif (`$failedMeasurement) { 'FAIL' }" },
        @{ name = 'ignore blocking failure records'; old = "-ne 'ENVIRONMENT_FAILURE'"; new = "-eq 'IGNORED_HARNESS_DEFECT'" },
        @{ name = 'invent accepted baseline'; old = "`$baseline = 'UNACCEPTED'"; new = "`$baseline = 'invented-commit'" },
        @{ name = 'share raw output directory'; old = '$rawDirectory = Join-Path (Split-Path -Parent $ReportPath) "$([IO.Path]::GetFileNameWithoutExtension($ReportPath)).raw"'; new = '$rawDirectory = Join-Path $rootPath ''.agents/evidence/bootstrap/tooling''' },
        @{ name = 'ignore acceptance during normalization'; old = '$checkAcceptance = $PSBoundParameters.ContainsKey(''Acceptance'')'; new = '$checkAcceptance = $false' },
        @{ name = 'allow unproven required skip'; old = "-in @('UNVERIFIED', 'SKIPPED')"; new = "-eq 'UNVERIFIED'" },
        @{ name = 'ignore failed schema criterion'; old = "`$schemaAcceptance.status = 'FAIL'"; new = "`$schemaAcceptance.status = 'UNVERIFIED'" }
    )
    $survivors = 0
    foreach ($mutant in $mutants) {
        if ([regex]::Matches($source, [regex]::Escape($mutant.old)).Count -ne 1) { throw "Mutation target is not unique: $($mutant.name)" }
        Set-Content -LiteralPath (Join-Path $workflow 'VerificationWorkflow.psm1') -Value $source.Replace($mutant.old, $mutant.new)
        $output = (& pwsh -NoProfile -File (Join-Path $workflow 'VerificationWorkflow.Repair.Tests.ps1') 2>&1 | Out-String)
        $code = $LASTEXITCODE
        if ($output -notmatch 'Repair regression tests:') { throw "Mutant did not reach test assertions: $output" }
        Write-Output "mutant=$($mutant.name) exit=$code"
        Write-Output $output
        if ($code -eq 0) { $survivors++ }
    }
    Write-Output "Manual mutations: $($mutants.Count - $survivors)/$($mutants.Count) killed"
    if ($survivors) { throw 'A manual mutant survived.' }
} finally {
    if ((Split-Path $fixture -Parent) -ne ([IO.Path]::GetTempPath()).TrimEnd('\', '/')) { throw 'Unsafe fixture cleanup path' }
    Remove-Item -LiteralPath $fixture -Recurse -Force
}

param(
    [Parameter(Mandatory = $true)]
    [string]$CredentialsPath
)

$ErrorActionPreference = 'Stop'
$taskCredentials = (Resolve-Path -LiteralPath $CredentialsPath).Path
$taskCredentialData = Get-Content -LiteralPath $taskCredentials -Raw | ConvertFrom-Json
if ($taskCredentialData.type -ne 'service_account' -or
    $taskCredentialData.project_id -ne 'linkup-7898e' -or
    -not $taskCredentialData.private_key -or -not $taskCredentialData.client_email) {
    throw 'Use a Firebase Admin service-account JSON for linkup-7898e. The Android google-services.json is not a server credential.'
}
Remove-Variable taskCredentialData

$taskMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($taskMaven) {
    $taskMavenPath = $taskMaven.Source
} else {
    $taskMavenCache = Join-Path $env:USERPROFILE '.m2/wrapper/dists'
    $taskMavenPath = Get-ChildItem -LiteralPath $taskMavenCache -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $taskMavenPath) { throw 'Install Maven and make mvn.cmd available on PATH.' }

$taskPrevious = @{}
foreach ($taskName in @('GOOGLE_APPLICATION_CREDENTIALS', 'LINKUP_PUSH_ENABLED', 'FIREBASE_PROJECT_ID', 'LINKUP_PUSH_PROJECT_ID')) {
    $taskPrevious[$taskName] = [Environment]::GetEnvironmentVariable($taskName, 'Process')
}
Push-Location $PSScriptRoot
try {
    $env:GOOGLE_APPLICATION_CREDENTIALS = $taskCredentials
    $env:LINKUP_PUSH_ENABLED = 'true'
    $env:FIREBASE_PROJECT_ID = 'linkup-7898e'
    $env:LINKUP_PUSH_PROJECT_ID = 'linkup-7898e'
    & $taskMavenPath spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
} finally {
    Pop-Location
    foreach ($taskName in $taskPrevious.Keys) {
        [Environment]::SetEnvironmentVariable($taskName, $taskPrevious[$taskName], 'Process')
    }
}

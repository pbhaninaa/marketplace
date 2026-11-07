# Test + build the MarketPlace API JAR and zip a Docker bundle for Elastic Beanstalk
# (platform: Docker running on 64bit Amazon Linux 2023).
# Output: backend\eb-docker-deploy.zip (gitignored). Usage: .\scripts\aws\package-eb-docker.ps1 [-OutZip path]
param(
  [string]$OutZip = ""
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Backend = Join-Path $RepoRoot "backend"
if (-not (Test-Path (Join-Path $Backend "pom.xml"))) {
  throw "Backend not found at $Backend"
}
Push-Location $Backend
try {
  Write-Host "Running tests and packaging in $Backend ..."
  mvn -B package
  if ($LASTEXITCODE -ne 0) { throw "mvn package failed (exit $LASTEXITCODE)" }

  $Jar = Get-ChildItem "target" -Filter "marketplace-api-*.jar" | Select-Object -First 1
  if (-not $Jar) { throw "target\marketplace-api-*.jar not found" }

  $Stage = Join-Path $Backend "eb-docker-stage"
  if (Test-Path $Stage) { Remove-Item $Stage -Recurse -Force }
  New-Item -ItemType Directory -Path (Join-Path $Stage ".ebextensions") -Force | Out-Null

  Copy-Item $Jar.FullName (Join-Path $Stage "app.jar")
  Copy-Item "eb\Dockerfile" (Join-Path $Stage "Dockerfile")
  Copy-Item "eb\Dockerrun.aws.json" (Join-Path $Stage "Dockerrun.aws.json")
  Copy-Item "eb\.ebextensions\*.config" (Join-Path $Stage ".ebextensions")

  if (-not $OutZip) { $OutZip = Join-Path $Backend "eb-docker-deploy.zip" }
  if (Test-Path $OutZip) { Remove-Item $OutZip -Force }

  # Compress-Archive skips dot-folders when given a wildcard, so add .ebextensions explicitly.
  Compress-Archive -Path (Join-Path $Stage "app.jar"), (Join-Path $Stage "Dockerfile"), (Join-Path $Stage "Dockerrun.aws.json"), (Join-Path $Stage ".ebextensions") -DestinationPath $OutZip -Force
  Remove-Item $Stage -Recurse -Force

  Write-Host ""
  Write-Host "Created: $OutZip"
  Write-Host "Upload in Elastic Beanstalk -> Upload and deploy (platform: Docker on Amazon Linux 2023, t3.small or larger)."
} finally {
  Pop-Location
}

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force target/classes | Out-Null
$sources = @(Get-ChildItem -Recurse src/main/java -Filter *.java | ForEach-Object FullName)
& javac --release 21 -encoding UTF-8 -d target/classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }

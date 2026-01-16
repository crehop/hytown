$ErrorActionPreference = "Stop"

$PROJECT = "C:\Users\Crehop\Desktop\HyCrownDev\town\HyTown"
$CLASSPATH = "$PROJECT\lib\HytaleServer.jar;$PROJECT\lib\HyConomy-1.0.0.jar"

Set-Location $PROJECT

Write-Host "Compiling Java files..."

# Ensure output directory exists
if (!(Test-Path "target\classes")) {
    New-Item -ItemType Directory -Path "target\classes" -Force | Out-Null
}

# Get the list of java files
$sources = Get-ChildItem -Recurse -Path "src\main\java" -Filter "*.java" | ForEach-Object { $_.FullName }

# Run javac
& javac -d "target\classes" -cp $CLASSPATH $sources

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "Copying resources..."
Copy-Item -Recurse -Force "src\main\resources\*" "target\classes\"

Write-Host "Creating JAR..."
Set-Location "target\classes"
& jar -cvf "..\HyTown-1.0.0.jar" .

Write-Host "Done!" -ForegroundColor Green
Set-Location $PROJECT

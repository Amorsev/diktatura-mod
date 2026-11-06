# Local Windows build of the Diktatura mod.
# Finds (or downloads into %LOCALAPPDATA%\diktatura-build) JDK 21 and Gradle 8.12,
# runs "gradle build" and writes the log to build\local-build.log and the result
# (OK / FAIL <code>) to build\local-build.status. Nothing is installed system-wide.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $root 'build'
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
$log = Join-Path $buildDir 'local-build.log'
$status = Join-Path $buildDir 'local-build.status'
Set-Content -Path $status -Value 'RUNNING' -Encoding ascii
Set-Content -Path $log -Value ("Diktatura local build " + (Get-Date -Format s)) -Encoding utf8

function Log($msg) { Add-Content -Path $log -Value $msg -Encoding utf8; Write-Host $msg }

$tools = Join-Path $env:LOCALAPPDATA 'diktatura-build'
New-Item -ItemType Directory -Force -Path $tools | Out-Null

function Get-JavaMajor($javaExe) {
    try {
        $out = & cmd /c "`"$javaExe`" -version 2>&1"
        $line = ($out | Select-Object -First 1).ToString()
        if ($line -match 'version "(\d+)') { return [int]$Matches[1] }
    } catch {}
    return 0
}

try {
    # ---------------------------------------------------------------- JDK 21
    $javaHome = $null
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += (Get-ChildItem -Directory -ErrorAction SilentlyContinue (Join-Path $tools 'jdk21') | ForEach-Object FullName)
    foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu")) {
        $candidates += (Get-ChildItem -Directory -ErrorAction SilentlyContinue $base | ForEach-Object FullName)
    }
    foreach ($c in $candidates) {
        $exe = Join-Path $c 'bin\java.exe'
        if ((Test-Path $exe) -and ((Get-JavaMajor $exe) -ge 21)) { $javaHome = $c; break }
    }
    if (-not $javaHome) {
        Log 'JDK 21 not found, downloading Temurin 21 (portable zip)...'
        $zip = Join-Path $tools 'jdk21.zip'
        Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $zip
        $dest = Join-Path $tools 'jdk21'
        if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
        Expand-Archive -Path $zip -DestinationPath $dest
        Remove-Item $zip
        $javaHome = (Get-ChildItem -Directory $dest | Select-Object -First 1).FullName
    }
    $env:JAVA_HOME = $javaHome
    $env:Path = (Join-Path $javaHome 'bin') + ';' + $env:Path
    Log ("JAVA_HOME = " + $javaHome)

    # ---------------------------------------------------------------- Gradle 8.12
    $gradleHome = Join-Path $tools 'gradle-8.12'
    $gradleBat = Join-Path $gradleHome 'bin\gradle.bat'
    if (-not (Test-Path $gradleBat)) {
        Log 'Gradle 8.12 not found, downloading...'
        $zip = Join-Path $tools 'gradle.zip'
        Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-8.12-bin.zip' -OutFile $zip
        Expand-Archive -Path $zip -DestinationPath $tools -Force
        Remove-Item $zip
    }
    Log ("Gradle = " + $gradleBat)

    # ---------------------------------------------------------------- build
    Push-Location $root
    Log '--- gradle build ---'
    & cmd /c "`"$gradleBat`" build --stacktrace --console=plain >> `"$log`" 2>&1"
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -eq 0) {
        $jar = Get-ChildItem (Join-Path $buildDir 'libs') -Filter '*.jar' | Where-Object { $_.Name -notlike '*-sources.jar' } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        Log ("BUILD OK: " + $jar.FullName)
        Set-Content -Path $status -Value ("OK " + $jar.Name) -Encoding ascii
    } else {
        Log ("BUILD FAILED, exit code " + $code)
        Set-Content -Path $status -Value ("FAIL " + $code) -Encoding ascii
    }
} catch {
    Log ("ERROR: " + $_.Exception.Message)
    Set-Content -Path $status -Value 'FAIL script' -Encoding ascii
}

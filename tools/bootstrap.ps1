# Загрузка JDK 21 (Amazon Corretto) и Maven 3.9.9 в каталог -Tools для bootstrap.bat (Windows).
# Уже скачанное не скачивается заново. Контрольные суммы: JDK — SHA-256 с сайта Corretto, Maven —
# SHA-512, записанная здесь (та же, что в bootstrap.sh для tar.gz, — своя для zip).
param([Parameter(Mandatory = $true)][string]$Tools)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # без полосы прогресса Invoke-WebRequest быстрее в разы
# Windows PowerShell 5.1 по умолчанию может не включать TLS 1.2
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem

$MavenVersion = '3.9.9'
$MavenSha512 = '8beac8d11ef208f1e2a8df0682b9448a9a363d2ad13ca74af43705549e72e74c9378823bf689287801cbbfc2f6ea9596201d19ccacfdfb682ee8a2ff4c4418ba'
$MavenUrl = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
# Corretto для Windows — x64 (на Windows для ARM он работает через эмуляцию)
$JdkFile = 'amazon-corretto-21-x64-windows-jdk.zip'
$JdkUrl = "https://corretto.aws/downloads/latest/$JdkFile"
$JdkSha256Url = "https://corretto.aws/downloads/latest_sha256/$JdkFile"

function Get-File([string]$Url, [string]$Target) {
    for ($i = 1; ; $i++) {
        try {
            Invoke-WebRequest -Uri $Url -OutFile $Target -UseBasicParsing
            return
        } catch {
            if ($i -ge 3) { throw "Не удалось скачать ${Url}: $($_.Exception.Message)" }
            Start-Sleep -Seconds (5 * $i)
        }
    }
}

function Test-Sum([string]$File, [string]$Expected, [string]$Algorithm) {
    $actual = (Get-FileHash -Path $File -Algorithm $Algorithm).Hash.ToLowerInvariant()
    if ($actual -ne $Expected.Trim().ToLowerInvariant()) {
        Remove-Item -Force $File
        throw "Контрольная сумма $File не совпала ($Algorithm`: ожидалась $Expected, получена $actual)"
    }
}

# zip -> каталог: содержимое единственной папки архива
function Expand-Single([string]$Zip, [string]$Target) {
    $tmp = "$Target.part"
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    [System.IO.Compression.ZipFile]::ExtractToDirectory($Zip, $tmp)
    $top = Get-ChildItem -Path $tmp -Directory | Select-Object -First 1
    if (Test-Path $Target) { Remove-Item -Recurse -Force $Target }
    Move-Item -Path $top.FullName -Destination $Target
    Remove-Item -Recurse -Force $tmp
}

try {
    New-Item -ItemType Directory -Force -Path $Tools | Out-Null
    $jdk = Join-Path $Tools 'jdk'
    if (-not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
        Write-Host 'Скачиваю JDK 21 (Amazon Corretto, Windows x64)...'
        $sha = (Invoke-WebRequest -Uri $JdkSha256Url -UseBasicParsing).Content
        if ($sha -is [byte[]]) { $sha = [Text.Encoding]::ASCII.GetString($sha) }
        $zip = Join-Path $Tools 'jdk.zip'
        Get-File $JdkUrl $zip
        Test-Sum $zip $sha 'SHA256'
        Expand-Single $zip $jdk
        Remove-Item -Force $zip
    }
    $maven = Join-Path $Tools 'maven'
    if (-not (Test-Path (Join-Path $maven 'bin\mvn.cmd'))) {
        Write-Host "Скачиваю Maven $MavenVersion..."
        $zip = Join-Path $Tools 'maven.zip'
        Get-File $MavenUrl $zip
        Test-Sum $zip $MavenSha512 'SHA512'
        Expand-Single $zip $maven
        Remove-Item -Force $zip
    }
} catch {
    [Console]::Error.WriteLine("bootstrap: $($_.Exception.Message)")
    exit 1
}

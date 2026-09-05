# Copyright 2026 Netflix, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.

param(
    [string] $JigVersion = "0.13.0",
    [string] $JaVersion,
    [string] $Output
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ConfiguredJavaHome = $env:JAVA_HOME
$JavaProperties = $null
$SourceFromJavaHome = -not [string]::IsNullOrWhiteSpace($ConfiguredJavaHome)
if ($SourceFromJavaHome) {
    $SourceJavaHome = $ConfiguredJavaHome
    $Java = Join-Path $SourceJavaHome "bin\java.exe"
} else {
    $JavaCommand = Get-Command java -CommandType Application -ErrorAction SilentlyContinue
    if ($null -eq $JavaCommand) {
        throw "A JDK 25 or later installation must be available through JAVA_HOME or PATH"
    }
    $Java = $JavaCommand.Source
    $JavaProperties = & $Java -XshowSettings:properties -version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect the Java installation on PATH"
    }
    $SourceJavaHome = $null
    foreach ($Line in $JavaProperties) {
        if ([string] $Line -match "^\s*java\.home = (.+)\s*$") {
            $SourceJavaHome = $Matches[1].Trim()
            break
        }
    }
    if ([string]::IsNullOrWhiteSpace($SourceJavaHome)) {
        throw "Unable to locate the Java installation on PATH"
    }
}
if ($null -eq $JavaProperties) {
    $JavaProperties = & $Java -XshowSettings:properties -version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect the Java installation"
    }
}
$JavaVmName = $null
foreach ($Line in $JavaProperties) {
    if ([string] $Line -match "^\s*java\.vm\.name = (.+)\s*$") {
        $JavaVmName = $Matches[1].Trim()
        break
    }
}
$OpenJ9 = -not [string]::IsNullOrWhiteSpace($JavaVmName) -and
    $JavaVmName -match "(?i)OpenJ9"

$Release = Join-Path $SourceJavaHome "release"
if (-not (Test-Path -PathType Leaf $Java) -or
        -not (Test-Path -PathType Leaf $Release) -or
        -not (Test-Path -PathType Container (Join-Path $SourceJavaHome "jmods")) -or
        -not (Test-Path -PathType Leaf (Join-Path $SourceJavaHome "lib\src.zip"))) {
    throw "Java must be a JDK 25 or later installation with a release file, JMODs, and lib/src.zip"
}
$JavaVersionLine = Get-Content $Release |
    Where-Object { $_ -match "^JAVA_VERSION=" } |
    Select-Object -First 1
$JavaVersion = ([string] $JavaVersionLine -replace "^JAVA_VERSION=", "").Trim('"')
if ($JavaVersion -notmatch "^([0-9]+)([.+-].*)?$") {
    throw "Unable to determine the Java feature version from $JavaVersion"
}
$JavaFeature = [int] $Matches[1]
if ($JavaFeature -lt 25) {
    throw "Java 25 or later is required, found $JavaVersion"
}

if ([string]::IsNullOrWhiteSpace($Output)) {
    $UserHome = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
    $Output = Join-Path $UserHome ".jdks\ja-$JavaFeature"
}
if (Test-Path -LiteralPath $Output) {
    throw "Output path already exists: $Output"
}

if (-not [string]::IsNullOrWhiteSpace($env:JA_BIN_HOME)) {
    $JaBinHome = $env:JA_BIN_HOME
} elseif (-not [string]::IsNullOrWhiteSpace($env:XDG_BIN_HOME)) {
    $JaBinHome = $env:XDG_BIN_HOME
} elseif (-not [string]::IsNullOrWhiteSpace($env:XDG_DATA_HOME)) {
    $JaBinHome = Join-Path (Split-Path -Parent $env:XDG_DATA_HOME) "bin"
} else {
    $UserHome = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
    $JaBinHome = Join-Path $UserHome ".local\bin"
}
$DirectorySeparators = [char[]] @('\', '/')
$NormalizedJaBinHome = $JaBinHome.TrimEnd($DirectorySeparators)
$JaBinOnPath = $false
foreach ($Entry in ($env:Path -split [IO.Path]::PathSeparator)) {
    if ($Entry.Trim().TrimEnd($DirectorySeparators) -ieq $NormalizedJaBinHome) {
        $JaBinOnPath = $true
        break
    }
}

$Work = Join-Path ([IO.Path]::GetTempPath()) "ja-install-$([Guid]::NewGuid())"
New-Item -ItemType Directory -Path $Work | Out-Null
try {
    $JigHome = Join-Path $Work "home"
    New-Item -ItemType Directory -Path $JigHome | Out-Null

    $Jig = Join-Path $Work "com.netflix.tools.jig-$JigVersion.jar"
    Invoke-WebRequest -OutFile $Jig `
        "https://repo.maven.apache.org/maven2/com/netflix/com.netflix.tools.jig/$JigVersion/com.netflix.tools.jig-$JigVersion.jar"

    $JigArguments = @(
        "-Duser.home=$JigHome",
        "--module-path", $Jig,
        "--module", "com.netflix.tools.jig/com.netflix.tools.jig.Jig"
    )
    if ([string]::IsNullOrWhiteSpace($JaVersion)) {
        $JaVersion = & $Java @JigArguments `
            --list-module-versions com.netflix.tools.ja | Select-Object -Last 1
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($JaVersion)) {
            throw "Unable to determine the latest Ja version"
        }
    }

    $JaArguments = Join-Path $Work "ja.args"
    $ResolvedArguments = @(& $Java @JigArguments `
        --module-path $Jig `
        --add-requires "com.netflix.tools.ja@$JaVersion" `
        --args runtime)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to resolve Ja"
    }
    $ResolvedArguments += "--module"
    $ResolvedArguments += "com.netflix.tools.ja/com.netflix.tools.ja.Ja"
    [IO.File]::WriteAllLines(
        $JaArguments,
        [string[]] $ResolvedArguments,
        [Text.UTF8Encoding]::new($false))

    $OutputParent = Split-Path -Parent $Output
    if (-not [string]::IsNullOrEmpty($OutputParent)) {
        New-Item -ItemType Directory -Force -Path $OutputParent | Out-Null
    }

    $LinkArguments = @(
        "com.netflix.tools.ja@$JaVersion",
        "--include-static", "--include-sources",
        "--add-modules", "ALL-MODULE-PATH"
    )
    if (-not $OpenJ9) {
        $LinkArguments += "--generate-cds-archive"
    }
    $LinkArguments += @("--output", $Output)
    & $Java "-Duser.home=$JigHome" "@$JaArguments" link @LinkArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to link the Ja JDK"
    }
    if ($OpenJ9) {
        $LauncherConfiguration = Join-Path $Output "conf\com.netflix.tools.launcher"
        foreach ($Options in Get-ChildItem -Path $LauncherConfiguration -Filter "*.args") {
            if ((Get-Content $Options.FullName) -notcontains "-L-aot=auto") {
                continue
            }
            $CommandName = [IO.Path]::GetFileNameWithoutExtension($Options.Name)
            $Command = Join-Path $Output "bin\$CommandName.exe"
            & $Command "-L-aot=create" "--version" | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw "Unable to warm shared class cache with $CommandName"
            }
        }
    }

    Write-Output "Ja $JaVersion installed in $Output"
    Write-Output ""
    Write-Output "To use Ja in this PowerShell session:"
    Write-Output ""
    $PathEntries = @((Join-Path $Output "bin"))
    if (-not $JaBinOnPath) {
        $PathEntries += $JaBinHome
    }
    $PathPrefix = ($PathEntries -join [IO.Path]::PathSeparator) + [IO.Path]::PathSeparator
    if ($SourceFromJavaHome) {
        $QuotedOutput = "'" + $Output.Replace("'", "''") + "'"
        Write-Output "  `$env:JAVA_HOME = $QuotedOutput"
    }
    $QuotedPathPrefix = "'" + $PathPrefix.Replace("'", "''") + "'"
    Write-Output "  `$env:Path = $QuotedPathPrefix + `$env:Path"
} finally {
    Remove-Item -Recurse -Force $Work
}

$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$stage = Join-Path $PWD ('build/package-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $stage 'classes'
$inputDir = Join-Path $stage 'app'
$image = Join-Path $stage 'image'
New-Item -ItemType Directory -Force $classes,$inputDir,$image,'dist' | Out-Null
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Build command failed ($LASTEXITCODE)" } }
javac --release 21 -encoding UTF-8 -cp 'lib/*' -d $classes src/*.java
Check-Exit
Copy-Item resources/index $classes -Recurse
Copy-Item resources/fonts $classes -Recurse
$icon = Join-Path $stage 'ruby.ico'
javac -cp "$classes;lib/*" -d $stage scripts/MakeIcon.java
Check-Exit
java --class-path "$stage;$classes;lib/*" MakeIcon $icon
Check-Exit
$jars = Get-ChildItem lib/*.jar | Sort-Object Name
Copy-Item $jars.FullName $inputDir
# The manifest writer folds long Class-Path entries when creating the JAR.
$manifest = Join-Path $stage 'MANIFEST.MF'
@("Manifest-Version: 1.0","Main-Class: Ruby",("Class-Path: " + ($jars.Name -join ' ')),"") | Set-Content -Encoding ascii $manifest
jar cfm (Join-Path $inputDir 'ruby.jar') $manifest -C $classes .
Check-Exit
$props = Join-Path $stage 'cli.properties'
'win-console=true' | Set-Content -Encoding ascii $props
jpackage --type app-image --name 'Ruby Transformer' --icon $icon --app-version 1.0.0 --input $inputDir --dest $image --main-jar ruby.jar --main-class Ruby --add-modules 'java.desktop,java.net.http,jdk.crypto.ec,jdk.unsupported,java.logging' --add-launcher "RubyCLI=$props"
Check-Exit
Copy-Item README.md,NOTICE.txt (Join-Path $image 'Ruby Transformer')
tar.exe -a -cf dist/Ruby-Transformer-Windows-x64.zip -C $image 'Ruby Transformer'
Check-Exit
Write-Output (Resolve-Path (Join-Path $image 'Ruby Transformer'))


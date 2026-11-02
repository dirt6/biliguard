param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$ROOT = $PSScriptRoot
$SDK  = "E:\dsh\_android"
$BT   = Join-Path $SDK "bt36\android-16"
$AJ   = Join-Path $SDK "pf\android-35\android.jar"
$BUILD = Join-Path $ROOT "build"

function Step($name) { Write-Host "==> $name" -ForegroundColor Cyan }
function Die($msg) { Write-Host "FAILED: $msg" -ForegroundColor Red; exit 1 }

if ($Clean -and (Test-Path $BUILD)) { Remove-Item $BUILD -Recurse -Force }

New-Item -ItemType Directory -Force -Path $BUILD, `
    (Join-Path $BUILD "gen"), `
    (Join-Path $BUILD "classes"), `
    (Join-Path $BUILD "dex") | Out-Null

Step "1/7 aapt2 compile"
& (Join-Path $BT "aapt2.exe") compile --dir (Join-Path $ROOT "res") -o (Join-Path $BUILD "res.zip")
if ($LASTEXITCODE -ne 0) { Die "aapt2 compile" }

Step "2/7 aapt2 link"
& (Join-Path $BT "aapt2.exe") link `
    -o (Join-Path $BUILD "base.apk") `
    -I $AJ `
    --manifest (Join-Path $ROOT "AndroidManifest.xml") `
    --java (Join-Path $BUILD "gen") `
    --auto-add-overlay `
    -R (Join-Path $BUILD "res.zip")
if ($LASTEXITCODE -ne 0) { Die "aapt2 link" }

Step "3/7 javac"
$srcs = @(Get-ChildItem (Join-Path $ROOT "java") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$srcs += (Join-Path $BUILD "gen\com\biliguard\app\R.java")
& javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -g:none -proc:none `
    -bootclasspath $AJ `
    -d (Join-Path $BUILD "classes") `
    $srcs
if ($LASTEXITCODE -ne 0) { Die "javac" }

Step "4/7 d8"
$classes = @(Get-ChildItem (Join-Path $BUILD "classes") -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& (Join-Path $BT "d8.bat") --lib $AJ --min-api 26 --release --output (Join-Path $BUILD "dex") $classes
if ($LASTEXITCODE -ne 0) { Die "d8" }

Step "5/7 inject classes.dex"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkPath = Join-Path $BUILD "base.apk"
$zip = [System.IO.Compression.ZipFile]::Open($apkPath, 'Update')
try {
    $old = $zip.GetEntry("classes.dex")
    if ($old) { $old.Delete() }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, (Join-Path $BUILD "dex\classes.dex"), "classes.dex", 'Optimal') | Out-Null
} finally { $zip.Dispose() }

Step "6/7 zipalign"
& (Join-Path $BT "zipalign.exe") -f 4 $apkPath (Join-Path $BUILD "aligned.apk")
if ($LASTEXITCODE -ne 0) { Die "zipalign" }

Step "7/7 sign"
$ks = Join-Path $ROOT "debug.keystore"
if (-not (Test-Path $ks)) {
    & keytool -genkeypair -v -keystore $ks -storepass android -keypass android `
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Android Debug,O=Android,C=US" | Out-Null
    if ($LASTEXITCODE -ne 0) { Die "keytool" }
}
$out = Join-Path $ROOT "biliguard-0.1.apk"
& (Join-Path $BT "apksigner.bat") sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out $out (Join-Path $BUILD "aligned.apk")
if ($LASTEXITCODE -ne 0) { Die "apksigner" }

& (Join-Path $BT "apksigner.bat") verify --print-certs $out | Select-Object -First 4

Write-Host ""
Write-Host "OK -> $out" -ForegroundColor Green
Write-Host ("size: {0:N0} bytes" -f (Get-Item $out).Length)

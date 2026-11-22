$jar = 'C:\Users\motoy\curseforge\minecraft\Instances\Bakasan\mods\Mekanism-1.21.1-10.7.19.85.jar'
if (-not (Test-Path $jar)) {
    Write-Host "Mekanism JAR not found at $jar" -ForegroundColor Red
    exit 1
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
function Extract-Entry($entryPath, $outPath) {
    $e = $zip.GetEntry($entryPath)
    if ($e) { [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $outPath, $true) }
}

$tmpModel = Join-Path $PSScriptRoot 'tmp_electric_pump.json'
$tmpBlockstate = Join-Path $PSScriptRoot 'tmp_electric_pump_bs.json'
$tmpPumpTex = Join-Path $PSScriptRoot 'tmp_electric_pump.png'
$tmpInstTex = Join-Path $PSScriptRoot 'tmp_tier_installer.png'

Extract-Entry 'assets/mekanism/models/block/electric_pump.json' $tmpModel
Extract-Entry 'assets/mekanism/blockstates/electric_pump.json' $tmpBlockstate
Extract-Entry 'assets/mekanism/textures/block/models/electric_pump.png' $tmpPumpTex
Extract-Entry 'assets/mekanism/textures/item/ultimate_tier_installer.png' $tmpInstTex
$zip.Dispose()

$baseModel = Get-Content -Raw $tmpModel
$baseBs = if (Test-Path $tmpBlockstate) { Get-Content -Raw $tmpBlockstate } else { $null }
$tiers = @('advanced','elite','ultimate','absolute','supreme','cosmic','infinite')
$customInstallers = @('absolute','supreme','cosmic')
$u = New-Object System.Text.UTF8Encoding($false)

foreach ($t in $tiers) {
    $pId = "${t}_electric_pump"
    $bModel = $baseModel -replace 'mekanism:block/models/electric_pump', "mekmp:block/models/$pId"
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot "src/main/resources/assets/mekmp/models/block/$pId.json"), $bModel, $u)
    if ($baseBs) {
        $bsOut = $baseBs -replace 'mekanism:block/electric_pump', "mekmp:block/$pId"
        [IO.File]::WriteAllText((Join-Path $PSScriptRoot "src/main/resources/assets/mekmp/blockstates/$pId.json"), $bsOut, $u)
    }
    Copy-Item $tmpPumpTex (Join-Path $PSScriptRoot "src/main/resources/assets/mekmp/textures/block/models/$pId.png") -Force
}

foreach ($t in $customInstallers) {
    $iId = "${t}_tier_installer"
    Copy-Item $tmpInstTex (Join-Path $PSScriptRoot "src/main/resources/assets/mekmp/textures/item/$iId.png") -Force
}

Remove-Item -ErrorAction SilentlyContinue $tmpModel, $tmpBlockstate, $tmpPumpTex, $tmpInstTex
Write-Host "Mekanism base models, blockstates & textures copied!" -ForegroundColor Green

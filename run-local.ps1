[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) { throw "No existe .env. Copia .env.example a .env, completa sus valores y ejecuta .\run-local.ps1." }
$previous = @{}
$loaded = @()
$values = @{}
try {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        if ($trimmed -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { throw "Línea inválida en .env: $line" }
        $name, $value = $Matches[1], $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) { $value = $value.Substring(1, $value.Length - 2) }
        $values[$name] = $value
    }
    foreach ($name in @('AUTH_JWT_ISSUER','EVALUATION_QUERY_JWT_PUBLIC_KEY_LOCATION','EVALUATION_QUERY_DB_URL','EVALUATION_QUERY_DB_USERNAME','EVALUATION_QUERY_DB_PASSWORD')) {
        $value = $values[$name]
        if ([string]::IsNullOrWhiteSpace($value) -or $value -match '^<.*>$|^CHANGE_ME$|^TODO$') { throw "Completa la variable $name en .env." }
    }
    foreach ($name in $values.Keys) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process'); [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process'); $loaded += $name }
    $authKey = $values['EVALUATION_QUERY_JWT_PUBLIC_KEY_LOCATION'] -replace '^file:', ''
    if (-not [IO.Path]::IsPathRooted($authKey)) { $authKey = Join-Path $root $authKey }
    if (-not (Test-Path -LiteralPath $authKey -PathType Leaf)) { throw "No se encuentra la clave pública de Auth: $authKey" }
    if ([Environment]::GetEnvironmentVariable('EVALUATION_QUERY_SAGA_HABILITADA', 'Process') -eq 'true') {
        foreach ($name in @('SCORING_SERVICE_JWT_KEY_ID','SCORING_SERVICE_JWT_PUBLIC_KEY_LOCATION')) {
            if ([string]::IsNullOrWhiteSpace($values[$name])) { throw "Con la saga habilitada debes completar $name en .env." }
        }
        $serviceKey = $values['SCORING_SERVICE_JWT_PUBLIC_KEY_LOCATION'] -replace '^file:', ''
        if (-not [IO.Path]::IsPathRooted($serviceKey)) { $serviceKey = Join-Path $root $serviceKey }
        if (-not (Test-Path -LiteralPath $serviceKey -PathType Leaf)) { throw "No se encuentra la clave pública de servicio: $serviceKey" }
    }
    Push-Location $root
    try { & .\mvnw.cmd spring-boot:run; if ($LASTEXITCODE -ne 0) { throw "Maven terminó con código $LASTEXITCODE." } }
    finally { Pop-Location }
}
finally { foreach ($name in $loaded) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') } }

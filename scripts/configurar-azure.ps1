param(
    [string]$FrontendPath = (Join-Path $PSScriptRoot '../../frontend-reservas/frontend-reservas'),
    [string]$RedirectUri = 'http://localhost:4200',
    [string]$CaBundle = $env:REQUESTS_CA_BUNDLE
)
$ErrorActionPreference = 'Stop'
if ($CaBundle) { $env:REQUESTS_CA_BUNDLE = $CaBundle }
function Invoke-AzJson([string[]]$Arguments) {
    $result = & az @Arguments --only-show-errors -o json
    if ($LASTEXITCODE -ne 0) { throw 'Azure CLI no pudo completar la operación. Revisa sesión y permisos.' }
    if ($result) { return ($result | Out-String | ConvertFrom-Json) }
}
function Send-Graph([string]$Method, [string]$Uri, $Body) {
    $taskJsonPath = Join-Path ([IO.Path]::GetTempPath()) ('reservas-azure-' + [guid]::NewGuid() + '.json')
    try {
        $Body | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $taskJsonPath -Encoding utf8
        return Invoke-AzJson @('rest', '--method', $Method, '--uri', $Uri, '--headers', 'Content-Type=application/json', '--body', ('@' + $taskJsonPath))
    } finally { Remove-Item -LiteralPath $taskJsonPath -ErrorAction SilentlyContinue }
}
$account = Invoke-AzJson @('account', 'show')
$apiApps = @(Invoke-AzJson @('ad', 'app', 'list', '--display-name', 'ReservasApp-API'))
if ($apiApps.Count -gt 1) { throw 'Hay varios registros ReservasApp-API. Resuelve la ambigüedad antes de continuar.' }
if ($apiApps.Count -eq 0) {
    $scopeId = [guid]::NewGuid().ToString()
    $apiApp = Send-Graph 'POST' 'https://graph.microsoft.com/v1.0/applications' @{
        displayName = 'ReservasApp-API'; signInAudience = 'AzureADMyOrg'
        api = @{
            requestedAccessTokenVersion = 2
            oauth2PermissionScopes = @(@{
                id = $scopeId; value = 'access_as_user'; type = 'User'; isEnabled = $true
                adminConsentDisplayName = 'Acceder al sistema de reservas'
                adminConsentDescription = 'Permite usar las APIs de reservas con la identidad del usuario.'
                userConsentDisplayName = 'Acceder al sistema de reservas'
                userConsentDescription = 'Permite usar las APIs de reservas con tu identidad.'
            })
        }
    }
    Send-Graph 'PATCH' ('https://graph.microsoft.com/v1.0/applications/' + $apiApp.id) @{identifierUris = @('api://' + $apiApp.appId)} | Out-Null
} else {
    $apiApp = $apiApps[0]
    $scope = @($apiApp.api.oauth2PermissionScopes | Where-Object { $_.value -eq 'access_as_user' -and $_.isEnabled })
    if ($scope.Count -ne 1) { throw 'El registro API existente no tiene un scope access_as_user habilitado.' }
    $scopeId = $scope[0].id
    if ($apiApp.api.requestedAccessTokenVersion -ne 2) { throw 'El registro API existente debe emitir access tokens v2.' }
}
$spaApps = @(Invoke-AzJson @('ad', 'app', 'list', '--display-name', 'ReservasApp-SPA'))
if ($spaApps.Count -gt 1) { throw 'Hay varios registros ReservasApp-SPA.' }
if ($spaApps.Count -eq 0) {
    $spaApp = Send-Graph 'POST' 'https://graph.microsoft.com/v1.0/applications' @{
        displayName = 'ReservasApp-SPA'; signInAudience = 'AzureADMyOrg'
        spa = @{redirectUris = @($RedirectUri)}
        requiredResourceAccess = @(@{resourceAppId = $apiApp.appId; resourceAccess = @(@{id = $scopeId; type = 'Scope'})})
    }
} else {
    $spaApp = $spaApps[0]
    if ($RedirectUri -notin $spaApp.spa.redirectUris) { throw 'El registro SPA existente no contiene el redirect URI solicitado.' }
}
# Preautorizar únicamente esta SPA para el scope de la API; no se crean secretos.
$apiCurrent = Invoke-AzJson @('ad', 'app', 'show', '--id', $apiApp.appId)
$preauthorized = @($apiCurrent.api.preAuthorizedApplications | Where-Object { $_.appId -ne $spaApp.appId })
$preauthorized += @{appId = $spaApp.appId; delegatedPermissionIds = @($scopeId)}
Send-Graph 'PATCH' ('https://graph.microsoft.com/v1.0/applications/' + $apiApp.id) @{
    api = @{requestedAccessTokenVersion = 2; oauth2PermissionScopes = $apiCurrent.api.oauth2PermissionScopes; preAuthorizedApplications = $preauthorized}
} | Out-Null
foreach ($clientId in @($apiApp.appId, $spaApp.appId)) {
    $sp = @(Invoke-AzJson @('ad', 'sp', 'list', '--filter', "appId eq '$clientId'"))
    if ($sp.Count -eq 0) { Invoke-AzJson @('ad', 'sp', 'create', '--id', $clientId) | Out-Null }
}
foreach ($file in @('environment.ts', 'environment.prod.ts')) {
    $taskEnvironmentPath = Join-Path $FrontendPath ('src/environments/' + $file)
    $content = [IO.File]::ReadAllText($taskEnvironmentPath)
    # Reemplazar también IDs ya configurados: un clon debe admitir otro tenant.
    $content = [regex]::Replace($content, "(?m)(\bclientId:\s*)'[^']*'", {
        param($match)
        $match.Groups[1].Value + "'" + $spaApp.appId + "'"
    })
    $content = [regex]::Replace($content, 'https://login\.microsoftonline\.com/[^'']+', ('https://login.microsoftonline.com/' + $account.tenantId))
    $content = [regex]::Replace($content, 'api://[^''/]+/access_as_user', ('api://' + $apiApp.appId + '/access_as_user'))
    [IO.File]::WriteAllText($taskEnvironmentPath, $content)
}
$taskEnvPath = Join-Path $PSScriptRoot '../.env'
$lines = if (Test-Path -LiteralPath $taskEnvPath) { @(Get-Content -LiteralPath $taskEnvPath) } else { @('SPRING_PROFILES_ACTIVE=local') }
$lines = @($lines | Where-Object { $_ -notmatch '^AZURE_(TENANT_ID|CLIENT_ID)=' })
$lines += @(('AZURE_TENANT_ID=' + $account.tenantId), ('AZURE_CLIENT_ID=' + $apiApp.appId))
$lines | Set-Content -LiteralPath $taskEnvPath -Encoding utf8
Write-Output ('Azure configurado. Tenant: ' + $account.tenantId + '; API: ' + $apiApp.appId + '; SPA: ' + $spaApp.appId)

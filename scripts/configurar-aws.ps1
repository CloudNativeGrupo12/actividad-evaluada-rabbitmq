param(
    [string]$Region = 'us-east-1',
    [string]$Profile = 'default',
    [string]$InstanceId = 'ep3-reservas-postgres',
    [ValidatePattern('^(?:\d{1,3}\.){3}\d{1,3}/32$')][string]$ClientCidr,
    [switch]$SoloConsultar
)
$ErrorActionPreference = 'Stop'
$env:AWS_PAGER = ''
function Invoke-AwsJson([string[]]$Arguments) {
    if (Get-Command uv -ErrorAction SilentlyContinue) {
        $result = & uv tool run --from awscli python -m awscli @Arguments --region $Region --profile $Profile --output json
    } else { $result = & aws @Arguments --region $Region --profile $Profile --output json }
    if ($LASTEXITCODE -ne 0) { throw 'AWS CLI falló. Revisa credenciales, aws_session_token y permisos antes de continuar.' }
    if ($result) { return ($result | Out-String | ConvertFrom-Json) }
}
# Ningún recurso se modifica si las credenciales no son válidas.
$identity = Invoke-AwsJson @('sts', 'get-caller-identity')
$instances = Invoke-AwsJson @('rds', 'describe-db-instances')
$db = @($instances.DBInstances | Where-Object { $_.DBInstanceIdentifier -eq $InstanceId })
Write-Output ('Cuenta AWS: ' + $identity.Account + '; región: ' + $Region + '; RDS: ' + $InstanceId)
if ($SoloConsultar) {
    $db | Select-Object DBInstanceIdentifier, DBInstanceStatus, Engine, Endpoint
    return
}
$taskEnvPath = Join-Path $PSScriptRoot '../.env'
if (-not (Test-Path -LiteralPath $taskEnvPath)) { throw 'Ejecuta configurar-azure.ps1 primero para crear el .env local.' }
$lines = @(Get-Content -LiteralPath $taskEnvPath)
$passwordLine = $lines | Where-Object { $_ -match '^DB_PASSWORD=' } | Select-Object -First 1
if ($db.Count -eq 0) {
    if (-not $ClientCidr) {
        $clientIp = (Invoke-RestMethod -Uri 'https://checkip.amazonaws.com').Trim()
        [void][IPAddress]::Parse($clientIp)
        $ClientCidr = $clientIp + '/32'
    }
    $vpcs = Invoke-AwsJson @('ec2', 'describe-vpcs', '--filters', 'Name=is-default,Values=true')
    if (@($vpcs.Vpcs).Count -ne 1) { throw 'No hay una VPC por defecto única; define la red del laboratorio antes de crear RDS.' }
    $vpcId = $vpcs.Vpcs[0].VpcId
    $subnets = Invoke-AwsJson @('ec2', 'describe-subnets', '--filters', ('Name=vpc-id,Values=' + $vpcId), 'Name=default-for-az,Values=true')
    if (@($subnets.Subnets | Select-Object -ExpandProperty AvailabilityZone -Unique).Count -lt 2) { throw 'RDS necesita subredes en al menos dos zonas.' }
    $groupName = $InstanceId + '-subnets'
    $groups = Invoke-AwsJson @('rds', 'describe-db-subnet-groups')
    if ($groupName -notin $groups.DBSubnetGroups.DBSubnetGroupName) {
        Invoke-AwsJson (@('rds', 'create-db-subnet-group', '--db-subnet-group-name', $groupName,
            '--db-subnet-group-description', 'Red EP3 reservas', '--subnet-ids') + @($subnets.Subnets.SubnetId)) | Out-Null
    }
    $sgName = $InstanceId + '-client'
    $groups = Invoke-AwsJson @('ec2', 'describe-security-groups', '--filters', ('Name=group-name,Values=' + $sgName), ('Name=vpc-id,Values=' + $vpcId))
    if (@($groups.SecurityGroups).Count -eq 0) {
        $sg = Invoke-AwsJson @('ec2', 'create-security-group', '--group-name', $sgName, '--description', 'PostgreSQL EP3 desde IP de demostracion', '--vpc-id', $vpcId)
        $sgId = $sg.GroupId
        Invoke-AwsJson @('ec2', 'authorize-security-group-ingress', '--group-id', $sgId, '--protocol', 'tcp', '--port', '5432', '--cidr', $ClientCidr) | Out-Null
    } else {
        $sgId = $groups.SecurityGroups[0].GroupId
        $ranges = @($groups.SecurityGroups[0].IpPermissions | Where-Object { $_.IpProtocol -eq 'tcp' -and $_.FromPort -eq 5432 -and $_.ToPort -eq 5432 } | ForEach-Object { $_.IpRanges.CidrIp })
        if ($ClientCidr -notin $ranges) { throw 'El security group existente no permite esta IP. Revísalo antes de reutilizarlo.' }
    }
    # Instancia de laboratorio: single AZ, 20 GiB, cifrada. Genera cargos mientras exista.
    $password = if ($passwordLine) { $passwordLine.Substring('DB_PASSWORD='.Length) } else {
        [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    }
    $lines = @($lines | Where-Object { $_ -notmatch '^DB_(USER|PASSWORD)=' })
    $lines += @('DB_USER=reservas_admin', ('DB_PASSWORD=' + $password))
    $lines | Set-Content -LiteralPath $taskEnvPath -Encoding utf8
    $request = @{
        DBInstanceIdentifier = $InstanceId; DBName = 'reservas'; DBInstanceClass = 'db.t3.micro'
        Engine = 'postgres'; AllocatedStorage = 20; StorageType = 'gp3'; StorageEncrypted = $true
        MasterUsername = 'reservas_admin'; MasterUserPassword = $password
        VpcSecurityGroupIds = @($sgId); DBSubnetGroupName = $groupName; PubliclyAccessible = $true
        MultiAZ = $false; BackupRetentionPeriod = 1; AutoMinorVersionUpgrade = $true
        Tags = @(@{Key = 'Proyecto'; Value = 'EP3-DSY1107'})
    }
    # El secreto se transmite mediante archivo local y no aparece en argumentos ni salida.
    $taskRequestPath = Join-Path $PSScriptRoot '../.aws-rds-request.json'
    try {
        $request | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $taskRequestPath -Encoding utf8
        Invoke-AwsJson @('rds', 'create-db-instance', '--cli-input-json', ('file://' + $taskRequestPath)) | Out-Null
    } finally { Remove-Item -LiteralPath $taskRequestPath -ErrorAction SilentlyContinue }
    Write-Output 'RDS en creación. Ejecuta de nuevo el script cuando la instancia esté available.'
    return
}
if ($db[0].DBInstanceStatus -ne 'available') { Write-Output ('Estado RDS: ' + $db[0].DBInstanceStatus + '. Repite cuando esté available.'); return }
if (-not $passwordLine) { throw 'La instancia existe pero falta DB_PASSWORD en .env; no se cambia su contraseña automáticamente.' }
$endpoint = $db[0].Endpoint.Address
$lines = @($lines | Where-Object { $_ -notmatch '^(RESERVAS|DISPONIBILIDAD|NOTIFICACIONES|AUDITORIA)_DB_URL=' })
foreach ($database in @('reservas', 'disponibilidad', 'notificaciones', 'auditoria')) {
    $lines += $database.ToUpperInvariant() + '_DB_URL=jdbc:postgresql://' + $endpoint + ':5432/' + $database + '?sslmode=require'
}
$lines | Set-Content -LiteralPath $taskEnvPath -Encoding utf8
Write-Output ('RDS disponible: ' + $endpoint + '. Inicializa las cuatro bases con inicializar-postgres.py antes de activar cloud.')

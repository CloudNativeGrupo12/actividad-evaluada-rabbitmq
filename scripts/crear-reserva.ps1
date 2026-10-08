param(
    [datetime]$Fecha = (Get-Date).AddDays(7),
    [string]$HoraInicio = '18:00',
    [string]$HoraFin = '20:00',
    [ValidateRange(1, 8)][int]$Personas = 4,
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$AccessToken = $env:RESERVAS_ACCESS_TOKEN
)

$ErrorActionPreference = 'Stop'
if (-not $AccessToken) { throw 'Define RESERVAS_ACCESS_TOKEN con el access token de la SPA para access_as_user.' }
$solicitud = @{
    clienteId = 'demo-' + [guid]::NewGuid().ToString()
    emailCliente = 'cliente@example.com'
    fechaReserva = $Fecha.ToString('yyyy-MM-dd')
    horaInicio = $HoraInicio
    horaFin = $HoraFin
    cantidadPersonas = $Personas
} | ConvertTo-Json

$reserva = Invoke-RestMethod -Method Post -Uri ($BaseUrl.TrimEnd('/') + '/reservas') -Headers @{Authorization = 'Bearer ' + $AccessToken} -ContentType 'application/json' -Body $solicitud -TimeoutSec 20
$reserva | ConvertTo-Json -Depth 5

if ($reserva.publicacion -eq 'PENDIENTE') {
    Write-Warning ('Reserva confirmada con evento pendiente. Reintentar con POST /reservas/' + $reserva.reservaId + '/publicar')
}

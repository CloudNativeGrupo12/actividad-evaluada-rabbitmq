"""Publica un JSON inválido y verifica ambas rutas hacia DLQ, sin consumir mensajes.
Ejecutar con RabbitMQ y los dos consumidores activos: python scripts/verificar-dlq.py
"""
import base64
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request
import uuid

root = Path(__file__).resolve().parents[1]
output = root / 'evidencias' / 'ep3'
output.mkdir(parents=True, exist_ok=True)
auth = 'Basic ' + base64.b64encode((os.environ.get('RABBIT_USER', 'reservas') + ':' +
                                  os.environ.get('RABBIT_PASSWORD', 'reservas-demo')).encode()).decode()
def request(path, data=None):
    req = urllib.request.Request('http://localhost:15672/api/' + path,
        data=json.dumps(data).encode() if data is not None else None,
        headers={'Authorization': auth, 'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=10) as response:
        return json.load(response)

before = request('queues/%2F/reservas.dlq').get('messages_ready', 0)
message_id = 'ep3-dlq-' + str(uuid.uuid4())
assert request('exchanges/%2F/reservas.exchange/publish', {
    'properties': {'delivery_mode': 2, 'content_type': 'application/json', 'message_id': message_id},
    'routing_key': 'reserva.confirmada', 'payload': '{json invalido', 'payload_encoding': 'string'
})['routed'], 'El exchange no tiene bindings para el evento'
deadline = time.monotonic() + 45
while time.monotonic() < deadline:
    queue = request('queues/%2F/reservas.dlq')
    if queue.get('messages_ready', 0) >= before + 2:
        logs = subprocess.run(['docker', 'compose', 'logs', '--no-color', '--tail', '100',
            'ms-notificaciones', 'ms-auditoria'], cwd=root, capture_output=True, text=True, check=True).stdout
        lines = [line for line in logs.splitlines() if message_id in line and 'DLQ_REDIRECT' in line]
        if len(lines) >= 2:
            (output / 'dlq.json').write_text(json.dumps({'messageId': message_id, 'mensajesAntes': before,
                'mensajesDespues': queue['messages_ready'], 'resultado': 'OK'}, indent=2), encoding='utf-8')
            (output / 'dlq-logs.txt').write_text('\n'.join(lines), encoding='utf-8')
            print('DLQ verificada: ambas colas rechazan el JSON inválido y registran su redirección.')
            break
    time.sleep(1)
else:
    raise SystemExit('No se verificaron los dos mensajes y sus logs en DLQ dentro de 45 segundos.')

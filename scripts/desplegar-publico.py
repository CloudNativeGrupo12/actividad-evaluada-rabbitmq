"""Construye Angular y despliega contenedores en la EC2 de provisionar-publico.py.

Los secretos viajan por SCP y no se incluyen en el archivo de aplicación ni Git.
Ejecutar después de provisionar-publico.py y configurar el redirect de Entra ID.
"""
import argparse
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tarfile
import time

root = Path(__file__).resolve().parents[1]
task_dir = Path.home() / '.codex' / 'tmp' / 'ep3-publico'
state_path = task_dir / 'infra.json'
state = json.loads(state_path.read_text(encoding='utf-8'))
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--frontend', type=Path, default=root.parent / 'frontend-reservas' / 'frontend-reservas')
parser.add_argument('--configurar-frontend', action='store_true', help='Solo escribir la configuración pública de Angular')
args = parser.parse_args()
public_url = state['PublicUrl']
if not re.fullmatch(r'https://[a-z0-9]+\.execute-api\.[a-z0-9-]+\.amazonaws\.com', public_url):
    raise SystemExit('URL pública inválida en el estado de infraestructura.')
domains = ['reservas', 'disponibilidad', 'notificaciones', 'auditoria', 'admin']
modules = ['ms-reservas', 'ms-disponibilidad', 'ms-notificaciones', 'ms-auditoria', 'ms-admin-rabbitmq']

content = (args.frontend / 'src/environments/environment.ts').read_text(encoding='utf-8-sig')
content = content.replace('production: false', 'production: true')
content = content.replace('http://localhost:4200', public_url)
for port, domain in zip(range(8080, 8085), domains):
    content = content.replace(f'http://localhost:{port}', public_url + '/backend/' + domain)
if 'localhost' in content:
    raise SystemExit('La configuración pública conserva una referencia a localhost.')
(args.frontend / 'src/environments/environment.prod.ts').write_text(
    '// AWS público: frontend y APIs por API Gateway HTTPS; datos en RDS.\n' + content,
    encoding='utf-8')
print('Angular configurado para ' + public_url, flush=True)
if args.configurar_frontend:
    raise SystemExit(0)

environment = os.environ.copy()
environment['NODE_OPTIONS'] = (environment.get('NODE_OPTIONS', '') + ' --use-system-ca').strip()
print('Construyendo el frontend optimizado...', flush=True)
subprocess.run(['npm.cmd', 'run', 'build'], cwd=args.frontend, env=environment, check=True)
dist = args.frontend / 'dist' / 'frontend-reservas' / 'browser'
archive_path = task_dir / 'app.tar.gz'
with tarfile.open(archive_path, 'w:gz') as archive:
    archive.add(dist, arcname='frontend')
    for name in ('Dockerfile.runtime', 'compose.aws.yaml', 'nginx.conf', 'proxy_params_ep3'):
        archive.add(root / 'deploy' / name, arcname=name)
    ignore_path = task_dir / 'dockerignore'
    ignore_path.write_text('.env\n*.tar.gz\n', encoding='utf-8')
    archive.add(ignore_path, arcname='.dockerignore')
    for module in modules:
        jar = root / module / 'target' / (module + '-1.0.0.jar')
        if not jar.is_file():
            raise SystemExit('Falta compilar ' + module + ' con mvn package.')
        archive.add(jar, arcname='jars/' + module + '.jar')

local_config = dict(line.split('=', 1) for line in (root / '.env').read_text(encoding='utf-8-sig').splitlines()
                    if line and not line.startswith('#') and '=' in line)
required = ['DB_USER', 'DB_PASSWORD', 'AZURE_TENANT_ID', 'AZURE_CLIENT_ID']
required += [x.upper() + '_DB_URL' for x in domains[:4]]
if any(not local_config.get(key) or '\n' in local_config[key] for key in required):
    raise SystemExit('Faltan variables cloud válidas en .env local.')
cloud_env = task_dir / 'cloud.env'
existing = dict(line.split('=', 1) for line in cloud_env.read_text().splitlines() if '=' in line) if cloud_env.exists() else {}
cloud_values = {key: local_config[key] for key in required}
cloud_values.update(SPRING_PROFILES_ACTIVE='cloud', CORS_ALLOWED_ORIGINS=public_url,
                    RABBIT_USER='reservas_cloud', RABBIT_PASSWORD=existing.get('RABBIT_PASSWORD') or secrets.token_hex(24))
cloud_env.write_text('\n'.join(key + '=' + value for key, value in cloud_values.items()) + '\n', encoding='utf-8')

address = str(ipaddress.IPv4Address(state['PublicIp']))
key = state['KeyPath']
ssh_options = ['-i', key, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=accept-new',
               '-o', 'UserKnownHostsFile=' + str(task_dir / 'known_hosts'), '-o', 'ConnectTimeout=10']
destination = 'ubuntu@' + address
print('Esperando Docker en EC2...', flush=True)
deadline = time.monotonic() + 300
while time.monotonic() < deadline:
    ready = subprocess.run(['ssh', *ssh_options, destination,
                            'test -f /home/ubuntu/ep3/docker-ready'], capture_output=True, text=True)
    if ready.returncode == 0:
        break
    time.sleep(5)
else:
    raise SystemExit('EC2 no está lista; revisar cloud-init y acceso SSH desde esta IP.')
print('Transfiriendo aplicación y configuración privada por SCP...', flush=True)
subprocess.run(['scp', '-q', *ssh_options, str(archive_path), destination + ':/home/ubuntu/ep3/app.tar.gz'], check=True)
subprocess.run(['scp', '-q', *ssh_options, str(cloud_env), destination + ':/home/ubuntu/ep3/.env'], check=True)
command = ('set -eu; cd /home/ubuntu/ep3; chmod 600 .env; tar -xzf app.tar.gz; '
           'sudo docker compose -f compose.aws.yaml up -d --build; '
           'sudo docker compose -f compose.aws.yaml exec -T web nginx -t')
print('Levantando ocho contenedores en AWS...', flush=True)
subprocess.run(['ssh', *ssh_options, destination, command], check=True)
print('Contenedores desplegados. Verificar salud pública, login y reserva antes de entregar.', flush=True)

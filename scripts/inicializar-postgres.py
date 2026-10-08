"""Crea las cuatro bases de dominio y activa cloud solo después de comprobarlas.
Ejecutar: uv run --with 'psycopg[binary]' python scripts/inicializar-postgres.py
Lee .env local; no imprime credenciales ni cadenas con contraseña.
"""
from pathlib import Path
from urllib.parse import urlparse
import psycopg
from psycopg import sql

root = Path(__file__).resolve().parents[1]
env_path = root / '.env'
lines = env_path.read_text(encoding='utf-8-sig').splitlines()
config = dict(line.split('=', 1) for line in lines if line and not line.startswith('#') and '=' in line)
endpoint = urlparse(config['RESERVAS_DB_URL'].removeprefix('jdbc:'))
connection = dict(host=endpoint.hostname, port=endpoint.port or 5432,
                  user=config['DB_USER'], password=config['DB_PASSWORD'], sslmode='require', connect_timeout=10)
with psycopg.connect(dbname='postgres', autocommit=True, **connection) as conn:
    for name in ('reservas', 'disponibilidad', 'notificaciones', 'auditoria'):
        if not conn.execute('SELECT 1 FROM pg_database WHERE datname = %s', (name,)).fetchone():
            conn.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(name)))
for name in ('reservas', 'disponibilidad', 'notificaciones', 'auditoria'):
    with psycopg.connect(dbname=name, **connection) as conn:
        assert conn.execute('SELECT current_database()').fetchone()[0] == name
    print(f'Conexión PostgreSQL verificada: {name}')
lines = [line for line in lines if not line.startswith('SPRING_PROFILES_ACTIVE=')]
env_path.write_text('\n'.join(lines + ['SPRING_PROFILES_ACTIVE=cloud']) + '\n', encoding='utf-8')
print('Perfil cloud activado; reinicia los microservicios para cargar PostgreSQL.')

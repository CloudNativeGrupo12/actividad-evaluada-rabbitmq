"""Verifica tablas, TLS y una reserva real en las cuatro bases de RDS.

uv run --with 'psycopg[binary]' python scripts/verificar-postgres.py --reserva-id UUID
Repetir con --despues-reinicio después de reiniciar los microservicios.
Lee secretos de .env sin incluirlos en la salida ni en las evidencias.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
from urllib.parse import urlparse

import psycopg
from psycopg import sql


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reserva-id')
    parser.add_argument('--despues-reinicio', action='store_true')
    parser.add_argument('--archivo-evidencia', help='Nombre .json en evidencias/ep3 para conservar ejecuciones anteriores')
    args = parser.parse_args()
    if args.archivo_evidencia and (Path(args.archivo_evidencia).name != args.archivo_evidencia
                                   or not args.archivo_evidencia.endswith('.json')
                                   or '/' in args.archivo_evidencia or '\\' in args.archivo_evidencia):
        parser.error('--archivo-evidencia debe ser un nombre .json sin directorios')
    root = Path(__file__).resolve().parents[1]
    config = dict(line.split('=', 1) for line in
                  (root / '.env').read_text(encoding='utf-8-sig').splitlines()
                  if line and not line.startswith('#') and '=' in line)
    if config.get('SPRING_PROFILES_ACTIVE') != 'cloud':
        raise SystemExit('Activa cloud con inicializar-postgres.py antes de verificar RDS.')
    expected = {
        'reservas': ('reservas',),
        'disponibilidad': ('mesas', 'asignaciones'),
        'notificaciones': ('procesados',),
        'auditoria': ('procesados',),
    }
    evidence = {'fechaUTC': dt.datetime.now(dt.timezone.utc).isoformat(),
                'perfil': 'cloud', 'reservaId': args.reserva_id,
                'despuesReinicio': args.despues_reinicio, 'bases': {}}
    event_ids = []
    for database, tables in expected.items():
        endpoint = urlparse(config[database.upper() + '_DB_URL'].removeprefix('jdbc:'))
        if endpoint.path != '/' + database:
            raise AssertionError(f'La URL de {database} apunta a otra base.')
        with psycopg.connect(host=endpoint.hostname, port=endpoint.port or 5432,
                             dbname=database, user=config['DB_USER'],
                             password=config['DB_PASSWORD'], sslmode='require',
                             connect_timeout=10) as conn:
            ssl = conn.execute('SELECT ssl, version FROM pg_stat_ssl WHERE pid = pg_backend_pid()').fetchone()
            if not ssl or not ssl[0]:
                raise AssertionError(f'La conexión a {database} no usa TLS.')
            result = {'host': endpoint.hostname, 'tls': ssl[0], 'protocolo': ssl[1],
                      'versionPostgreSQL': conn.execute('SHOW server_version').fetchone()[0],
                      'tablas': {}}
            for table in tables:
                result['tablas'][table] = conn.execute(
                    sql.SQL('SELECT count(*) FROM {}').format(sql.Identifier(table))).fetchone()[0]
            if args.reserva_id:
                if database == 'reservas':
                    row = conn.execute('SELECT evento_id, publicacion FROM reservas WHERE reserva_id = %s',
                                       (args.reserva_id,)).fetchone()
                    if not row or row[1] != 'PUBLICADA':
                        raise AssertionError('La reserva no está publicada en RDS.')
                    result['eventoId'], result['publicacion'] = row
                    event_ids.append(row[0])
                elif database == 'disponibilidad':
                    rows = conn.execute('SELECT mesa_id FROM asignaciones WHERE reserva_id = %s',
                                        (args.reserva_id,)).fetchall()
                    if len(rows) != 1:
                        raise AssertionError('Se esperaba una única asignación en RDS.')
                    result['mesaId'] = rows[0][0]
                else:
                    rows = conn.execute('SELECT evento_id, resultado FROM procesados WHERE reserva_id = %s',
                                        (args.reserva_id,)).fetchall()
                    if len(rows) != 1:
                        raise AssertionError(f'Se esperaba un único evento procesado en {database}.')
                    result['eventoId'], result['resultado'] = rows[0]
                    expected_result = ('CORREO_ENVIADO' if database == 'notificaciones'
                                       else 'RESERVA_CONFIRMADA_REGISTRADA')
                    if rows[0][1] != expected_result:
                        raise AssertionError(f'Resultado inesperado en {database}.')
                    event_ids.append(rows[0][0])
            evidence['bases'][database] = result
            print(f'{database}: TLS {ssl[1]}, tablas verificadas' +
                  (', reserva verificada' if args.reserva_id else ''))
    if args.reserva_id and len(set(event_ids)) != 1:
        raise AssertionError('Los eventos de reserva, correo y auditoría no coinciden.')
    evidence['verificado'] = True
    directory = root / 'evidencias' / 'ep3'
    directory.mkdir(parents=True, exist_ok=True)
    name = args.archivo_evidencia or ('postgres-persistencia.json' if args.despues_reinicio else 'postgres.json')
    (directory / name).write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Evidencia guardada: evidencias/ep3/' + name)


if __name__ == '__main__':
    main()

"""Prueba real del flujo HTTP -> RabbitMQ -> dos consumers -> correo local.
Ejecutar con el stack encendido: python scripts/verificar.py
No borra reservas. Elige una fecha futura libre para evitar conflictos de otras demos.
"""
import base64
import concurrent.futures
import datetime as dt
import json
from pathlib import Path
import subprocess
import time
import urllib.request
import urllib.error
import uuid

ROOT=Path(__file__).resolve().parents[1]
EVIDENCIAS=ROOT/'evidencias'
EVIDENCIAS.mkdir(exist_ok=True)
AUTH='Basic '+base64.b64encode(b'reservas:reservas-demo').decode()

def http(url,data=None,auth=False):
    headers={'Content-Type':'application/json'}
    if auth: headers['Authorization']=AUTH
    req=urllib.request.Request(url,data=json.dumps(data).encode() if data is not None else None,headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=20) as r:
            body=r.read(); return r.status,json.loads(body) if body else None
    except urllib.error.HTTPError as e:
        return e.code,json.loads(e.read())

def wait(fn,timeout=180):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        try:
            result=fn()
            if result: return result
        except (OSError,ValueError): pass
        time.sleep(1)
    raise AssertionError('Se agotó el tiempo de espera')

def docker(*args):
    return subprocess.run(['docker','compose',*args],cwd=ROOT,check=True,capture_output=True,text=True).stdout

def save(name,data):
    (EVIDENCIAS/name).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')

def main():
    print('Esperando los cuatro servicios...')
    for port in [8080,8081,8082,8083]:
        wait(lambda p=port:http(f'http://localhost:{p}/actuator/health')[0]==200)
    _,asignaciones=http('http://localhost:8081/asignaciones')
    ocupadas={a['FECHA'] for a in asignaciones if a['MESA_ID']=='mesa-05'}
    future=next((dt.date.today()+dt.timedelta(days=n)).isoformat() for n in range(30,1000)
        if (dt.date.today()+dt.timedelta(days=n)).isoformat() not in ocupadas)
    payload={'clienteId':'demo-'+str(uuid.uuid4()),'emailCliente':'cliente@example.com','fechaReserva':future,
        'horaInicio':'18:00','horaFin':'20:00','cantidadPersonas':8}
    print('Deteniendo consumers para demostrar desacoplamiento y mensajes pendientes...')
    docker('stop','ms-notificaciones','ms-auditoria')
    try:
        code,reserva=http('http://localhost:8080/reservas',payload)
        assert code==201,(code,reserva)
        assert reserva['publicacion']=='PUBLICADA',reserva
        save('01-reserva-creada.json',reserva)
        def pendientes():
            status,queues=http('http://localhost:15672/api/queues/%2F',auth=True)
            if status!=200: return False
            return queues if len(queues)==2 and all(q.get('messages_ready',0)>=1 and q.get('consumers')==0 for q in queues) else False
        queues=wait(pendientes,30)
        save('02-queues-pendientes.json',queues)
        print('Reserva 201 y evento en ambas colas con consumers detenidos: OK')
    finally:
        docker('start','ms-notificaciones','ms-auditoria')
    event=reserva['eventoId']
    def procesado(port,path):
        status,rows=http(f'http://localhost:{port}/{path}')
        return [r for r in rows if r.get('EVENTO_ID')==event] if status==200 else False
    notificaciones=wait(lambda:procesado(8082,'notificaciones'),90)
    auditoria=wait(lambda:procesado(8083,'auditoria'),90)
    save('03-notificacion.json',notificaciones)
    save('04-auditoria.json',auditoria)
    def correo():
        status,mensajes=http('http://localhost:8025/api/v1/messages')
        return [m for m in mensajes.get('messages',[]) if reserva['reservaId'] in m.get('Subject','')] if status==200 else False
    save('05-correo-mailpit.json',wait(correo,30))
    code,conflicto=http('http://localhost:8080/reservas',payload)
    assert code==409,(code,conflicto)
    save('06-conflicto.json',{'status':code,'respuesta':conflicto})
    for invalid in [dict(payload,horaFin='17:00'),dict(payload,emailCliente='incorrecto'),dict(payload,cantidadPersonas=0),dict(payload,fechaReserva='2020-01-01')]:
        code,body=http('http://localhost:8080/reservas',invalid)
        assert code==400,(invalid,code,body)
    print('Ambos consumers procesaron el mismo evento y el correo llegó a Mailpit: OK')
    print('Conflicto 409 y validaciones 400: OK')
    parallel=dict(payload,horaInicio='21:00',horaFin='22:00')
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        responses=list(executor.map(lambda i:http('http://localhost:8080/reservas',dict(parallel,clienteId=f'concurrente-{i}')),range(8)))
    codes=[r[0] for r in responses]
    assert codes.count(201)==1 and codes.count(409)==7,codes
    save('07-concurrencia.json',{'solicitudes':8,'status':codes,'resultado':'Una reserva aceptada y siete rechazadas'})
    print('Ocho solicitudes concurrentes: una reserva 201 y siete conflictos 409: OK')
    save('08-exchange.json',http('http://localhost:15672/api/exchanges/%2F/reservas.exchange',auth=True)[1])
    save('09-bindings.json',http('http://localhost:15672/api/exchanges/%2F/reservas.exchange/bindings/source',auth=True)[1])
    # Esperar el evento de la prueba concurrente para guardar el estado final de las colas.
    successful=next(body for code,body in responses if code==201)
    wait(lambda:any(r.get('EVENTO_ID')==successful['eventoId'] for r in http('http://localhost:8082/notificaciones')[1]))
    wait(lambda:any(r.get('EVENTO_ID')==successful['eventoId'] for r in http('http://localhost:8083/auditoria')[1]))
    def colas_consumidas():
        status,queues=http('http://localhost:15672/api/queues/%2F',auth=True)
        return queues if status==200 and len(queues)==2 and all(q.get('messages',1)==0 and q.get('consumers',0)>=1 for q in queues) else False
    save('10-queues-consumidas.json',wait(colas_consumidas,30))
    (EVIDENCIAS/'11-logs.txt').write_text(docker('logs','--no-color','--tail','120','ms-reservas','ms-disponibilidad','ms-notificaciones','ms-auditoria'),encoding='utf-8')
    save('resultado.json',{'fechaPrueba':dt.datetime.now(dt.timezone.utc).isoformat(),'resultado':'OK',
        'reservaId':reserva['reservaId'],'eventoId':event,'pruebas':['201 con consumers detenidos','dos colas con mensaje pendiente',
        'notificación SMTP local','registro de auditoría','409 sin disponibilidad','400 para cuatro entradas inválidas','concurrencia 1 de 8 aceptada']})
    print('Evidencias guardadas en',EVIDENCIAS)

if __name__=='__main__': main()

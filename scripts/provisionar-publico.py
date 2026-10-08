"""Provisiona la demo pública en AWS mediante AWS CLI, sin publicar secretos.

EC2 + ALB interno + VPC Link + HTTP API HTTPS; PostgreSQL usa el RDS existente.
Estado y clave SSH quedan fuera del repositorio, en ~/.codex/tmp/ep3-publico.
Ejecutar con Python; requiere uv/awscli y una sesión AWS válida.
"""
import argparse
import base64
import ipaddress
import json
import os
from pathlib import Path
import ssl
import subprocess
import tempfile
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--region', default='us-east-1')
parser.add_argument('--profile', default='default')
args = parser.parse_args()
task_dir = Path.home() / '.codex' / 'tmp' / 'ep3-publico'
task_dir.mkdir(parents=True, exist_ok=True)
state_path = task_dir / 'infra.json'
state = json.loads(state_path.read_text()) if state_path.exists() else {}
prefix = 'ep3-reservas'
tags = [{'Key': 'Proyecto', 'Value': 'EP3-DSY1107'}]
uv = Path.home() / '.local' / 'bin' / 'uv.exe'
cli = [str(uv), 'tool', 'run', '--from', 'awscli', 'python', '-m', 'awscli']
os.environ['AWS_PAGER'] = ''


def aws(service, operation, *arguments, body=None):
    command = cli + [service, operation, *arguments, '--region', args.region,
                     '--profile', args.profile, '--output', 'json']
    request_path = None
    try:
        if body is not None:
            fd, request_path = tempfile.mkstemp(prefix='ep3-infra-', suffix='.json', dir=task_dir)
            with os.fdopen(fd, 'w', encoding='utf-8') as request:
                json.dump(body, request)
            command += ['--cli-input-json', 'file://' + request_path]
        result = subprocess.run(command, capture_output=True, text=True)
        if result.returncode:
            raise RuntimeError(f'{service} {operation}: {result.stderr.strip()}')
        return json.loads(result.stdout) if result.stdout.strip() else {}
    finally:
        if request_path:
            Path(request_path).unlink(missing_ok=True)


def save(**values):
    state.update(values)
    state_path.write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')


def security_group(suffix):
    name = prefix + '-' + suffix
    groups = aws('ec2', 'describe-security-groups', '--filters',
                 'Name=group-name,Values=' + name, 'Name=vpc-id,Values=' + vpc)['SecurityGroups']
    if groups:
        return groups[0]['GroupId']
    group = aws('ec2', 'create-security-group', '--group-name', name,
                '--description', 'EP3 reservas ' + suffix, '--vpc-id', vpc)['GroupId']
    aws('ec2', 'create-tags', '--resources', group, body={'Tags': tags})
    print('Security group creado: ' + name, flush=True)
    return group


def grant(group, port, source_group=None, cidr=None):
    current = aws('ec2', 'describe-security-groups', '--group-ids', group)['SecurityGroups'][0]
    for rule in current['IpPermissions']:
        if rule.get('IpProtocol') == 'tcp' and rule.get('FromPort') == port and rule.get('ToPort') == port:
            if source_group and any(x['GroupId'] == source_group for x in rule.get('UserIdGroupPairs', [])):
                return
            if cidr and any(x['CidrIp'] == cidr for x in rule.get('IpRanges', [])):
                return
    rule = {'IpProtocol': 'tcp', 'FromPort': port, 'ToPort': port}
    if source_group:
        rule['UserIdGroupPairs'] = [{'GroupId': source_group}]
    else:
        rule['IpRanges'] = [{'CidrIp': cidr}]
    aws('ec2', 'authorize-security-group-ingress', '--group-id', group,
        body={'IpPermissions': [rule]})


identity = aws('sts', 'get-caller-identity')
if state.get('Account') and state['Account'] != identity['Account']:
    raise RuntimeError('El estado pertenece a otra cuenta AWS; usar un directorio de estado separado.')
if state.get('Region') and state['Region'] != args.region:
    raise RuntimeError('El estado pertenece a otra región AWS; conservar la región del despliegue.')
save(Account=identity['Account'], Region=args.region)
vpcs = aws('ec2', 'describe-vpcs', '--filters', 'Name=is-default,Values=true')['Vpcs']
if len(vpcs) != 1:
    raise RuntimeError('Se necesita una VPC por defecto única para este despliegue.')
vpc = vpcs[0]['VpcId']
all_subnets = aws('ec2', 'describe-subnets', '--filters', 'Name=vpc-id,Values=' + vpc,
                  'Name=default-for-az,Values=true')['Subnets']
# us-east-1 VPC Links V2 no soporta use1-az3 según documentación AWS.
subnets = sorted((x for x in all_subnets if x['AvailabilityZoneId'] != 'use1-az3'),
                 key=lambda x: x['AvailabilityZoneId'])[:2]
if len(subnets) != 2:
    raise RuntimeError('Faltan dos subredes compatibles con VPC Link.')
subnet_ids = [x['SubnetId'] for x in subnets]
save(VpcId=vpc, SubnetIds=subnet_ids)
ca = os.environ.get('AWS_CA_BUNDLE')
context = ssl.create_default_context(cafile=ca) if ca else ssl.create_default_context()
with urllib.request.urlopen('https://checkip.amazonaws.com', context=context, timeout=20) as response:
    address = str(ipaddress.IPv4Address(response.read().decode().strip()))

print('Creando red privada para API Gateway, ALB, EC2 y RDS...', flush=True)
link_group = security_group('link')
alb_group = security_group('alb')
ec2_group = security_group('web')
grant(alb_group, 80, source_group=link_group)
grant(ec2_group, 80, source_group=alb_group)
grant(ec2_group, 22, cidr=address + '/32')
db = aws('rds', 'describe-db-instances', '--db-instance-identifier', 'ep3-reservas-postgres')['DBInstances'][0]
if db['DBInstanceStatus'] != 'available':
    raise RuntimeError('RDS debe estar disponible antes de desplegar.')
for group in db['VpcSecurityGroups']:
    grant(group['VpcSecurityGroupId'], 5432, source_group=ec2_group)
save(LinkSecurityGroup=link_group, AlbSecurityGroup=alb_group, Ec2SecurityGroup=ec2_group)

key_path = task_dir / 'ep3-reservas-web.pem'
if not key_path.exists():
    subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-C', 'ep3-reservas',
                    '-f', str(key_path)], check=True)
    subprocess.run(['icacls', str(key_path), '/inheritance:r', '/grant:r',
                    os.environ['USERNAME'] + ':F'], check=True, capture_output=True)
keys = aws('ec2', 'describe-key-pairs', '--filters', 'Name=key-name,Values=' + prefix + '-web')['KeyPairs']
if not keys:
    aws('ec2', 'import-key-pair', '--key-name', prefix + '-web',
        '--public-key-material', 'fileb://' + str(key_path) + '.pub')
save(KeyPath=str(key_path))
instances = aws('ec2', 'describe-instances', '--filters',
                'Name=tag:Name,Values=' + prefix + '-web',
                'Name=instance-state-name,Values=pending,running,stopping,stopped')['Reservations']
instances = [i for reservation in instances for i in reservation['Instances']]
if len(instances) > 1:
    raise RuntimeError('Hay varias EC2 de la demo; resolver ambigüedad.')
if not instances:
    images = aws('ec2', 'describe-images', '--owners', '099720109477', '--filters',
                 'Name=name,Values=ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*',
                 'Name=state,Values=available')['Images']
    image = max(images, key=lambda x: x['CreationDate'])
    user_data = '''#!/bin/bash
set -eu
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y docker.io docker-compose-v2
systemctl enable --now docker
install -d -o ubuntu -g ubuntu -m 700 /home/ubuntu/ep3
touch /home/ubuntu/ep3/docker-ready
chown ubuntu:ubuntu /home/ubuntu/ep3/docker-ready
'''
    result = aws('ec2', 'run-instances', body={
        'ImageId': image['ImageId'], 'InstanceType': 't3.medium', 'MinCount': 1, 'MaxCount': 1,
        'KeyName': prefix + '-web',
        'NetworkInterfaces': [{'DeviceIndex': 0, 'SubnetId': subnet_ids[0],
                               'Groups': [ec2_group], 'AssociatePublicIpAddress': True}],
        'BlockDeviceMappings': [{'DeviceName': image['RootDeviceName'],
                                 'Ebs': {'VolumeSize': 20, 'VolumeType': 'gp3', 'Encrypted': True,
                                         'DeleteOnTermination': True}}],
        'MetadataOptions': {'HttpTokens': 'required', 'HttpEndpoint': 'enabled'},
        'UserData': base64.b64encode(user_data.encode()).decode(),
        'TagSpecifications': [{'ResourceType': 'instance', 'Tags': tags + [{'Key': 'Name', 'Value': prefix + '-web'}]}],
    })
    instance = result['Instances'][0]
    print('EC2 creada: ' + instance['InstanceId'], flush=True)
else:
    instance = instances[0]
    if instance['State']['Name'] == 'stopped':
        aws('ec2', 'start-instances', '--instance-ids', instance['InstanceId'])
save(InstanceId=instance['InstanceId'])

balances = aws('elbv2', 'describe-load-balancers')['LoadBalancers']
balance = next((x for x in balances if x['LoadBalancerName'] == prefix + '-alb'), None)
if not balance:
    balance = aws('elbv2', 'create-load-balancer', body={
        'Name': prefix + '-alb', 'Subnets': subnet_ids, 'SecurityGroups': [alb_group],
        'Scheme': 'internal', 'Type': 'application', 'IpAddressType': 'ipv4', 'Tags': tags,
    })['LoadBalancers'][0]
save(LoadBalancerArn=balance['LoadBalancerArn'])
groups = aws('elbv2', 'describe-target-groups')['TargetGroups']
target = next((x for x in groups if x['TargetGroupName'] == prefix + '-nginx'), None)
if not target:
    target = aws('elbv2', 'create-target-group', body={
        'Name': prefix + '-nginx', 'Protocol': 'HTTP', 'Port': 80, 'VpcId': vpc,
        'TargetType': 'instance', 'HealthCheckPath': '/healthz',
        'HealthCheckIntervalSeconds': 15, 'HealthyThresholdCount': 2,
        'UnhealthyThresholdCount': 3, 'Matcher': {'HttpCode': '200'}, 'Tags': tags,
    })['TargetGroups'][0]
aws('elbv2', 'register-targets', '--target-group-arn', target['TargetGroupArn'],
    body={'Targets': [{'Id': instance['InstanceId'], 'Port': 80}]})
listeners = aws('elbv2', 'describe-listeners', '--load-balancer-arn', balance['LoadBalancerArn'])['Listeners']
listener = next((x for x in listeners if x['Port'] == 80), None)
if not listener:
    listener = aws('elbv2', 'create-listener', body={
        'LoadBalancerArn': balance['LoadBalancerArn'], 'Protocol': 'HTTP', 'Port': 80,
        'DefaultActions': [{'Type': 'forward', 'TargetGroupArn': target['TargetGroupArn']}],
    })['Listeners'][0]
save(TargetGroupArn=target['TargetGroupArn'], ListenerArn=listener['ListenerArn'])

links = aws('apigatewayv2', 'get-vpc-links').get('Items', [])
link = next((x for x in links if x['Name'] == prefix + '-link'), None)
if not link:
    link = aws('apigatewayv2', 'create-vpc-link', body={
        'Name': prefix + '-link', 'SubnetIds': subnet_ids, 'SecurityGroupIds': [link_group],
        'Tags': {'Proyecto': 'EP3-DSY1107'},
    })
save(VpcLinkId=link['VpcLinkId'])
apis = aws('apigatewayv2', 'get-apis').get('Items', [])
api = next((x for x in apis if x['Name'] == prefix + '-web'), None)
if not api:
    api = aws('apigatewayv2', 'create-api', body={
        'Name': prefix + '-web', 'ProtocolType': 'HTTP', 'Tags': {'Proyecto': 'EP3-DSY1107'},
    })
save(ApiId=api['ApiId'], PublicUrl=api['ApiEndpoint'])
integrations = aws('apigatewayv2', 'get-integrations', '--api-id', api['ApiId']).get('Items', [])
integration = next((x for x in integrations if x.get('IntegrationUri') == listener['ListenerArn']), None)
if not integration:
    integration = aws('apigatewayv2', 'create-integration', body={
        'ApiId': api['ApiId'], 'IntegrationType': 'HTTP_PROXY', 'IntegrationMethod': 'ANY',
        'ConnectionType': 'VPC_LINK', 'ConnectionId': link['VpcLinkId'],
        'IntegrationUri': listener['ListenerArn'], 'PayloadFormatVersion': '1.0',
        'RequestParameters': {'overwrite:path': '$request.path'}, 'TimeoutInMillis': 30000,
    })
routes = aws('apigatewayv2', 'get-routes', '--api-id', api['ApiId']).get('Items', [])
if not any(x['RouteKey'] == '$default' for x in routes):
    aws('apigatewayv2', 'create-route', '--api-id', api['ApiId'], '--route-key', '$default',
        '--target', 'integrations/' + integration['IntegrationId'])
stages = aws('apigatewayv2', 'get-stages', '--api-id', api['ApiId']).get('Items', [])
if not any(x['StageName'] == '$default' for x in stages):
    aws('apigatewayv2', 'create-stage', body={
        'ApiId': api['ApiId'], 'StageName': '$default', 'AutoDeploy': True,
        'DefaultRouteSettings': {'ThrottlingBurstLimit': 50, 'ThrottlingRateLimit': 25},
    })
current = aws('ec2', 'describe-instances', '--instance-ids', instance['InstanceId'])['Reservations'][0]['Instances'][0]
save(PublicIp=current.get('PublicIpAddress'), PrivateIp=current['PrivateIpAddress'])
print('Infraestructura creada. URL reservada: ' + api['ApiEndpoint'], flush=True)
print('Esperar disponibilidad de VPC Link y desplegar los contenedores antes de probar.', flush=True)

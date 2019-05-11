#!/usr/bin/env python3.6

import sys
import os
import secrets
import string
alphabet = string.ascii_letters + string.digits
import argparse
import requests
import getpass
import json
import shutil
import base64
import glob
import subprocess

parser = argparse.ArgumentParser(description = 'Provision and deploy powerwatch backend')
parser.add_argument('-p','--product', type=int, required=True, action='append')
parser.add_argument('-n','--name', type=str, required=True)
parser.add_argument('-r','--region', type=str, required=True)
parser.add_argument('-u','--timescaleu', type=str, required=True)
parser.add_argument('-t','--timescalep', type=str, required=True)
parser.add_argument('-w','--webhookp', type=str, required=True)
args = parser.parse_args()

timescale_user = str(base64.b64encode('powerwatch'.encode('ascii')), 'utf-8')
timescale_password = str(base64.b64encode(args.timescalep.encode('ascii')), 'utf-8')
webhook_password = str(base64.b64encode(args.webhookp.encode('ascii')), 'utf-8')
product_ids = str(args.product)

#now get the particle authentication token using the particle API
print()
print('Generating a particle access token...')
username = input('Particle username:')
password = getpass.getpass('Particle password:')
resp =  requests.post('https://api.particle.io/oauth/token', data={'username':username, 'password':password,'grant_type':'password','expires_in':0}, auth=('particle', 'particle'))
access_token = str(base64.b64encode(json.loads(resp.text)['access_token'].encode('ascii')), 'utf-8')

#CREATE a new globally routable ip address
poster_ip_address = ''
try:
    subprocess.check_call(['gcloud', 'compute','addresses','create',
                        args.name+'-poster','--global'])
except Exception as e:
    print(e)

try:
    poster_ip_address = subprocess.check_output(['gcloud', 'compute','addresses','describe',
                        args.name+'-poster','--global'])
    poster_ip_address = poster_ip_address.decode('utf-8').replace('\n',' ').split(' ')[1]
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','start',
                        '--zone','powerwatch'])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','add',
                        '--zone','powerwatch',
                        '--name','poster.'+args.name+'.powerwatch.io',
                        '--type','A',
                        '--ttl','300',poster_ip_address])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','execute',
                        '--zone','powerwatch'])
except Exception as e:
    os.remove('transaction.yaml')
    print(e)

#now search and replace all of the templating variables with the generated values
for filepath in glob.iglob('./*'):
    if(not os.path.isdir(filepath)):
        with open(filepath) as file:
            s = file.read()

        s = s.replace('${TIMESCALE_USER}', timescale_user)
        s = s.replace('${TIMESCALE_PASSWORD}', timescale_password)
        s = s.replace('${WEBHOOK_PASSWORD}', webhook_password)
        s = s.replace('${PRODUCT_IDS}', product_ids)
        s = s.replace('${PARTICLE_AUTH_TOKEN}', access_token)
        s = s.replace('${POWERWATCH_POSTGRES_POSTER_IP_ADDRESS}', args.name+'-poster')
        s = s.replace('${POWERWATCH_POSTGRES_POSTER_DOMAIN_NAME}', 'poster.'+args.name+'.powerwatch.io')
        with open(filepath, "w") as file:
            file.write(s)

#Powerwatch poster deployment
try:
    subprocess.check_call(['kubectl', 'delete', 'configmap', 'config'])
except:
    pass

try:
    subprocess.check_call(['kubectl', 'create', 'configmap', 'config',
                            '--from-file=config.json',
                            '--from-file=webhook.json',
                            '--from-file=postgres.json'])
except Exception as e:
    shutil.rmtree(dest)
    raise e


subprocess.check_call(['kubectl','apply','-f', 'particle-auth-token.yaml'])
subprocess.check_call(['kubectl','apply','-f', 'webhook-pass.yaml'])
subprocess.check_call(['kubectl','apply','-f', 'postgres-user-pass.yaml'])
subprocess.check_call(['kubectl','apply','-f', 'powerwatch-postgres-poster.yaml'])

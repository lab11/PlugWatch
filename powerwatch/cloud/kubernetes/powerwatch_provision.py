#!/usr/bin/env python3
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

parser = argparse.ArgumentParser(description = 'Provision and deploy powerwatch backend')
parser.add_argument('-p','--product', type=int, required=True, action='append')
parser.add_argument('-n','--name', type=str, required=True)
args = parser.parse_args()

# Gather/generate the necessary information
timescale_user = str(base64.b64encode('powerwatch'.encode('ascii')), 'utf-8')
timescale_password = str(base64.b64encode(''.join(secrets.choice(alphabet) for i in range(12)).encode('ascii')), 'utf-8')
influx_user = str(base64.b64encode('powerwatch'.encode('ascii')), 'utf-8')
influx_password = str(base64.b64encode(''.join(secrets.choice(alphabet) for i in range(12)).encode('ascii')), 'utf-8')
influx_admin_user = str(base64.b64encode('admin'.encode('ascii')), 'utf-8')
influx_admin_password = str(base64.b64encode(''.join(secrets.choice(alphabet) for i in range(12)).encode('ascii')), 'utf-8')
grafana_database_user = str(base64.b64encode('grafana'.encode('ascii')), 'utf-8')
grafana_database_user_clear = 'grafana'
grafana_database_password_clear = ''.join(secrets.choice(alphabet) for i in range(12))
grafana_database_password = str(base64.b64encode(grafana_database_password_clear.encode('ascii')), 'utf-8')
grafana_admin_password = str(base64.b64encode(''.join(secrets.choice(alphabet) for i in range(12)).encode('ascii')), 'utf-8')
product_ids = str(args.product)

now get the particle authentication token using the particle API
username = input('Particle username:')
password = getpass.getpass('Particle password:')
resp =  requests.post('https://api.particle.io/oauth/token', data={'username':username, 'password':password,'grant_type':'password','expires_in':0}, auth=('particle', 'particle'))
access_token = str(base64.b64encode(json.loads(resp.text)['access_token'].encode('ascii')), 'utf-8')

#copy all of the kubernetes configuration files into a staging area
dest = args.name+'_deployment'
os.mkdir(dest)
shutil.copytree('timescale', dest + '/timescale')
shutil.copytree('influx', dest + '/influx')
shutil.copytree('grafana', dest + '/grafana')
shutil.copytree('powerwatch-data-poster', dest + '/powerwatch-data-poster')

#now search and replace all of the templating variables with the generated values
for filepath in glob.iglob('./'+dest+'/**', recursive=True):
    if(not os.path.isdir(filepath)):
        with open(filepath) as file:
            s = file.read()

        s = s.replace('${TIMESCALE_USER}', timescale_user)
        s = s.replace('${TIMESCALE_PASSWORD}', timescale_password)
        s = s.replace('${INFLUX_USER}', influx_user)
        s = s.replace('${INFLUX_PASSWORD}', influx_password)
        s = s.replace('${INFLUX_ADMIN_USER}', influx_admin_user)
        s = s.replace('${INFLUX_ADMIN_PASSWORD}', influx_admin_password)
        s = s.replace('${GRAFANA_DATABASE_USER}', grafana_database_user)
        s = s.replace('${GRAFANA_DATABASE_USER_CLEAR}', grafana_database_user_clear)
        s = s.replace('${GRAFANA_DATABASE_PASSWORD}', grafana_database_password)
        s = s.replace('${GRAFANA_DATABASE_PASSWORD_CLEAR}', grafana_database_password_clear)
        s = s.replace('${GRAFANA_PASSWORD}', grafana_admin_password)
        s = s.replace('${PRODUCT_IDS}', product_ids)
        s = s.replace('${PARTICLE_AUTH_TOKEN}', access_token)
        with open(filepath, "w") as file:
            file.write(s)

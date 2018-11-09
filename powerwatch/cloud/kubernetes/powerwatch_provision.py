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
import subprocess
from kubernetes import client, config, utils

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

#now get the particle authentication token using the particle API
print()
print('Generating a particle access token...')
username = input('Particle username:')
password = getpass.getpass('Particle password:')
resp =  requests.post('https://api.particle.io/oauth/token', data={'username':username, 'password':password,'grant_type':'password','expires_in':0}, auth=('particle', 'particle'))
access_token = str(base64.b64encode(json.loads(resp.text)['access_token'].encode('ascii')), 'utf-8')

print()
print('Configuring new cluster for these products...')
#copy all of the kubernetes configuration files into a staging area
dest = args.name+'_deployment'
os.mkdir(dest)
shutil.copytree('timescale', dest + '/timescale')
shutil.copytree('influx', dest + '/influx')
shutil.copytree('grafana', dest + '/grafana')
shutil.copytree('powerwatch-data-poster', dest + '/powerwatch-data-poster')
shutil.copytree('powerwatch-visualization', dest + '/powerwatch-visualization')

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
        s = s.replace('${GRAFANA_IP_ADDRESS}', args.name+'-grafana')
        s = s.replace('${GRAFANA_IP_ADDRESS}', args.name+'-powerwatch-visualization')
        with open(filepath, "w") as file:
            file.write(s)

#deploy a cluster in google cloud
print()
print('Deploying a new google cloud container cluster (this could take several minutes)...')

#Make sure the project is set correctly
try:
    subprocess.check_call(['gcloud', 'config','set','project','powerwatch-backend'])
except Exception as e:
    print('Error setting the cloud project - do you the google cloud SDK isntalled and logged into an account with access to the powerwatch-backend project?')
    shutil.rmtree(dest)
    raise e

#Create a new cluster with the deployment name
try:
    subprocess.check_call(['gcloud', 'container','clusters','create',args.name,
                            '--region', 'us-central1',
                            '--num-nodes', '2',
                            '--machine-type', 'n1-standard-1'])
except Exception as e:
    shutil.rmtree(dest)
    raise e

#CREATE a new globally routable ip address
try:
    subprocess.check_call(['gcloud', 'compute','addresses','create',
                        args.name+'-grafana','--global'])
except Exception as e:
    #shutil.rmtree(dest)
    #raise e
    pass

#CREATE a new globally routable ip address
try:
    subprocess.check_call(['gcloud', 'compute','addresses','create',
                        args.name+'-powerwatch-visualization','--global'])
except Exception as e:
    #shutil.rmtree(dest)
    #raise e
    pass


#describe that address for printing
grafana_ip_address = ''
try:
    grafana_ip_address = subprocess.check_output(['gcloud', 'compute','addresses','describe',
                        args.name+'-grafana','--global'])
except Exception as e:
    shutil.rmtree(dest)
    raise e

#describe that address for printing
visualization_ip_address = ''
try:
    visualization_ip_address = subprocess.check_output(['gcloud', 'compute','addresses','describe',
                        args.name+'-powerwatch-visualization','--global'])
except Exception as e:
    shutil.rmtree(dest)
    raise e


#point the kubernetes python API at the new cluster
try:
    subprocess.check_call(['gcloud', 'container', 'clusters', 'get-credentials', args.name,
                            '--region', 'us-central1',
                            '--project', 'powerwatch-backend'])
except Exception as e:
    shutil.rmtree(dest)
    raise e

#A retained storage class for SDDs
try:
    subprocess.check_call(['kubectl', 'create', '-f', 'storage-class/retained-storage-class.yaml'])
except:
    pass

#Timescale deployment
try:
    subprocess.check_call(['kubectl', 'delete', 'configmap', 'timescale-initialization'])
except:
    pass

try:
    subprocess.check_call(['kubectl', 'create', 'configmap', 'timescale-initialization',
                            '--from-file='+dest+'/timescale/initialize-read-user.sh'])
except Exception as e:
    shutil.rmtree(dest)
    raise e

subprocess.check_call(['kubectl', 'create', '-f', dest+'/timescale/timescale-config.yaml'])
subprocess.check_call(['kubectl','create','-f', dest+'/timescale/timescale-deployment.yaml'])

#Influx deployment
subprocess.check_call(['kubectl','create','-f', dest+'/influx/influx-config.yaml'])
subprocess.check_call(['kubectl','create','-f', dest+'/influx/influx-deployment.yaml'])

#Grafana deployment
try:
    subprocess.check_call(['kubectl', 'delete', 'configmap', 'grafana-provisioning'])
except:
    pass

try:
    subprocess.check_call(['kubectl', 'delete', 'configmap', 'grafana-dashboards'])
except:
    pass

try:
    subprocess.check_call(['kubectl', 'create', 'configmap', 'grafana-provisioning',
                            '--from-file='+dest+'/grafana/datasource.yaml',
                            '--from-file='+dest+'/grafana/dashboard.yaml'])
    subprocess.check_call(['kubectl', 'create', 'configmap', 'grafana-dashboards',
                            '--from-file='+dest+'/grafana/Powerwatch_General.json'])
except Exception as e:
    shutil.rmtree(dest)
    raise e

subprocess.check_call(['kubectl','create','-f', dest+'/grafana/grafana-config.yaml'])
subprocess.check_call(['kubectl','create','-f', dest+'/grafana/grafana-deployment.yaml'])

#Powerwatch poster deployment
try:
    subprocess.check_call(['kubectl', 'delete', 'configmap', 'config'])
except:
    pass

try:
    subprocess.check_call(['kubectl', 'create', 'configmap', 'config',
                            '--from-file='+dest+'/powerwatch-data-poster/config.json',
                            '--from-file='+dest+'/powerwatch-data-poster/influx.json',
                            '--from-file='+dest+'/powerwatch-data-poster/postgres.json'])
except Exception as e:
    shutil.rmtree(dest)
    raise e

subprocess.check_call(['kubectl','create','-f', dest+'/powerwatch-data-poster/particle-auth-token.yaml'])
subprocess.check_call(['kubectl','create','-f', dest+'/powerwatch-data-poster/influx-user-pass.yaml'])
subprocess.check_call(['kubectl','create','-f', dest+'/powerwatch-data-poster/postgres-user-pass.yaml'])
subprocess.check_call(['kubectl','create','-f', dest+'/powerwatch-data-poster/powerwatch-data-poster-deployment.yaml'])

#Powerwatch Visualization
subprocess.check_call(['kubectl','create','-f', dest+'/grafana/powerwatch-visualization-deployment.yaml'])


print()
print('Generated usernames and passwords (save to lastpass)')
#Print the information generated above
print('Timescale User: ' + str(base64.b64decode(timescale_user),'utf-8'))
print('Timescale Password: ' + str(base64.b64decode(timescale_password),'utf-8'))
print('Influx User: ' + str(base64.b64decode(influx_user),'utf-8'))
print('Influx Password: ' + str(base64.b64decode(influx_password),'utf-8'))
print('Influx Admin User: ' + str(base64.b64decode(influx_admin_user),'utf-8'))
print('Influx Admin Password: ' + str(base64.b64decode(influx_admin_password),'utf-8'))
print('Grafana Database User: ' + grafana_database_user_clear)
print('Grafana Database Password: ' + grafana_database_password_clear)
print('Grafana Admin User: admin')
print('Grafana Admin Password: ' + str(base64.b64decode(grafana_admin_password),'utf-8'))
print('Particle access token: ' + str(base64.b64decode(access_token),'utf-8'))
print('Grafana globally accessible IP address: ' + str(grafana_ip_address))
print('Visualization globally accessible IP address: ' + str(visualization_ip_address))

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

parser = argparse.ArgumentParser(description = 'Provision and deploy powerwatch backend')
parser.add_argument('-n','--name', type=str, required=True)
parser.add_argument('-r','--region', type=str, required=True)
args = parser.parse_args()

#CREATE a new globally routable ip address
mosquitto_ip_address = ''
mosquitto_ip_address_name = args.name + '-mqtt'
try:
    subprocess.check_call(['gcloud', 'compute','addresses','create',
                        args.name+'-mqtt','--region',args.region])
except Exception as e:
    print(e)

try:
    mosquitto_ip_address = subprocess.check_output(['gcloud', 'compute','addresses','describe',
                        mosquitto_ip_address_name,'--region',args.region])
    mosquitto_ip_address = mosquitto_ip_address.decode('utf-8').replace('\n',' ').split(' ')[1]
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','start',
                        '--zone','powerwatch'])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','add',
                        '--zone','powerwatch',
                        '--name','mosquitto.'+args.name+'.powerwatch.io',
                        '--type','A',
                        '--ttl','300',mosquitto_ip_address])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','execute',
                        '--zone','powerwatch'])
except Exception as e:
    os.remove('transaction.yaml')
    print(e)

#now search and replace all of the templating variables with the generated values
for filepath in glob.iglob('./*'):
    if(filepath == 'instantiate.py' or filepath == './instantiate.py'):
        continue

    if(not os.path.isdir(filepath)):
        with open(filepath) as file:
            s = file.read()

        s = s.replace('${MOSQUITTO_IP_ADDRESS}', mosquitto_ip_address)
        s = s.replace('${MOSQUITTO_IP_ADDRESS_NAME}', mosquitto_ip_address_name)
        s = s.replace('${MOSQUITTO_DOMAIN_NAME}', 'mosquitto.'+args.name+'.powerwatch.io')
        with open(filepath, "w") as file:
            file.write(s)

try:
    subprocess.check_call(['kubectl', 'delete', 'configmap', 'mosquitto-config'])
except:
    pass
try:
    subprocess.check_call(['kubectl', 'create', 'configmap', 'mosquitto-config',
                            '--from-file=mosquitto.conf'])
except Exception as e:
    raise e

try:
    subprocess.check_call(['kubectl', 'delete', 'configmap', 'mosquitto-cafile'])
except:
    pass
try:
    subprocess.check_call(['kubectl', 'create', 'configmap', 'mosquitto-cafile',
                            '--from-file=chain.pem'])
except Exception as e:
    raise e

try:
    subprocess.check_call(['kubectl', 'delete', 'secret', 'mosquitto-password'])
except:
    pass
try:
    subprocess.check_call(['kubectl', 'create', 'secret','generic','mosquitto-password',
                            '--from-file=password.txt'])
except Exception as e:
    raise e



subprocess.check_call(['kubectl','apply','-f', 'mosquitto-deployment.yaml'])

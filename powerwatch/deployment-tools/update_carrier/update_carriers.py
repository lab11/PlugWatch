#!/usr/bin/env python

from datetime import datetime
import time
import psycopg2
import yaml
import argparse
import csv
import requests
import json

#open the postgres database
with open('postgres_config.json') as config_file:
    config = yaml.safe_load(config_file)

#open the postgres database
with open('particle_config.json') as config_file:
    particle_config = yaml.safe_load(config_file)

connection = psycopg2.connect(dbname=config['database'], user=config['user'], host=config['host'], password=config['password'])
cursor = connection.cursor();

#now get a list of devices in the product
print('Getting list of devices in the two products')
devices1 = None
devices2 = None
r = requests.get("https://api.particle.io/v1/products/7008/devices?access_token=" + particle_config['key'] + '&perPage=1000&sortAttr=firmwareVersion&sortDir=desc')
resp = json.loads(r.text)
if 'ok' in resp:
    if(resp['ok'] is False):
        print('Getting devices failed: ' + resp['error'])
else:
    devices1 = json.loads(r.text)

r = requests.get("https://api.particle.io/v1/products/7009/devices?access_token=" + particle_config['key'] + '&perPage=1000&sortAttr=firmwareVersion&sortDir=desc')
resp = json.loads(r.text)
if 'ok' in resp:
    if(resp['ok'] is False):
        print('Getting devices failed: ' + resp['error'])
else:
    devices2 = json.loads(r.text)

devices = devices1['devices']
devices += devices2['devices']

for device in devices:
    #query to see if this device is in the deployment table
    cursor.execute('SELECT * from deployment where core_id = %s',(device['id'],))
    result = cursor.fetchone()
    if(result != None):
        #we should insert the carrier name based on iccid
        if('last_iccid' in device):
            if(device['last_iccid'].find('89233') != -1):
                #this is an mtn sim
                print('Setting {} to mtn'.format(device['id']))
                cursor.execute("UPDATE deployment set cellular_carrier='mtn' where core_id = %s",(device['id'],))
            elif(device['last_iccid'].find('890126') != -1):
                #this is a twilio sim
                print('Setting {} to twilio'.format(device['id']))
                cursor.execute("UPDATE deployment set cellular_carrier='twilio' where core_id = %s",(device['id'],))
            else:
                #what is this?
                print('Unknown ICCID type - skipping');
    else:
        print('Device has not been deployed');

connection.commit()
cursor.close()
connection.close()

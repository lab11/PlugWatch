#! /usr/bin/env python3

import sys
import requests
import argparse
import json

parser = argparse.ArgumentParser(description = 'Deploy particle firmware')
parser.add_argument('-p','--product', type=int, required=True)
parser.add_argument('-v','--version', type=int, required=True)
parser.add_argument('-f','--fname', type=str, required=True)
parser.add_argument('-k','--key', type=str, required=True)
parser.add_argument('-t','--title', type=str, required=True)
parser.add_argument('-d','--filter',type=str,required=False)

args = parser.parse_args()
product_string = str(args.product)

#get the devices filter if it exists
device_filter_list = [];
if args.filter != None:
    with open(args.filter) as f:
        device_filter_list = f.readlines()
device_filter_list = [x.strip() for x in device_filter_list]

print()
print('Uploading firmware to the particle cloud...')
#first upload the binary file to the cloud
r = requests.post("https://api.particle.io/v1/products/" + product_string + "/firmware?access_token=" + args.key,
        data = {'version':str(args.version), 'title':args.title}, files=dict(binary=open(args.fname, 'rb')))

resp = json.loads(r.text)
if 'ok' in resp:
    if(resp['ok'] is False):
        print('Firmware upload failed: ' + resp['error'])
else:
    print('Firmware upload succeeded')

print()

#now get a list of devices in the product
print('Getting list of devices in product...')
r = requests.get("https://api.particle.io/v1/products/" + product_string + "/devices?access_token=" + args.key + '&perPage=1000&sortAttr=firmwareVersion&sortDir=desc')

resp = json.loads(r.text)
if 'ok' in resp:
    if(resp['ok'] is False):
        print('Getting devices failed: ' + resp['error'])
else:
    print('Getting devices succeeded')

devices = json.loads(r.text)
print()



#now iterate through the product locking each device ID to the new version
print('Locking devices to new firmware...')
for device in devices['devices']:
    if(device['id'] not in device_filter_list and args.filter != None):
        print('Skipping {} - not in filter list'.format(device['id']));
        continue

    if('firmware_version' in device and device['firmware_version'] >= args.version):
        print('Skipping {} - already has equal or greater version'.format(device['id']));
        continue


    r = requests.put("https://api.particle.io/v1/products/" + product_string + "/devices/" + device['id'],
        data = {'desired_firmware_version':str(args.version), 'access_token':args.key, 'flash':'true'})
    resp = json.loads(r.text)
    if 'ok' in resp:
        if(resp['ok'] is False):
            if 'error' in resp:
                print('Locking device ' + device['id'] + ' failed: ' + resp['error'])
            elif 'errors' in resp:
                print('Locking device ' + device['id'] + ' failed: ' + resp['errors'][0])
    else:
        print('Locking device ' + device['id'] + ' succeeded')

print()

#Ask if the user would like to release this firmware
inp = input('Would you like to release this firmware [Y/n]: ')
if(inp == 'Y'):
    #now release the version to the product
    print('Releasing firmware...')
    r = requests.put("https://api.particle.io/v1/products/" + product_string + "/firmware/release",
            data = {'version':str(args.version), 'access_token':args.key, 'product_default':'true'})
    resp = json.loads(r.text)
    if 'ok' in resp:
        if(resp['ok'] is False):
            print('Releasing firmware failed: ' + resp['error'])
    else:
        print('Releasing firmware succeeded')


#!/usr/bin/env python

from PIL import Image, ImageDraw, ImageFont, ImageOps
import pyqrcode
import argparse
import glob
import yaml
import json
import requests
import re
import sys
import os
import time
import pyscreen
import serial
import datetime
import struct
from psheets import *

shield_fnt = ImageFont.truetype('/Library/Fonts/Arial.ttf', 14)
case_fnt = ImageFont.truetype('/Library/Fonts/Arial.ttf', 30)
case_fnt_larger = ImageFont.truetype('/Library/Fonts/Arial.ttf', 40)
case_fnt_small = ImageFont.truetype('/Library/Fonts/Arial.ttf', 20)
case_fnt_bold = ImageFont.truetype('/Library/Fonts/Arial Bold.ttf', 30)
case_fnt_l = ImageFont.truetype('/Library/Fonts/Arial.ttf', 60)

parser = argparse.ArgumentParser(description = 'Deploy particle firmware')
parser.add_argument('-p','--product', type=int, required=True)

args = parser.parse_args()

#make the gen folder if it doesn't exist
if(not os.path.isdir('./gen')):
    os.mkdir('./gen')

#Find the binary file in this directory with the correct product id
correct_file = None
binaries = glob.glob('./*.bin')
for b in binaries:
    binary = open(b, 'rb')
    data = binary.read()
    product_id = struct.unpack('<H',data[-44:-42])[0]
    if (product_id == args.product):
        #found the correct file
        correct_file = b
        break

if(correct_file == None):
    print("Did not find a predeployment firmware that matches the provided product ID. Exiting")
    sys.exit(1)


# Find the particle port
ports = glob.glob('/dev/ttyA*') + glob.glob('/dev/tty.u*')
if(len(ports) == 0):
    print('Did not find any particles - exiting.')
    sys.exit(1)

cur_max = 0
if sys.platform == 'linux' or sys.platform == 'linux2':
    cur_max = -1
elif sys.platform == 'darwin':
    cur_max = 0

electron_port = ''
for port in ports:
    num = int(re.findall(r"([0-9.]*[0-9]+)",port)[0])
    if num > cur_max:
        cur_max = num
        electron_port = port

print("Updating the particle base firmware...")
ser2 = serial.Serial(port=electron_port,baudrate=14400)
time.sleep(0.1)
ser2.close()
time.sleep(4)
os.system("particle flash --usb system-part1-0.7.0-electron.bin") #update
os.system("particle flash --usb system-part2-0.7.0-electron.bin") #update
os.system("particle flash --usb system-part3-0.7.0-electron.bin") #update
os.system("particle flash --usb " + correct_file) #send correct file

#extract the particle ID and shield ID from the predeploy firmware
time.sleep(5)
print
print("Getting core and shield ID...")

retryCount = 0
success = False
ser = None
particle_id = None
shield_id = None
while retryCount < 50 and not success:
    try:
        ser = serial.Serial(electron_port, 9600, timeout=10)
        version = ser.readline().strip()
        version = ser.readline().strip()
        version = ser.readline().strip()
        particle_id = version.split(",")[0]
        shield_id = version.split(",")[1]
        #sanity check the values
        if(shield_id.find('FFFFF') != -1 or shield_id.find('91FF') != -1 or len(particle_id) != 24):
            print("Got invalid values - retrying")
            ser.close()
            retryCount += 1
        else:
            success = True
    except Exception as e:
        print(e)
        retryCount += 1

if retryCount == 50:
    print("Failed to read the core and shield IDs. Too many retries")
    sys.exit(1)

print("Found particle:shield " + particle_id+":"+shield_id)
print("")

time_str = datetime.datetime.now().strftime("%I:%M%p on %B %d, %Y")

with open("device_list.txt", "a") as myfile:
        myfile.write(particle_id + "," + shield_id + "," + time_str + "\n")
print("Wrote to local log")
print("")
print("Appending to google devices list...")
append(time_str,particle_id,shield_id, str(args.product))
print("Success.")
print("")
with open('particle-config.json') as config_file:
    particle_config = yaml.safe_load(config_file)

print("Adding device to particle cloud..")
r = requests.post("https://api.particle.io/v1/products/" + str(args.product) +
                    "/devices?access_token=" + particle_config['authToken'],
                    data = {'id':particle_id})
resp = json.loads(r.text)
if 'updated' in resp:
    if(resp['updated'] == 1):
        print("Adding device to product succeeded.")
    else:
        if(len(resp['nonmemberDeviceIds']) == 0 and len(resp['invalidDeviceIds']) == 0):
            print("Device already present in product - continuing.")
        else:
            print("Adding device to product failed")
            print(resp)
            sys.exit(1)
else:
    print("Adding device to product failed")
    print(resp)
    sys.exit(1)

print("")
print("Printing stickers...")
def print_small(particle_id,shield_id):

    img = Image.new('L', (100, 450), "white")
    qr_basewidth = 80
    big_code = pyqrcode.create('F:' + particle_id+':'+shield_id, error='L', version=4)
    big_code.png('code.png', scale=12, module_color=[0, 0, 0, 128], background=[0xff, 0xff, 0xff, 0xff])
    qr_img = Image.open('code.png','r')
    qr_img = qr_img.convert("RGBA")
    qr_wpercent = (qr_basewidth/float(qr_img.size[0]))
    qr_hsize = int((float(qr_img.size[1])*float(qr_wpercent)))
    qr_img = qr_img.resize((qr_basewidth,qr_hsize), Image.ANTIALIAS)
    qr_img_w, qr_img_h = qr_img.size
    qr_img=qr_img.rotate(90,expand=1)
    img.paste(qr_img,(0,150))#,qr_img_w,qr_img_h))
    img.paste(qr_img,(0,370))#,qr_img_w,qr_img_h))

    txt = Image.new('L',(225,15),"white")
    d = ImageDraw.Draw(txt)
    d.text( (0,0), particle_id, font=shield_fnt, fill=0)
    w=txt.rotate(90, expand=1)
    img.paste(w, (70,220))
    img.paste(w, (70,0))

    txt = Image.new('L',(225,15),"white")
    d = ImageDraw.Draw(txt)
    d.text( (0,0), shield_id, font=shield_fnt, fill=0)
    w=txt.rotate(90, expand=1)
    img.paste(w, (85,220))
    img.paste(w, (85,0))


    img.save('./gen/small_logo_gen.png')
    os.system('brother_ql_create --model QL-800 ./gen/small_logo_gen.png -r 90 > ./gen/small_'+str(particle_id) + ":" +
                str(shield_id) +'.bin')
    os.system('brother_ql_print --backend pyusb ./gen/small_'+str(particle_id) + ":" + str(shield_id) + '.bin')

def print_case(particle_id, shield_id):
    img = Image.new('L', (420, 630), "white")
    qr_basewidth = 350
    big_code = pyqrcode.create('F:' + particle_id + ":" + shield_id, error='L', version=4)
    big_code.png('code.png', scale=12, module_color=[0, 0, 0, 128], background=[0xff, 0xff, 0xff, 0xff])
    qr_img = Image.open('code.png','r')
    qr_img = qr_img.convert("RGBA")
    qr_wpercent = (qr_basewidth/float(qr_img.size[0]))
    qr_hsize = int((float(qr_img.size[1])*float(qr_wpercent)))
    qr_img = qr_img.resize((qr_basewidth,qr_hsize), Image.ANTIALIAS)
    qr_img_w, qr_img_h = qr_img.size
    qr_img=qr_img.rotate(90,expand=1)
    img.paste(qr_img,(35,45))#,qr_img_w,qr_img_h))

    txt = Image.new('L',(500,35), "white")
    d = ImageDraw.Draw(txt)
    d.text( (0,0), 'F:' + particle_id, font=case_fnt_small, fill=0)
    w,h = d.textsize('F:' + particle_id, font=case_fnt_small)
    img.paste(txt,((420-w)/2,425))

    txt2 = Image.new('L',(500,35),"white")
    d = ImageDraw.Draw(txt2)
    d.text( (0,0), shield_id, font=case_fnt_larger, fill=0)
    w,h = d.textsize(shield_id, font=case_fnt_larger)
    img.paste(txt2, ((420-w)/2,380))

    txt7 = Image.new('L',(500,35),"white")
    d = ImageDraw.Draw(txt7)
    d.text( (0,0), "DO NOT UNPLUG", font=case_fnt_bold, fill=0)
    w,h = d.textsize("DO NOT UNPLUG", font=case_fnt_bold)
    img.paste(txt7, ((420-w)/2,470))

    txt10 = Image.new('L',(500,35),"white")
    d = ImageDraw.Draw(txt10)
    d.text( (0,0), "KEEP OUTLET ON", font=case_fnt_bold, fill=0)
    w,h = d.textsize("KEEP OUTLET ON", font=case_fnt_bold)
    img.paste(txt10, ((420-w)/2,505))

    txt8 = Image.new('L',(500,35),"white")
    d = ImageDraw.Draw(txt8)
    d.text( (0,0), "Call Kwame for questions:", font=case_fnt, fill=0)
    w,h = d.textsize("Call Kwame for questions:", font=case_fnt)
    img.paste(txt8, ((420-w)/2,560))

    txt9 = Image.new('L',(500,35),"white")
    d = ImageDraw.Draw(txt9)
    d.text( (0,0), "+233 024-653-6896", font=case_fnt, fill=0)
    w,h = d.textsize("+233 024-653-6896", font=case_fnt)
    img.paste(txt9, ((420-w)/2,595))

    txt3 = Image.new('L',(500,80),"white")
    d = ImageDraw.Draw(txt3)
    d.text( (0,0), "DumsorWatch", font=case_fnt_larger, fill=0)
    w,h = d.textsize("DumsorWatch", font=case_fnt_larger)
    img.paste(txt3, ((420-w)/2,0))

    img.save('./gen/case_logo_gen.png')
    os.system('brother_ql_create --model QL-800 ./gen/case_logo_gen.png -r 90 > ./gen/'+str(particle_id) + ":" + str(shield_id)+'.bin')
    os.system('brother_ql_print --backend pyusb ./gen/'+str(particle_id) + ":" + str(shield_id) + '.bin')

print_case(particle_id,shield_id)
print_small(particle_id,shield_id)

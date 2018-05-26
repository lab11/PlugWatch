from PIL import Image, ImageDraw, ImageFont, ImageOps
import pyqrcode
import argparse
import glob
import re
import os
import time
import pyscreen
import serial
import datetime

shield_fnt = ImageFont.truetype('/Library/Fonts/Arial.ttf', 18)
case_fnt = ImageFont.truetype('/Library/Fonts/Arial.ttf', 30)
case_fnt_l = ImageFont.truetype('/Library/Fonts/Arial.ttf', 70)

ports = glob.glob('/dev/tty.u*')
cur_max = 0
electron_port = ''
for port in ports:
    num = int(re.findall(r"([0-9.]*[0-9]+)",port)[0])
    if num > cur_max:
        cur_max = num
        electron_port = port

#os.system("stty -f " + electron_port + " 14400") #go to dfu
#os.system("particle update") #update
#time.sleep(30) #wait for serial to come back
os.system("stty -f " + electron_port + " 14400") #go to dfu
os.system("particle flash --usb first.bin")
time.sleep(20)

ser=serial.Serial(electron_port, 9600)
version = ser.readline().strip()
version = ser.readline().strip()
version = ser.readline().strip()
version = ser.readline().strip()
version = ser.readline().strip()
particle_id = version.split(",")[0]
shield_id = version.split(",")[1]


time_str = datetime.datetime.now().strftime("%I:%M%p on %B %d, %Y")
with open("device_list.txt", "a") as myfile:
        myfile.write(particle_id + "," + shield_id + "," + time_str + "\n")

print "PRINTING: " + particle_id + ":" + shield_id

def print_small(msg,copy):
    img = Image.open('blank.png') #.new('RGBA', (135, 90), color = (255, 255, 0)
    
    qr_basewidth = 150
    big_code = pyqrcode.create(str(msg), error='L', version=2)
    big_code.png('code.png', scale=12, module_color=[0, 0, 0, 128], background=[0xff, 0xff, 0xff, 0xff])
    qr_img = Image.open('code.png','r')
    qr_img = qr_img.convert("RGBA")
    qr_wpercent = (qr_basewidth/float(qr_img.size[0]))
    qr_hsize = int((float(qr_img.size[1])*float(qr_wpercent)))
    qr_img = qr_img.resize((qr_basewidth,qr_hsize), Image.ANTIALIAS)
    qr_img_w, qr_img_h = qr_img.size
    qr_img=qr_img.rotate(90,expand=1)
    img.paste(qr_img,(-14,575))#,qr_img_w,qr_img_h))
    
    txt = Image.new('L',(500,500))
    d = ImageDraw.Draw(txt)
    d.text( (0,0), msg, font=shield_fnt, fill=255)
    w=txt.rotate(90, expand=1)
    start = 75
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (120,start+140),  w)

    img_final = Image.open('blank.png')
    spacer = 245
    for x in xrange(0,copy):
        cur_spacer = x*spacer*-1
        print cur_spacer
        img_final.paste(img, (0,cur_spacer))


    img_final.save('./gen/logo_gen.png')
    os.system('brother_ql_create --model QL-800 ./gen/logo_gen.png -r 90 > ./gen/'+str(msg)+'.bin')
    os.system('brother_ql_print ./gen/'+str(msg)+'.bin usb://0x4f9:0x209b')

def print_case(particle_id, shield_id):
    img = Image.open('case_blank.png') #.new('RGBA', (135, 90), color = (255, 255, 0)
    txt = Image.new('L',(500,500))
    d = ImageDraw.Draw(txt)
    d.text( (0,0), particle_id, font=case_fnt, fill=255)
    w=txt.rotate(90, expand=1)
    start = 75
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (10,start),  w)
    
    txt2 = Image.new('L',(500,500))
    d = ImageDraw.Draw(txt2)
    d.text( (0,0), shield_id, font=case_fnt, fill=255)
    w=txt2.rotate(90, expand=1)
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (50,start),  w)
   
    txt3 = Image.new('L',(500,500))
    d = ImageDraw.Draw(txt3)
    d.text( (0,0), "THIS SIDE UP", font=case_fnt_l, fill=255)
    w=txt3.rotate(90, expand=1)
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (170,start+140), w)
   
    txt6 = Image.new('L',(500,500))
    d = ImageDraw.Draw(txt6)
    d.text( (0,0), "POWERWATCH REV 3", font=case_fnt, fill=255)
    w=txt6.rotate(90, expand=1)
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (90,start), w)

    qr_basewidth = 150
    big_code = pyqrcode.create(particle_id + ":" + shield_id, error='L', version=4)
    big_code.png('code.png', scale=12, module_color=[0, 0, 0, 128], background=[0xff, 0xff, 0xff, 0xff])
    qr_img = Image.open('code.png','r')
    qr_img = qr_img.convert("RGBA")
    qr_wpercent = (qr_basewidth/float(qr_img.size[0]))
    qr_hsize = int((float(qr_img.size[1])*float(qr_wpercent)))
    qr_img = qr_img.resize((qr_basewidth,qr_hsize), Image.ANTIALIAS)
    qr_img_w, qr_img_h = qr_img.size
    qr_img=qr_img.rotate(90,expand=1)

    img.paste(qr_img,(-5,575))#,qr_img_w,qr_img_h))
    img.save('./gen/logo_gen.png')
    os.system('brother_ql_create --model QL-800 ./gen/logo_gen.png -r 90 > ./gen/'+str(particle_id) + ":" + str(shield_id)+'.bin')
    os.system('brother_ql_print ./gen/'+str(particle_id) + ":" + str(shield_id) + '.bin usb://0x4f9:0x209b')

print_case(particle_id,shield_id)
print_small(shield_id,1)
print_small(particle_id,3)

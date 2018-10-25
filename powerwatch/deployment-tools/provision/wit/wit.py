from PIL import Image, ImageDraw, ImageFont, ImageOps
import pyqrcode
import argparse
import glob
import calendar
import re
import os
import time
import pyscreen
import serial
import datetime

shield_fnt = ImageFont.truetype('/Library/Fonts/Arial.ttf', 18)
case_fnt = ImageFont.truetype('/Library/Fonts/Arial.ttf', 30)
case_fnt_l = ImageFont.truetype('/Library/Fonts/Arial.ttf', 70)

product_a_id = 7008
product_b_id = 7009



time_str = datetime.datetime.now().strftime("%I:%M%p on %B %d, %Y")

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


ports = glob.glob('/dev/tty.u*')
cur_max = 0
electron_port = ''
for port in ports:
    num = int(re.findall(r"([0-9.]*[0-9]+)",port)[0])
    if num > cur_max:
        cur_max = num
        electron_port = port

while 1:
    ser=serial.Serial(port=electron_port, baudrate=115200)
    
    mac = ""
    while len(mac) <= 0:
        version = ser.readline().strip()
        print version
        if "MAC" in version:
            mac = version.split(" ")[-1]
            break
    print "found mac: " + mac
    print "taking samples"
    sample_cnt = 0
    while sample_cnt < 45:
        version = ser.readline().strip()
        if "Disconnected." in version:
            #print "\t " + version + " : skipping"
            continue
        elif "MAC" in version:
            #print "\t " + version + " : skipping"
            continue
        else:
            cur_time = calendar.timegm(time.gmtime())
            full_str = str(sample_cnt) + "\t" + str(version) + " " + str(mac) + " " + str(cur_time)
            print full_str
            with open("wit_samples.txt", "a") as myfile:
                myfile.write(full_str + "\n")
            sample_cnt = sample_cnt + 1
    print_small(mac,1)
    exit()

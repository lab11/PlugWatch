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
from psheets import *

shield_fnt = ImageFont.truetype('/Library/Fonts/Arial.ttf', 18)
case_fnt = ImageFont.truetype('/Library/Fonts/Arial.ttf', 30)
case_fnt_l = ImageFont.truetype('/Library/Fonts/Arial.ttf', 40)

def print_case(particle_id, shield_id):
    img = Image.open('case_blank.png') #.new('RGBA', (135, 90), color = (255, 255, 0)

    txt = Image.new('L',(500,500))
    d = ImageDraw.Draw(txt)
    d.text( (0,0), "FOR HELP CONTACT:", font=case_fnt_l, fill=255)
    w=txt.rotate(90, expand=1)
    start = 75+140
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (10,start-35),  w)
    
    txt2 = Image.new('L',(500,500))
    d = ImageDraw.Draw(txt2)
    d.text( (0,0), "dumsorwatch@gmail.com" , font=case_fnt_l, fill=255)
    w=txt2.rotate(90, expand=1)
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (70,start),  w)
   
    txt3 = Image.new('L',(500,500))
    d = ImageDraw.Draw(txt3)
    d.text( (0,0), "or", font=case_fnt_l, fill=255)
    w=txt3.rotate(90, expand=1)
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (120,start-225), w)
   
    txt6 = Image.new('L',(500,500))
    d = ImageDraw.Draw(txt6)
    d.text( (0,0), "0246 536 896", font=case_fnt_l, fill=255)
    w=txt6.rotate(90, expand=1)
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (170,start-110), w)

    img.save('./gen/logo_gen.png')
    os.system('brother_ql_create --model QL-800 ./gen/logo_gen.png -r 90 > ./gen/' + "help" + '.bin')
    os.system('brother_ql_print ./gen/'+ 'help.bin usb://0x4f9:0x209b')


for x in xrange(0,180):
    print_case(-1,-1)

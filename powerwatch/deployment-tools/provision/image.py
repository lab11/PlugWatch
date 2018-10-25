from PIL import Image, ImageDraw, ImageFont, ImageOps
import pyqrcode
import argparse
import os

fnt = ImageFont.truetype('/Library/Fonts/Arial.ttf', 10)
m_fnt = ImageFont.truetype('/Library/Fonts/Arial.ttf', 10)
l_fnt = ImageFont.truetype('/Library/Fonts/Arial.ttf', 100)

for x in range(1, 41):
    #api_val = "grid.watch/provision/strip/"+str(x);
    particle_id = str(x)
    #print api_val
    img = Image.open('blank.png') #.new('RGBA', (135, 90), color = (255, 255, 0))
    #img = Image() 
    txt = Image.new('L',(250,250))
    d = ImageDraw.Draw(txt)
    d.text( (0,0), particle_id, font=l_fnt, fill=255)
    w=txt.rotate(90, expand=1)
    start = -80
    spacer = 180
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (-5,start),  w)
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (-5,start+spacer),  w)
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (-5,start+spacer+spacer),  w)
    img.paste( ImageOps.colorize(w, (0,0,0), (0,0,0)), (-5,start+spacer+spacer+spacer),  w)

    #qr_basewidth = 150
    #big_code = pyqrcode.create(str(api_val), error='L', version=2)
    #big_code.png('code.png', scale=12, module_color=[0, 0, 0, 128], background=[0xff, 0xff, 0xff, 0xff])
    #qr_img = Image.open('code.png','r')
    #qr_img = qr_img.convert("RGBA")
    #qr_wpercent = (qr_basewidth/float(qr_img.size[0]))
    #qr_hsize = int((float(qr_img.size[1])*float(qr_wpercent)))
    #qr_img = qr_img.resize((qr_basewidth,qr_hsize), Image.ANTIALIAS)
    #qr_img_w, qr_img_h = qr_img.size
    #qr_img=qr_img.rotate(90,expand=1)

    #img.paste(qr_img,(30,500))#,qr_img_w,qr_img_h))
    img.save('./gen/logo_gen.png')
    os.system('brother_ql_create --model QL-800 ./gen/logo_gen.png -r 90 > ./gen/'+str(x)+'.bin')
    os.system('brother_ql_print ./gen/'+str(x)+'.bin usb://0x4f9:0x209b') 
    os.system('brother_ql_print ./gen/'+str(x)+'.bin usb://0x4f9:0x209b') 

import glob
import re
import os
import time
import pyscreen
import serial

ports = glob.glob('/dev/tty.u*')
cur_max = 0
electron_port = ''
for port in ports:
    num = int(re.findall(r"([0-9.]*[0-9]+)",port)[0])
    if num > cur_max:
        cur_max = num
        electron_port = port
os.system("stty -f " + electron_port + " 14400") #go to dfu
os.system("particle keys save test.der") #save key
os.system("particle update") #update
time.sleep(5) #wait for serial to come back
os.system("stty -f " + electron_port + " 28800") #put into listening mode
ser=serial.Serial(electron_port, 28800)
ser.write("v\r") 
version = ser.readline().strip().split(":")[-1].strip()
print version
ser.write("i\r")
while True:
    response = ser.readline().strip()
    data = response.split(":")[-1].strip()
    if 'ICC' in response:
            iccid = data
            break
    if 'IMEI' in response:
            imei = data
    if 'ID' in response:
            device_id = data
print iccid
print imei
print device_id
ser.write("s\r")
dump = ser.readline().strip()
print dump



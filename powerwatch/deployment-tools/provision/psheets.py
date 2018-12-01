import pygsheets
import datetime
import time
import sys

try:
    gc = pygsheets.authorize()
except:
    print("Pysheets user not authorized, follow README instructions. Exiting")
    sys.exit(1)

sh = gc.open('PowerWatch Devices - Deployment Table Hardware Mapping')

product_a_cnt = 0
product_b_cnt = 0
product_c_cnt = 0
product_d_cnt = 0

product_a_id = 7008
product_b_id = 7009
product_c_id = 7010
product_d_id = 7011

def append(time_str,device_id,shield_id,product_id):
    global product_a_cnt, product_b_cnt, product_c_cnt, product_d_cnt
    wks = sh.sheet1
    cnt = 1
    product_a_cnt = 0
    product_b_cnt = 0
    product_c_cnt = 0
    product_d_cnt = 0
    for row in wks: #check for duplicate device id or shield id
        d_id = row[1]
        s_id = row[2]
        if str(d_id) == str(device_id):
            print "DUPLICATE DEVICE"
            return -1
        if str(s_id) == str(shield_id):
            print "DUPLICATE SHIELD"
            return -1
    for row in wks: #check for overage in product and get the index for the append
        cnt = cnt+1
        p_id = row[3]
        if str(p_id) == str(product_a_id):
            product_a_cnt = product_a_cnt + 1
        if str(p_id) == str(product_b_id):
            product_b_cnt = product_b_cnt + 1
        if str(p_id) == str(product_c_id):
            product_c_cnt = product_c_cnt + 1
        if str(p_id) == str(product_d_id):
            product_d_cnt = product_d_cnt + 1
    if str(product_id) == str(product_a_id):
        if product_a_cnt >= 250:
            print "PRODUCT A LIMIT REACHED. REFLASH... EXITING"
            return -1
        product_a_cnt = product_a_cnt + 1
    if str(product_id) == str(product_b_id):
        if product_b_cnt >= 250:
            print "PRODUCT B LIMIT REACHED. REFLASH... EXITING"
            return -1
        product_b_cnt = product_b_cnt + 1
    if str(product_id) == str(product_c_id):
        if product_c_cnt >= 250:
            print "PRODUCT C LIMIT REACHED. REFLASH... EXITING"
            return -1
        product_c_cnt = product_c_cnt + 1
    if str(product_id) == str(product_d_id):
        if product_d_cnt >= 250:
            print "PRODUCT D LIMIT REACHED. REFLASH... EXITING"
            return -1
        product_d_cnt = product_d_cnt + 1

    r = "A"+str(cnt)+":"+"D"+str(cnt)
    wks.update_values(crange=r,values=[[time_str, device_id, shield_id, product_id]])



#time_str = datetime.datetime.now().strftime("%I:%M%p on %B %d, %Y")
#append(time_str, 1, 2, 7008)
#for x in xrange(0,100):
#    append(time_str,x,22222,product_a_id)

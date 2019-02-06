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

product_cnt = 0

def append(time_str,device_id,shield_id,product_id):
    #global product_a_cnt, product_b_cnt, product_c_cnt, product_d_cnt
    global product_cnt
    wks = sh.sheet1
    cnt = 1
    for row in wks: #check for duplicate device id or shield id
        d_id = row[1]
        s_id = row[2]
        if str(d_id) == str(device_id):
            print "Device ID already present in spreadsheet"
            return -1
        if str(s_id) == str(shield_id):
            print "Shield ID already present in spreadsheet"
            return -1
    for row in wks: #check for overage in product and get the index for the append
        cnt = cnt+1
        p_id = row[3]
        if str(p_id) == str(product_id)
            product_cnt += 1

    if product_cnt >= 100:
        print "PRODUCT LIMIT REACHED. REFLASH... EXITING"
        return -1

    r = "A"+str(cnt)+":"+"D"+str(cnt)
    wks.update_values(crange=r,values=[[time_str, device_id, shield_id, product_id]])



#time_str = datetime.datetime.now().strftime("%I:%M%p on %B %d, %Y")
#append(time_str, 1, 2, 7008)
#for x in xrange(0,100):
#    append(time_str,x,22222,product_a_id)

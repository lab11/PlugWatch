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
        p_id = row[3]
        if str(d_id) == str(device_id):
            if str(s_id) == str(shield_id) and str(p_id) == str(product_id):
                print("Device ID already present in spreadsheet with same shield ID and product_ID, skipping")
                return -1;
            else:
                print("Device ID already present in spreadsheet.")
                print("Old shield ID: {}\tNew shield ID: {}".format(s_id,shield_id))
                print("Old product ID: {}\tNew product ID: {}".format(p_id,product_id))
                print()
                answer = raw_input("Would you like to update this device in google sheets? [Y\n]")
                if(answer == 'Y' or answer == 'Yes' or answer == 'y' or answer == 'yes'):
                    r = "A"+str(cnt)+":"+"D"+str(cnt)
                    wks.update_values(crange=r,values=[[time_str, device_id, shield_id, product_id]])
                    return 0;
                else:
                    return -2;

        cnt += 1;

    print("Adding new device ID to google sheet")
    r = "A"+str(cnt)+":"+"D"+str(cnt)
    wks.update_values(crange=r,values=[[time_str, device_id, shield_id, product_id]])
    return 0;

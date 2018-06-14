#!/usr/bin/env python

import datetime
import time
import psycopg2
import yaml
import argparse
import csv

#parse the filename from the survey
parser = argparse.ArgumentParser();
parser.add_argument('filename',type=str);
args = parser.parse_args();

#open the postgres database
with open('postgres_config.json') as config_file:
    config = yaml.safe_load(config_file)

connection = psycopg2.connect(dbname=config['database'], user=config['user'], host=config['host'], password=config['password'])
cursor = connection.cursor();

#read in the survey line by line, try to find the device in the devices table, get the core_id, add it to the deployment table
with open(args.filename) as survey:
    reader = csv.reader(survey)
    reader.next()
    for row in reader:
        #get preliminary information

        site = int(row[0])
        device_id = row[1]
        orig_id = device_id;

        if(row[3] == '0'):
            wit = False
        else:
            wit = True

        time = row[4]

        gps_lat_acc = None
        if(len(row[5]) > 0):
            gps_lat_acc = float(row[5])

        gps_long_acc = None
        if(len(row[6]) > 0):
            gps_long_acc = float(row[6])

        gps_lat = None
        if(len(row[7]) > 0):
            gps_lat = float(row[7])

        gps_long = None
        if(len(row[8]) > 0):
            gps_long = float(row[8])

        core_id = None
        #check length of id
        while(core_id == None):
            if(len(device_id) == 24):
                #this must be a core_id
                print('Loooking up core ID {}'.format(device_id));
                cursor.execute('SELECT * from devices where core_id = %s',(device_id,))
                result = cursor.fetchone()
                if(len(result) == 0):
                    print('No exact core match - looking for partial matches');
                    break;
                else:
                    print('Got match for core ID {} with shield ID {}'.format(result[0], result[1]));
                    core_id = result[0];
            elif(len(device_id) == 12):
                #this must be a shield_id
                print('Loooking up shield ID {}'.format(device_id));
                cursor.execute('SELECT * from devices where shield_id = %s',(device_id,))
                result = cursor.fetchone()
                if(len(result) == 0):
                    print('No exact shield match - looking for partial matches');
                    break;
                else:
                    print('Got match for shield ID {} with core ID {}'.format(result[1],result[0]));
                    core_id = result[0];
            else:
                #something else - ask the script runner to fix it
                print('{} is neither shield ID nor core ID'.format(device_id));
                inp = raw_input('Enter user corrected core ID or shield ID: ')
                if(inp == 'skip'):
                    break;
                device_id = inp
        
        if(core_id != None):
            gps_final_lat = None
            gps_final_long = None
            if(gps_lat_acc != None and gps_long_acc != None):
                gps_final_lat = gps_lat_acc
                gps_final_long = gps_long_acc
            elif(gps_lat != None and gps_long != None):
                gps_final_lat = gps_lat
                gps_final_long = gps_long
                
            cursor.execute('INSERT into deployment (core_id, site, latitude, longitude, deployment_time, has_wit) VALUES (%s, %s, %s, %s, %s, %s)',
                                (core_id, site, gps_final_lat, gps_final_long, time, wit))

connection.commit()
cursor.close()
connection.close()

        

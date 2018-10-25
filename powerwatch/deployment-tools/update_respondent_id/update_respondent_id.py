#!/usr/bin/env python

from datetime import datetime
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

        if(row[3] == 'Yes'):
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

        respondent_id = int(row[7]);

        #check to see if this devices has already been inserted based on the timestamp
        cursor.execute('SELECT * from deployment where deployment_time = %s',(time,))
        result = cursor.fetchone()
        if(result != None):
            core_id = result[0];
            cursor.execute('UPDATE deployment set respondent_id=%s where core_id=%s',(respondent_id,core_id));
            continue

connection.commit()
cursor.close()
connection.close()

        

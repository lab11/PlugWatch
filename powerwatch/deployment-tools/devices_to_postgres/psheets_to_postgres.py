#!/usr/bin/env python

import pygsheets
import datetime
import time
import psycopg2
import yaml

with open('postgres_config.json') as config_file:
    config = yaml.safe_load(config_file)


connection = psycopg2.connect(dbname=config['database'], user=config['user'], host=config['host'], password=config['password'])
cursor = connection.cursor();

gc = pygsheets.authorize()
sh = gc.open('PowerWatch Devices - Deployment Table Hardware Mapping')

wks = sh.sheet1
first = True;
for row in wks:
    if(first):
        first = False
        continue

    #get the information
    core_id = row[1]
    shield_id = row[2]
    product_id = row[3]

    if(core_id == '' or shield_id == ''):
        continue

    #insert it into postgres
    print("Adding core_id: {}, shield_id: {}, product_id: {}".format(core_id, shield_id, product_id))
    cursor.execute('INSERT INTO devices (core_id, shield_id, product_id) VALUES (%s, %s, %s)', (core_id, shield_id, product_id))

connection.commit();
cursor.close();
cursor.close();

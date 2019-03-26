#!/usr/bin/env python

import pygsheets
import datetime
import time
import psycopg2
import yaml
import csv

with open('postgres_config.json') as config_file:
    config = yaml.safe_load(config_file)


connection = psycopg2.connect(dbname=config['database'], user=config['user'], host=config['host'], password=config['password'])
cursor = connection.cursor();

#Add the achimota payments table
#cursor.execute('DROP table achimota_respondents')
cursor.execute('CREATE TABLE achimota_respondents(respondent_id TEXT, respondent_firstname TEXT, respondent_surname TEXT, respondent_popularname TEXT, site_id INTEGER, phone_number TEXT, carrier TEXT, location_latitude DOUBLE PRECISION, location_longitude DOUBLE PRECISION, pilot_survey_time TIMESTAMPTZ, powerwatch BOOLEAN, powerwatch_id TEXT)')

#now for each row in the korba table, write it to the payments table
f = open('respondents.csv', 'r')
reader = csv.reader(f, delimiter=',')

next(reader)
for row in reader:

    respondent_id = row[0]
    respondent_firstname = row[1]
    respondent_surname = row[2]
    respondent_popularname = row[3]
    site_id = row[4]
    pilot_survey_time = row[5]

    location_latitude = None
    if row[6] != '':
        location_latitude = row[6]

    location_longitude = None
    if row[7] != '':
        location_longitude = row[7]

    phone_number = row[8]
    carrier = None
    if(row[9] == '1'):
        carrier = 'MTN'
    elif(row[9] == '2'):
        carrier = 'Airtel'
    elif(row[9] == '3'):
        carrier = 'Vodaphone'
    elif(row[9] == '4'):
        carrier = 'Tigo'
    elif(row[9] == '5'):
        carrier = 'GLO'

    downloaded = row[12]
    powerwatch_id = row[15]
    powerwatch = False
    if(powerwatch_id != ''):
        powerwatch = True
    else:
        powerwatch_id = None

    cursor.execute('INSERT INTO achimota_respondents (respondent_id, respondent_firstname, respondent_surname, respondent_popularname, site_id, phone_number, carrier, location_latitude, location_longitude, pilot_survey_time, powerwatch, powerwatch_id) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)', (respondent_id, respondent_firstname, respondent_surname, respondent_popularname, site_id, phone_number, carrier, location_latitude, location_longitude, pilot_survey_time, powerwatch, powerwatch_id))

connection.commit();
cursor.close();
cursor.close();

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
try:
    cursor.execute('DROP table achimota_payments')
    cursor.execute('CREATE TABLE achimota_payments(phone_number TEXT, carrier TEXT, time_created TIMESTAMPTZ, time_submitted TIMESTAMPTZ, amount DOUBLE PRECISION, incentive_id TEXT, incentive_type TEXT, payment_attempt INTEGER, status TEXT, transaction_id TEXT, external_transaction_id TEXT)')
except:
    pass

#now for each row in the korba table, write it to the payments table
f = open('korba_transactions.csv', 'r')
reader = csv.reader(f, delimiter=',')

next(reader)
for row in reader:
    print(row[0])
    if row[9].split(' ')[0] == 'OVA':
        continue

    phone_number = row[7]
    carrier = row[6]
    amount = float(row[8])
    time = row[2]

    explanation = row[9]
    reason = None
    incentive_type = None
    if(len(explanation.split(' ')) >= 3):
        reason = explanation.split(' ')[2]
        if(len(explanation.split(' '))  >= 3 and len(reason.split('_')) >= 3):
            incentive_type = reason.split('_')[2]

    #we don't know incentive IDs for old payments
    incentive_id = None

    #we don't know payment attempts for old payments
    payment_attempt = None

    status = row[10]
    transaction_id = row[1]
    external_transaction_id = row[15]

    print('Inserting phone_number {}, carrier {}, time {}, amount {}, incentive_id {}, incentive_type {}, payment_attempt {}, status {}, transaction_id {}, external_transaction_id {}'.format(phone_number, carrier, time, amount, incentive_id, incentive_type, payment_attempt, status, transaction_id, external_transaction_id))
    cursor.execute('INSERT INTO achimota_payments (phone_number, carrier, time_created, time_submitted, amount, incentive_id, incentive_type, payment_attempt, status, transaction_id, external_transaction_id) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)', (phone_number, carrier, None, time, amount, incentive_id, incentive_type, payment_attempt, status, transaction_id, external_transaction_id))

connection.commit();
cursor.close();
cursor.close();

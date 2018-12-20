#!/usr/bin/env python3

# looking for big cluster of events and plotting GPS

import csv
import math
import pprint
import sys
import time

import itertools
import matplotlib.pyplot as plt
import numpy as np

try:
    dw_file = sys.argv[1]
except:
    print()
    print('Usage: {} dw_fils.csv'.format(sys.argv[0]))
    print()
    raise

# Threshold is time between events to define a cluster
# Size is minimum number of events in a cluster to be considered an outage
THRES_DW = 15*1000
CLUSTER_SIZE_DW = 3

print('Config:')
print('DW\tCluster sep {}, cluster size {}'.format(THRES_DW/1000, CLUSTER_SIZE_DW))
print()

# Load DW Data
times_dw = []
times_ref = {}

with open(dw_file) as csvfile:
    reader = csv.reader(csvfile)
    headers = next(reader, None)
    phones = {}
    phone_power_state = {}
    phone_del_cnt = {}
    for row in reader:
        # Not every row even has an event timestamp
        try:
            eventtime = int(row[headers.index('event_time')])
        except:
            continue

        # Lie, use IMEI as it's always present, phone can be ''
        phone_num = row[headers.index('phone_imei')]
        evt_type = row[headers.index('type')]

        # Assume phones start powered
        if phone_num not in phone_power_state:
            phone_power_state[phone_num] = True

        if evt_type == 'plugged':
            phone_power_state[phone_num] = True
        if evt_type != 'unplugged':
            continue
        phone_power_state[phone_num] = False

        # Keep track of the number of records we delete due to duplication
        if phone_num not in phone_del_cnt:
            phone_del_cnt[phone_num] = 0

        # Filter out duplicate events from the same phone
        if phone_num in phones:
            FIVE_MIN = 1000*60*5
            ONE_HOUR = 1000*60*60
            if (eventtime - phones[phone_num]) < (FIVE_MIN):
                # Keep sliding this forward so one phone can't keep recreating
                # clusters
                #phones[phone_num] = eventtime
                phone_del_cnt[phone_num] += 1
                continue
            if (eventtime - phones[phone_num]) < 0:
                # Somehow this phone went back in time? Skip.
                # This shouldn't ever happen, but let's be paranoid
                raise NotImplementedError
        phones[phone_num] = eventtime

        # This is now a unique enough outage event
        times_dw.append(eventtime)
        times_ref[eventtime] = row

        ## Optional, bail out in a shorter window
        #if eventtime > (times_dw[0] + 1000*60*60*24*7):
        #    break
times_dw.sort()

print('DW')
print('times_dw size: ', len(times_dw))

# Print the number of deleted records per phone, sorted by how many per phone
#for phone,cnt in reversed(sorted(phone_del_cnt.items(), key=lambda kv: kv[1])):
#    print('{}\t{}'.format(phone[-3:], cnt))


print('-------')
print('Clustering....')

# Clustering
# https://stackoverflow.com/questions/15800895/finding-clusters-of-numbers-in-a-list
#
# This magic splits into events into clusters where clusters are identified by
# being runs of numbers at least THRES apart. The `res[j]` holds the whole list
# of clusters, while the `r2` is what we actually use and filters down to just
# the clusters that are at least CLUSTER_SIZE.

nd = [0] + list(np.where(np.diff(times_dw) > THRES_DW)[0] + 1) + [len(times_dw)]
a, b = itertools.tee(nd)
next(b, None)
res = {}
r2 = {}
for j, (f, b) in enumerate(zip(a, b)):
    res[j] = times_dw[f:b]
    if len(res[j]) >= CLUSTER_SIZE_DW:
        cluster_times_dw = list(res[j])
        r2[int(np.average(cluster_times_dw))] = cluster_times_dw

print('DW')
print('num clusters of any size', len(res))
print('num clusters of min size', len(r2))

cluster_times_dw = []
cnts_dw = []
cluster_sizes_dw = {}
for time,cluster in r2.items():
    #print(time,cluster)
    t = int(time/1000)
    cluster_times_dw.append(t)
    cnts_dw.append(len(cluster))

    if len(cluster) not in cluster_sizes_dw:
        cluster_sizes_dw[len(cluster)] = 1
    else:
        cluster_sizes_dw[len(cluster)] += 1

    if len(cluster) > 10:
        print('\t{}\t{}'.format(t, len(cluster)))
        s = set()
        for dw in cluster:
            row = times_ref[dw]
            imei = row[headers.index('phone_imei')]
            if imei in s:
                print(s)
                print(imei)
                raise NotImplementedError
            s.add(imei)
            lat = row[headers.index('lat')]
            lng = row[headers.index('lng')]
            if lat != '-1':
                print('\t\t{},{}'.format(lat,lng))

pprint.pprint(cluster_sizes_dw)

#print(cnts_dw)

plt.scatter(cluster_times_dw,cnts_dw)
plt.show()


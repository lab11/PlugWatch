#!/usr/bin/env python3

# looking for number of events within 1 second, 5 seconds, 10 seconds

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
    pw_file = sys.argv[2]
except:
    print()
    print('Usage: {} dw_fils.csv pw_file.csv'.format(sys.argv[0]))
    print()
    raise

# Threshold is time between events to define a cluster
# Size is minimum number of events in a cluster to be considered an outage
THRES_DW = 15*1000
CLUSTER_SIZE_DW = 3

THRES_PW = 300
CLUSTER_SIZE_PW = 3

print('Config:')
print('DW\tCluster sep {}, cluster size {}'.format(THRES_DW/1000, CLUSTER_SIZE_DW))
print('PW\tCluster sep {}, cluster size {}'.format(THRES_PW, CLUSTER_SIZE_PW))
print()

# Load DW Data
times_dw = []

with open(dw_file) as csvfile:
    reader = csv.reader(csvfile)
    headers = next(reader, None)
    fucked_rows = 0
    good_rows = 0
    phones = {}
    phone_power_state = {}
    phone_del_cnt = {}
    for row in reader:
        # Not every row even has an event timestamp
        try:
            eventtime = int(row[headers.index('event_time')])
            good_rows += 1
        except:
            fucked_rows += 1
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

        ## Optional, bail out in a shorter window
        #if eventtime > (times_dw[0] + 1000*60*60*24*7):
        #    break
times_dw.sort()

print('DW')
print('good rows: ', good_rows)
print('bad rows: ', fucked_rows)
print('times_dw size: ', len(times_dw))

# Print the number of deleted records per phone, sorted by how many per phone
#for phone,cnt in reversed(sorted(phone_del_cnt.items(), key=lambda kv: kv[1])):
#    print('{}\t{}'.format(phone[-3:], cnt))

times_pw = []

with open(pw_file) as csvfile:
    reader = csv.reader(csvfile)
    headers = next(reader, None)
    cores = {}
    for row in reader:
        core_id = row[headers.index('core_id')]
        is_powered = True if row[headers.index('is_powered')] == 't' else False

        # Handle new nodes, we get enough repeats that ignoring the first
        # message is fine
        if core_id not in cores:
            cores[core_id] = is_powered
            continue

        # If the power state changes update
        if cores[core_id] != is_powered:
            cores[core_id] = is_powered

            # If the power turned off, note an event
            if is_powered == False:
                time_str = row[headers.index('time')]
                time_str = time_str[:-3].split('.')[0]
                pattern = '%Y-%m-%d %H:%M:%S'
                epoch = int(time.mktime(time.strptime(time_str, pattern)))
                epoch = epoch - 18000
                times_pw.append(epoch)

                ## Optional bail early
                #if epoch > ((times_dw[0] + 1000*60*60*24*7)/1000):
                #    break
times_pw.sort()

print('PW')
print('PW events: ', len(times_pw))


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
for time,cluster in r2.items():
    #print(time,cluster)
    t = int(time/1000)
    cluster_times_dw.append(t)
    cnts_dw.append(len(cluster))

    if len(cluster) > 10:
        print('\t{}\t{}'.format(t, len(cluster)))

#print(cnts_dw)

fig, ax1 = plt.subplots()
ax1.scatter(cluster_times_dw,cnts_dw)
#plt.scatter(cluster_times_dw,cnts_dw)
#plt.show()


nd = [0] + list(np.where(np.diff(times_pw) > THRES_PW)[0] + 1) + [len(times_pw)]
a, b = itertools.tee(nd)
next(b, None)
res = {}
r2 = {}
for j, (f, b) in enumerate(zip(a, b)):
    res[j] = times_pw[f:b]
    if len(res[j]) >= CLUSTER_SIZE_PW:
        cluster_times_pw = list(res[j])
        r2[int(np.average(cluster_times_pw))] = cluster_times_pw

print('PW')
print('num clusters of any size', len(res))
print('num clusters of min size', len(r2))

cluster_times_pw = []
cnts_pw = []
for time,cluster in r2.items():
    if time == -1:
        continue
    #print(time,cluster)
    cluster_times_pw.append(time)
    cnts_pw.append(len(cluster))

    if len(cluster) > 20:
        print('\t{}\t{}'.format(time, len(cluster)))

#print(cnts_pw)

ax2 = ax1.twinx()
ax2.scatter(cluster_times_pw,cnts_pw,c='orange')
#plt.scatter(cluster_times_pw,cnts_pw,c='orange')

for i,t in enumerate(cluster_times_pw):
    if cnts_pw[i] >= 20:
        plt.axvline(x=t,color='orange')

print(np.array(cluster_times_dw))
print(np.array(cluster_times_pw))

# https://stackoverflow.com/questions/2566412/find-nearest-value-in-numpy-array/2566508
def find_nearest(array,value):
    idx = np.searchsorted(array, value, side="left")
    if idx > 0 and (idx == len(array) or math.fabs(value - array[idx-1]) < math.fabs(value - array[idx])):
        return array[idx-1]
    else:
        return array[idx]


print('--------------')
print('PW with nearby DW?')
better60 = 0
better300 = 0
better600 = 0
for pw in cluster_times_pw:
    dw = find_nearest(cluster_times_dw, pw)
    diff = abs(pw-dw)
    print('{}\t{}\t{}\t{}\t{}\t{}'.format(pw, dw, diff, diff <= 60, diff <= 300, diff <= 600))
    if diff <= 60:
        better60 += 1
    if diff <= 300:
        better300 += 1
    if diff <= 600:
        better600 += 1
print()
print('tot {}, bet60 {} {:.2f}%, bet300 {} {:.2f}%, bet600 {} {:.2f}%'.format(
    len(cluster_times_pw),
    better60, 100*better60/len(cluster_times_pw),
    better300, 100*better300/len(cluster_times_pw),
    better600, 100*better600/len(cluster_times_pw)
    ))

print('--------------')
print('DW with nearby PW?')
better60 = 0
better300 = 0
better600 = 0
for dw in cluster_times_dw:
    pw = find_nearest(cluster_times_pw, dw)
    diff = abs(pw-dw)
    #print('{}\t{}\t{}\t{}\t{}\t{}'.format(pw, dw, diff, diff <= 60, diff <= 300, diff <= 600))
    if diff <= 60:
        better60 += 1
    if diff <= 300:
        better300 += 1
    if diff <= 600:
        better600 += 1
print()
print('tot {}, bet60 {} {:.2f}%, bet300 {} {:.2f}%, bet600 {} {:.2f}%'.format(
    len(cluster_times_dw),
    better60, 100*better60/len(cluster_times_dw),
    better300, 100*better300/len(cluster_times_dw),
    better600, 100*better600/len(cluster_times_dw)
    ))

plt.show()

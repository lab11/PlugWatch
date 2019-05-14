#!/usr/bin/env python3

# looking for big cluster of events and plotting GPS

import dateutil
import csv
import math
import pprint
import sys
import time

import itertools
import matplotlib.pyplot as plt
import numpy as np

try:
    pw_file = sys.argv[1]
except:
    print()
    print('Usage: {} pw_fils.csv'.format(sys.argv[0]))
    print()
    raise

# Threshold is time between events to define a cluster
# Size is minimum number of events in a cluster to be considered an outage
THRES_PW = 5*60
CLUSTER_SIZE_PW = 3

print('Config:')
print('PW\tCluster sep {}, cluster size {}'.format(THRES_PW, CLUSTER_SIZE_PW))
print()

# Load PW Data
times_pw = []
times_ref = {}

with open(pw_file) as csvfile:
    reader = csv.reader(csvfile)
    headers = next(reader, None)
    cores = {}
    cores_last = {}
    for row in reader:
        core_id = row[headers.index('core_id')]
        is_powered = True if row[headers.index('is_powered')] == 't' else False

        # Only care about the Accra sensors
        product_id = int(row[headers.index('product_id')])
        if product_id not in (7008, 7009):
            continue

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
                time_str = time_str.split('.')[0]
                date = dateutil.parser.parse(time_str)
                epoch = date.timestamp()

                # Filter out repeate events from same node tighter than
                # clustering
                if core_id not in cores_last:
                    cores_last[core_id] = epoch
                else:
                    if epoch - cores_last[core_id] < THRES_PW:
                        print('filter')
                        cores_last[core_id] = epoch
                        continue
                cores_last[core_id] = epoch

                #if core_id == '35003d001951353339373130':
                #    print('\t',epoch,'\t',time_str)

                # This is a bit of a hack, but really want unique times :/
                if epoch in times_ref:
                    epoch += .1

                times_pw.append(epoch)
                times_ref[epoch] = row

                ## Optional bail early
                #if epoch > ((times_pw[0] + 1000*60*60*24*7)/1000):
                #    break
times_pw.sort()

print('PW')
print('times_pw size: ', len(times_pw))


print('-------')
print('Clustering....')

# Clustering
# https://stackoverflow.com/questions/15800895/finding-clusters-of-numbers-in-a-list
#
# This magic splits into events into clusters where clusters are identified by
# being runs of numbers at least THRES apart. The `res[j]` holds the whole list
# of clusters, while the `r2` is what we actually use and filters down to just
# the clusters that are at least CLUSTER_SIZE.

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
cluster_sizes_pw = {}
big_times = []
for time,cluster in r2.items():
    #print(time,cluster)
    t = time
    cluster_times_pw.append(t)
    cnts_pw.append(len(cluster))

    if len(cluster) not in cluster_sizes_pw:
        cluster_sizes_pw[len(cluster)] = 1
    else:
        cluster_sizes_pw[len(cluster)] += 1

    #if t == 1535074586:
    #    print('PRINTING CLUSTER 1535074586')
    #    for c in cluster:
    #        print(times_ref[c])

    if len(cluster) > 20:
        big_times.append(t)
        print('\t{}\t{}'.format(t, len(cluster)))
        s = set()
        for pw in cluster:
            row = times_ref[pw]
            core_id = row[headers.index('core_id')]
            if core_id in s:
                #print(cluster)
                #print(s)
                #print(core_id)
                #print(row[headers.index('time')])
                #raise NotImplementedError
                print("!!! DUP")
                continue
            s.add(core_id)
            lat = row[headers.index('gps_latitude')]
            lng = row[headers.index('gps_longitude')]
            if lat != '':
                print('\t\t{},{}'.format(lat,lng))

print(cluster_sizes_pw)
for t in big_times:
    print(t)

#print(cnts_pw)

plt.scatter(cluster_times_pw,cnts_pw)
plt.show()


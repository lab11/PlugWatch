#!/usr/bin/env python3

import numpy
import matplotlib
#matplotlib.use('Agg')
import seaborn as sns
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap
import csv
import sys

cmap = ListedColormap(sns.color_palette('colorblind'))

sep_cluster_size = []
sep_event_count = []
sep_outage_count = []
with open('september_histogram.csv') as c:
    csv_reader = csv.reader(c,delimiter=',')
    linecount = 0
    for row in csv_reader:
        if linecount == 0:
            linecount += 1
        else:
            sep_cluster_size.append(int(row[1]))
            sep_event_count.append(float(row[3]))
            sep_outage_count.append(float(row[4]))

jul_cluster_size = []
jul_event_count = []
jul_outage_count = []
with open('july_histogram.csv') as c:
    csv_reader = csv.reader(c,delimiter=',')
    linecount = 0
    for row in csv_reader:
        if linecount == 0:
            linecount += 1
        else:
            jul_cluster_size.append(int(row[1]))
            jul_event_count.append(float(row[3]))
            jul_outage_count.append(float(row[4]))

aug_cluster_size = []
aug_event_count = []
aug_outage_count = []
with open('august_histogram.csv') as c:
    csv_reader = csv.reader(c,delimiter=',')
    linecount = 0
    for row in csv_reader:
        if linecount == 0:
            linecount += 1
        else:
            aug_cluster_size.append(int(row[1]))
            aug_event_count.append(float(row[3]))
            aug_outage_count.append(float(row[4]))

#load the ECG SAIFI
month = []
hv = []
mv = []
lv = []
with open('ecg_saifi.csv') as c:
    csv_reader = csv.reader(c,delimiter=',')
    linecount = 0
    for row in csv_reader:
        if linecount == 0:
            linecount += 1
        else:
            month.append((row[0]))
            hv.append(float(row[1]))
            mv.append(float(row[2]))
            lv.append(float(row[3]))


#calculate monthly PW SAIFI based on cluster size
lv_cutoff = 10
lv_saifi = [0, 0, 0]
hv_saifi = [0, 0, 0]

sep_num_devices = 140
for x in range(0,len(sep_cluster_size)):
    if sep_cluster_size[x] <= lv_cutoff and sep_cluster_size[x]  >= 2:
        lv_saifi[1] += sep_event_count[x]/sep_num_devices
    elif sep_cluster_size[x] > lv_cutoff:
        hv_saifi[1] += sep_event_count[x]/sep_num_devices

aug_num_devices = 141
for x in range(0,len(aug_cluster_size)):
    if aug_cluster_size[x] <= lv_cutoff and aug_cluster_size[x]  >= 2:
        lv_saifi[2] += aug_event_count[x]/aug_num_devices
    elif aug_cluster_size[x] > lv_cutoff:
        hv_saifi[2] += aug_event_count[x]/aug_num_devices

jul_num_devices = 160
for x in range(0,len(jul_cluster_size)):
    if jul_cluster_size[x] <= lv_cutoff and jul_cluster_size[x]  >= 2:
        lv_saifi[0] += jul_event_count[x]/jul_num_devices
    elif jul_cluster_size[x] > lv_cutoff:
        hv_saifi[0] += jul_event_count[x]/jul_num_devices

print(lv_saifi)
print(hv_saifi)
print(month)
print(lv)
print(mv)
print(hv)

plt.figure(figsize=(8,4))
#ECG Data
index = [0.84, 1.84, 2.84]
index3 = [1, 2, 3]
index2 = [1.16, 2.16, 3.16]
plt.bar(index, hv,width=0.3, label="ECG 33KV")
plt.bar(index, mv,width=0.3, label="ECG 11KV",bottom=hv)
plt.bar(index, lv,width=0.3, label="ECG Low Voltage",bottom=[mv[x] + hv[x] for x in range(0,3)])
plt.bar(index2, hv_saifi,width=0.3, label="PW Clusters of > 10")
plt.bar(index2, lv_saifi,width=0.3, label="PW Clusters of <= 10",bottom=hv_saifi)
plt.ylabel("SAIFI",fontsize=16,labelpad=10)
plt.xlabel("Month",fontsize=16,labelpad=10)
plt.xticks(index3,month)
plt.tick_params(axis='x',length=0)
plt.ylim(0,8)
plt.legend(ncol=2)
plt.tight_layout()
plt.savefig('pw_ecg_saifi.png')

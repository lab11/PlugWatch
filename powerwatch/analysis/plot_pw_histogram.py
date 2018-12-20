#!/usr/bin/env python3

import numpy
import matplotlib
#matplotlib.use('Agg')
import seaborn as sns
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap
import csv

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



#now group the outages into sections of 5
sep_bin_list = []
sep_outage_bin_list = []
m = 5
for i in range(0,11):
    count = 0
    o_count = 0
    for x in range(0,len(sep_cluster_size)):
        if sep_cluster_size[x] <= (i+1)*5 and sep_cluster_size[x]  >= 2 and sep_cluster_size[x] > i*5 :
            print
            count += sep_event_count[x]
            o_count += sep_outage_count[x]

    sep_bin_list.append(count)
    sep_outage_bin_list.append(o_count)

aug_bin_list = []
aug_outage_bin_list = []
m = 5
for i in range(0,11):
    count = 0
    o_count = 0
    for x in range(0,len(aug_cluster_size)):
        if aug_cluster_size[x] <= (i+1)*5 and aug_cluster_size[x]  >= 2 and aug_cluster_size[x] > i*5 :
            print
            count += aug_event_count[x]
            o_count += aug_outage_count[x]

    aug_bin_list.append(count)
    aug_outage_bin_list.append(o_count)

jul_bin_list = []
jul_outage_bin_list = []
m = 5
for i in range(0,11):
    count = 0
    o_count = 0
    for x in range(0,len(jul_cluster_size)):
        if jul_cluster_size[x] <= (i+1)*5 and jul_cluster_size[x]  >= 2 and jul_cluster_size[x] > i*5 :
            print
            count += jul_event_count[x]
            o_count += jul_outage_count[x]

    jul_bin_list.append(count)
    jul_outage_bin_list.append(o_count)


plt.figure(figsize=(8,4))
x = [x-0.3 for x in range(0,11)]
plt.bar(x, jul_bin_list,width=0.3, label="July")
x = [x for x in range(0,11)]
plt.bar(x, aug_bin_list,width=0.3, label="August")
x = [x+0.3 for x in range(0,11)]
plt.bar(x, sep_bin_list,width=0.3, label="September")
x = [x for x in range(0,11)]
plt.xticks(x,["2-5","6-10","11-15","16-20","21-25","26-30","31-35","36-40","41-45","46-50","51-55"])
plt.tick_params(axis='x',length=0)
plt.ylabel("Number of Outage Events",fontsize=16,labelpad=10)
plt.xlabel("Number of Devices Detecting Outage",fontsize=16,labelpad=10)
plt.legend()

plt.tight_layout()
plt.savefig('pw_outage_cluster.png')

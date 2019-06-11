#!/usr/bin/env python3

import matplotlib.pyplot as plt
import csv

x = []
y = []

with open('data.csv','r') as csvfile:
    plots = csv.reader(csvfile, delimiter=',')
    first = True
    for row in plots:
        if first:
            first = False
            continue

        x.append(float(row[1]))
        y.append(float(row[2]))

plt.plot(x,y,'ro')
plt.show()

#! /usr/bin/env python3

import os
import sys
import requests
import argparse
import json

parser = argparse.ArgumentParser(description = 'Deploy particle firmware')
parser.add_argument('fname', type=str)
args = parser.parse_args()


#extract the product id and version numbers
binary = open(args.fname, 'rb')
data = binary.read()

#the product version
version_bytes = [data[-41], data[-42]]
product_id_bytes = [data[-43], data[-44]]
version_string = str(int(''.join('{:02X}'.format(x) for x in version_bytes),16))
product_string = str(int(''.join('{:02X}'.format(x) for x in product_id_bytes),16))

print("Product ID: {}".format(product_string))
print("Version: {}".format(version_string))

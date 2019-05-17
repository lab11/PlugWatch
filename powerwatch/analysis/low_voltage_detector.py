#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, dayofmonth, collect_list, lit, year, date_trunc, dayofweek
import pyspark.sql.functions as F
from pyspark.sql.window import Window
from pyspark.sql.types import FloatType, IntegerType, DateType, TimestampType
from pyspark import SparkConf
import datetime
import os
from math import isnan
import argparse
import json

#read arguments
parser = argparse.ArgumentParser()
parser.add_argument('result')
parser.add_argument('user')
parser.add_argument('password')
args = parser.parse_args()

#initiate spark context
spark = SparkSession.builder.appName("SAIDI/SAIFI cluster size").getOrCreate()

#connect to the database
#it's more efficient to do the bulk of filter in the database, especially in the time dimensions
query = "(SELECT core_id, time, grid_voltage FROM powerwatch WHERE time > '2019-03-01' AND (product_id = 7008 OR product_id = 7009 or product_id = 7010 or product_id = 7011 or product_id = 8462) AND grid_voltage IS NOT NULL AND grid_voltage > 10) alias"

pw_df = spark.read.jdbc("jdbc:postgresql://timescale.ghana.powerwatch.io/powerwatch", query,
        properties={"user": args.user, "password": args.password, "driver":"org.postgresql.Driver"})

#caching this in memory up front makes this faster if it all fits
pw_df.cache();

pw_df = pw_df.withColumn("rms_voltage",col("grid_voltage")/1.414)
pw_df = pw_df.groupBy("core_id").avg().orderBy(asc("avg(rms_voltage)"))
pw_df.show(300)

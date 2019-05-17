#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, dayofmonth, collect_list, lit, year
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
query = "(SELECT core_id, time, is_powered, product_id,millis, last_unplug_millis, last_plug_millis FROM powerwatch WHERE time > '2018-07-01' AND time < '2018-08-01' AND (product_id = 7008 OR product_id = 7009)) alias"

pw_df = spark.read.jdbc("jdbc:postgresql://timescale.ghana.powerwatch.io/powerwatch", query,
        properties={"user": args.user, "password": args.password, "driver":"org.postgresql.Driver"})

#caching this in memory up front makes this faster if it all fits
pw_df.cache();

###
# Do your analysis here
####

#output to where the execution script specifies the results should go
pw_df.repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result)

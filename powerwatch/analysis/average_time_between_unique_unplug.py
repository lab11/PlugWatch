#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour
from pyspark.sql.functions import month, year, lit, when, collect_list, struct, mean, stddev, stddev_pop
import pyspark.sql.functions as F
from pyspark.sql.window import Window
from pyspark.sql.types import FloatType, IntegerType, DateType
from pyspark import SparkConf
import yaml
import datetime
import os

conf = SparkConf()
conf.set("spark.jars", os.getenv("HOME") + "/.ivy2/jars/org.postgresql_postgresql-42.1.1.jar")
conf.set("spark.executor.extrajavaoptions", "-Xmx15000m")
conf.set("spark.executor.memory", "15g")
conf.set("spark.driver.memory", "15g")
conf.set("spark.storage.memoryFraction", "0")

spark = SparkSession.builder \
    .config(conf=conf) \
    .master("local[4]") \
    .appName("SAIDI Calculator") \
    .getOrCreate()

config = open('config.yaml')
config = yaml.load(config)

#connect to the database
pw_df = spark.read.jdbc("jdbc:postgresql://timescale.lab11.eecs.umich.edu/powerwatch", "dumsorwatch",
        properties={"user": config['user'], "password": config['password'],"driver":"org.postgresql.Driver"})

#read the data that we care about
pw_df = pw_df.select(pw_df['phone_imei'],pw_df['time'],pw_df['type'])
pw_df = pw_df.filter("type = 'unplugged' or type = 'usr_unplugged'")
pw_df = pw_df.filter(year("time") >= 2018)
pw_df = pw_df.filter(month("time") >= 7)
pw_df = pw_df.select("phone_imei","time")

window_size = 100
w = Window.orderBy(asc("time")).rowsBetween(0,window_size)
pw_df = pw_df.withColumn("outage_window_list",collect_list(F.struct("time","phone_imei")).over(w))

def filterOutage(time, imei, timeList):
    for i in timeList:
        if imei != i[1]:
            return (i[0] - time).total_seconds()

    return 117

udfFilterTransition = udf(filterOutage, FloatType())
pw_df = pw_df.withColumn("seconds_until_next_unplug", udfFilterTransition("time","phone_imei","outage_window_list"))
print(pw_df.stat.approxQuantile("seconds_until_next_unplug", [0.02, 0.05, 0.1, 0.2, 0.5], 0.0))

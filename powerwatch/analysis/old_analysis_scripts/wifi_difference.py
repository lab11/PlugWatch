#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, mean, when
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
    .appName("Wifi Drop on Outage Calculator") \
    .getOrCreate()

config = open('config.yaml')
config = yaml.load(config)

#connect to the database
pw_df = spark.read.jdbc("jdbc:postgresql://timescale.lab11.eecs.umich.edu/powerwatch", "pw_dedupe",
        properties={"user": config['user'], "password": config['password'],"driver":"org.postgresql.Driver"})

#read the data that we care about
pw_df = pw_df.select(pw_df['core_id'],pw_df['time'],pw_df['is_powered'],pw_df["num_wifi_networks"],pw_df['product_id'])
pw_df = pw_df.filter("product_id = 7008 OR product_id = 7009")
pw_df = pw_df.select(pw_df['core_id'],pw_df['is_powered'],pw_df['num_wifi_networks'])
pw_df = pw_df.groupBy("core_id","is_powered").mean()

#now look at the average difference between wifi and no wifi
w = Window.partitionBy("core_id").orderBy(asc("is_powered"))
is_powered_lag = lag("is_powered",1).over(w)
num_wifi_lag = lag("avg(num_wifi_networks)",1).over(w)

def wifiDiff(is_powered, is_powered_lag, num_wifi, num_wifi_lag):
    if(is_powered == True and is_powered_lag == False and num_wifi != None and num_wifi_lag != None):
        return num_wifi - num_wifi_lag
    else:
        return 0
udfwifiDiff = udf(wifiDiff, FloatType())
pw_df = pw_df.withColumn("wifi_difference", udfwifiDiff("is_powered",is_powered_lag, "avg(num_wifi_networks)", num_wifi_lag))
pw_df.groupBy("is_powered").mean().show()

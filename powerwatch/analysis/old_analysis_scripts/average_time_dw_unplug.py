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
            t =  (i[0] - time).total_seconds()
            if(t < 0):
                return 117
            else:
                return t

    return 117

udfFilterTransition = udf(filterOutage, FloatType())
pw_df = pw_df.withColumn("seconds_until_next_unplug", udfFilterTransition("time","phone_imei","outage_window_list"))
print(pw_df.stat.approxQuantile("seconds_until_next_unplug", [x*0.01 for x in range(0,100)], 0.0))

#connect to the database
pw_df = spark.read.jdbc("jdbc:postgresql://timescale.lab11.eecs.umich.edu/powerwatch", "pw_dedupe",
        properties={"user": config['user'], "password": config['password'],"driver":"org.postgresql.Driver"})

#read the data that we care about
pw_df = pw_df.select(pw_df['core_id'],pw_df['time'],pw_df['is_powered'],pw_df['product_id'],pw_df['millis'],pw_df['last_unplug_millis'],pw_df['last_plug_millis'])
pw_df = pw_df.filter("product_id = 7008 OR product_id= 7009")

#now we need to created a window function that looks at the leading lagging edge of is powered and detects transitions
#then we can filter out all data that is not a transition
def detectTransition(value1, value2):
    if(value1 == value2):
        return 0
    else:
        return 1
udfDetectTransition = udf(detectTransition, IntegerType())
w = Window.partitionBy("core_id").orderBy(asc("time"))
is_powered_lag = lag("is_powered",1).over(w)
pw_df = pw_df.withColumn("transition", udfDetectTransition("is_powered",is_powered_lag))

#filter out all transitions
pw_df = pw_df.filter("transition != 0")

#now count each outage (really restoration)
def countOutage(value1, value2, value3):
    if(value1 == False and value2 == True and value3 == True):
        return 1
    else:
        return 0
udfCountTransition = udf(countOutage, IntegerType())
is_powered_lead = lead("is_powered",1).over(w)
is_powered_lag = lag("is_powered",1).over(w)
pw_df = pw_df.withColumn("outage", udfCountTransition("is_powered", is_powered_lead, is_powered_lag))

#now find all the exact outage and restore times using millis
def timeCorrect(time, millis, unplugMillis):
    if(unplugMillis == 0 or millis == None or unplugMillis == None or isnan(millis) or isnan(unplugMillis)):
        return time
    elif unplugMillis > millis:
        return time
    else:
        return time - datetime.timedelta(microseconds = (int(millis)-int(unplugMillis))*1000)
udftimeCorrect = udf(timeCorrect, TimestampType())
pw_df = pw_df.withColumn("outage_time", udftimeCorrect("time","millis","last_unplug_millis"))

#now filter out everything that is not an outage. We should have a time and end_time for every outage
pw_df = pw_df.filter("outage != 0")

window_size = 150
w = Window.orderBy(asc("outage_time")).rowsBetween(0,window_size)
pw_df = pw_df.withColumn("outage_window_list",collect_list(F.struct("outage_time","core_id")).over(w))

def filterOutage(time, imei, timeList):
    for i in timeList:
        if imei != i[1]:
            t =  (i[0] - time).total_seconds()
            if(t < 0):
                return None
            else:
                return t

    return None

udfFilterTransition = udf(filterOutage, FloatType())
pw_df = pw_df.withColumn("seconds_until_next_unplug", udfFilterTransition("outage_time","core_id","outage_window_list"))
print(pw_df.stat.approxQuantile("seconds_until_next_unplug", [x*0.01 for x in range(0,100)], 0.0))


#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, dayofmonth
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
    .master("local") \
    .appName("SAIDI Calculator") \
    .getOrCreate()

config = open('config.yaml')
config = yaml.load(config)

#connect to the database
pw_df = spark.read.jdbc("jdbc:postgresql://timescale.lab11.eecs.umich.edu/powerwatch", "pw_dedupe",
        properties={"user": config['user'], "password": config['password'],"driver":"org.postgresql.Driver"})

#read the data that we care about
pw_df = pw_df.select(pw_df['core_id'],pw_df['time'],pw_df['is_powered'],pw_df['product_id'])
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

#now denote the end time of the outage for saidi reasons
time_lead = lead("time",1).over(w)
pw_df = pw_df.withColumn("end_time", time_lead)

#now filter out everything that is not an outage. We should have a time and end_time for every outage
pw_df = pw_df.filter("outage != 0")

#record the duration of the outage
def calculateDuration(startTime, endTime):
    delta = endTime-startTime
    seconds = delta.total_seconds()
    return int(seconds)

udfcalculateDuration = udf(calculateDuration, IntegerType())
pw_df = pw_df.withColumn("outage_duration", udfcalculateDuration("time","end_time"))

#now only keep the outages that had another outage
def filterOutage(timeNow, timeBefore, timeAfter):
    if(timeBefore is None and timeAfter is not None):
        if(timeAfter - timeNow < datetime.timedelta(minutes=5)):
            return 1
        else:
            return 0
    elif(timeAfter is None and timeBefore is not None):
        if(timeNow - timeBefore < datetime.timedelta(minutes=5)):
            return 1
        else:
            return 0
    elif(timeBefore is None and timeAfter is None):
        return 0
    elif(timeNow - timeBefore < datetime.timedelta(minutes=5) or timeAfter - timeNow < datetime.timedelta(minutes=5)):
        return 1
    else:
        return 0

udfFilterTransition = udf(filterOutage, IntegerType())

w = Window.orderBy(asc("time"))
time_lead = lead("time",1).over(w)
time_lag = lag("time",1).over(w)
pw_df = pw_df.withColumn("outage_number", udfFilterTransition("time", time_lag, time_lead))
pw_df = pw_df.filter("outage_number = 1")
pw_df = pw_df.select("time","core_id","outage_duration","outage_number")
pw_df = pw_df.groupBy("core_id", month("time")).sum().orderBy("core_id", month("time"))
pw_df.repartition(1).write.format("com.databricks.spark.csv").option("header", "true").save("monthly_outages_per_device")

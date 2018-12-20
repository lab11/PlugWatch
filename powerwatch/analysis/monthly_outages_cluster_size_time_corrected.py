#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, dayofmonth, collect_list, lit
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
    .appName("SAIDI/SAIFI cluster size") \
    .getOrCreate()

config = open('config.yaml')
config = yaml.load(config)

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
    if(unplugMillis == 0):
        return time
    elif unplugMillis > millis:
        return time
    else:
        return time - datetime.timedelta(microseconds = (millis-unplugMillis)*1000)
udftimeCorrect = udf(timeCorrect, DateType())
pw_df = pw_df.withColumn("outage_time", udftimeCorrect("time","millis","last_unplug_millis"))
pw_df = pw_df.withColumn("r_time", udftimeCorrect("time","millis","last_plug_millis"))

#now denote the end time of the outage for saidi reasons
time_lead = lead("r_time",1).over(w)
pw_df = pw_df.withColumn("restore_time", time_lead)

#now filter out everything that is not an outage. We should have a time and end_time for every outage
pw_df = pw_df.filter("outage != 0")


#record the duration of the outage
def calculateDuration(startTime, endTime):
    delta = endTime-startTime
    seconds = delta.total_seconds()
    return int(seconds)

udfcalculateDuration = udf(calculateDuration, IntegerType())
pw_df = pw_df.withColumn("outage_duration", udfcalculateDuration("outage_time","restore_time"))

window_size = 150
w = Window.orderBy(asc("outage_time")).rowsBetween(-1*window_size,window_size)
pw_df = pw_df.withColumn("outage_window_list",collect_list(F.struct("outage_time","core_id")).over(w))

def filterOutage(time, core_id, timeList):
    count = 1
    used = []
    used.append(core_id)
    for i in timeList:
        if abs((time - i[0]).total_seconds()) < 300 and i[1] not in used:
            used.append(i[1])
            count += 1

    if count > window_size:
        return window_size
    else:
        return count

udfFilterTransition = udf(filterOutage, IntegerType())
pw_df = pw_df.withColumn("outage_cluster_size", udfFilterTransition("outage_time","core_id","outage_window_list"))


#now only keep the outages that had another outage
#def filterOutage(timeNow, timeBefore, timeAfter):
#    if(timeBefore is None and timeAfter is not None):
#        if(timeAfter - timeNow < datetime.timedelta(minutes=5)):
#            return 1
#        else:
#            return 0
#    elif(timeAfter is None and timeBefore is not None):
#        if(timeNow - timeBefore < datetime.timedelta(minutes=5)):
#            return 1
#        else:
#            return 0
#    elif(timeBefore is None and timeAfter is None):
#        return 0
#    elif(timeNow - timeBefore < datetime.timedelta(minutes=5) or timeAfter - timeNow < datetime.timedelta(minutes=5)):
#        return 1
#    else:
#        return 0
#udfFilterTransition = udf(filterOutage, IntegerType())
#w = Window.orderBy(asc("time"))
#time_lead = lead("time",1).over(w)
#time_lag = lag("time",1).over(w)
#pw_df = pw_df.withColumn("outage_number", udfFilterTransition("time", time_lag, time_lead))
#pw_df = pw_df.filter("outage_number = 1")

pw_df = pw_df.select("time","outage_duration","outage_cluster_size")
pw_df = pw_df.withColumn("outage_events",lit(1))
pw_df = pw_df.groupBy(month("time"),"outage_cluster_size").sum().orderBy(month("time"),"outage_cluster_size")
pw_df = pw_df.select("month(time)","outage_cluster_size","sum(outage_duration)","sum(outage_events)")
pw_df = pw_df.withColumn("num_outages",pw_df["sum(outage_events)"]/pw_df["outage_cluster_size"])
pw_df.show(500)
pw_df.repartition(1).write.format("com.databricks.spark.csv").option("header", "true").save("monthly_outages_aggregate_cluster_size_time_corrected")

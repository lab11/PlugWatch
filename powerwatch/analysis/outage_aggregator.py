#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, dayofmonth, dayofyear, collect_list, lit, year, date_trunc, dayofweek, when, unix_timestamp
import pyspark.sql.functions as F
from pyspark.sql.window import Window
from pyspark.sql.types import FloatType, IntegerType, DateType, TimestampType
from pyspark import SparkConf
from datetime import datetime, timedelta
import os
from math import isnan
import argparse
import json
import calendar

#read arguments
parser = argparse.ArgumentParser()
parser.add_argument('result')
parser.add_argument('user')
parser.add_argument('password')
args = parser.parse_args()

#initiate spark context
spark = SparkSession.builder.appName("SAIDI/SAIFI cluster size").getOrCreate()

### It's really important that you partition on this data load!!! otherwise your executors will timeout and the whole thing will fail
start_time = '2018-07-01'
end_time = '2019-05-15'

#Roughly one partition per week of data is pretty fast and doesn't take too much chuffling
num_partitions = 30

# This builds a list of predicates to query the data in parrallel. Makes everything much faster
start_time_timestamp = calendar.timegm(datetime.strptime(start_time, "%Y-%m-%d").timetuple())
end_time_timestamp = calendar.timegm(datetime.strptime(end_time, "%Y-%m-%d").timetuple())
stride = (end_time_timestamp - start_time_timestamp)/num_partitions
predicates = []
for i in range(0,num_partitions):
    begin_timestamp = start_time_timestamp + i*stride
    end_timestamp = start_time_timestamp + (i+1)*stride
    pred_string = "time >= '" + datetime.utcfromtimestamp(int(begin_timestamp)).strftime("%Y-%m-%d %H:%M:%S")
    pred_string += "' AND "
    pred_string += "time < '" + datetime.utcfromtimestamp(int(end_timestamp)).strftime("%Y-%m-%d %H:%M:%S") + "'"
    predicates.append(pred_string)

#This query should only get data from deployed devices in the deployment table
query = "(SELECT core_id, time, is_powered, product_id,millis, last_unplug_millis, last_plug_millis FROM powerwatch WHERE time >= '" + start_time + "' AND time < '" + end_time + "' AND (product_id = 7008 OR product_id = 7009 or product_id = 7010 or product_id = 7011 or product_id = 8462)) alias"

pw_df = spark.read.jdbc(
            url = "jdbc:postgresql://timescale.ghana.powerwatch.io/powerwatch",
            table = query,
            predicates = predicates,
            properties={"user": args.user, "password": args.password, "driver":"org.postgresql.Driver"})

#if you have multiple saves below this prevents reloading the data every time
pw_df.cache()

#now we need to created a window function that looks at the leading lagging edge of is powered and detects transitions
#then we can filter out all data that is not a transition
w = Window.partitionBy("core_id").orderBy(asc("time"))
pw_df = pw_df.withColumn("previous_power_state", lag("is_powered").over(w))

#filter out every time that the state does not change
pw_df = pw_df.filter(col("previous_power_state") != col("is_powered"))

#now we should only count this if it is an outage (on, off, on)
is_powered_lead = lead("is_powered",1).over(w)
is_powered_lag = lag("is_powered",1).over(w)
pw_df = pw_df.withColumn("lagging_power",is_powered_lag)
pw_df = pw_df.withColumn("leading_power",is_powered_lead)
pw_df = pw_df.withColumn("outage", when((col("is_powered") == 0) & (col("lagging_power") == 1) & (col("leading_power") == 1), 1).otherwise(0))

#now need the most accurate outage time possible for outage event
#now find all the exact outage and restore times using millis
def timeCorrect(time, millis, unplugMillis):
    if(unplugMillis == 0 or millis == None or unplugMillis == None or isnan(millis) or isnan(unplugMillis)):
        return time
    elif unplugMillis > millis:
        return time
    else:
        return time - timedelta(microseconds = (int(millis)-int(unplugMillis))*1000)
udftimeCorrect = udf(timeCorrect, TimestampType())
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
w = Window.partitionBy(dayofyear("outage_time")).orderBy(asc("outage_time")).rowsBetween(-1*window_size,window_size)
pw_df = pw_df.withColumn("outage_window_list",collect_list(F.struct("outage_time","core_id")).over(w))

def filterOutage(time, core_id, timeList):
    count = 1
    used = []
    used.append(core_id)
    for i in timeList:
        if abs((time - i[0]).total_seconds()) < 120 and i[1] not in used:
            used.append(i[1])
            count += 1

    if count > window_size:
        return window_size
    else:
        return count

udfFilterTransition = udf(filterOutage, IntegerType())
pw_df = pw_df.withColumn("outage_cluster_size", udfFilterTransition("outage_time","core_id","outage_window_list"))
pw_df = pw_df.filter("outage_cluster_size > 1")
pw_df = pw_df.withColumn("outage_number",lit(1))

#okay now we have a list of all outages where at least one other device also had an outage within a time window
#pw_df.cache()

### SAIFI ###
#note that this the raw number of sensors that go out rather than a single metric per "outage"
#by month (no averages because we only saw one month)
outages_by_month = pw_df.select("outage_time","outage_number")
outages_by_month = outages_by_month.groupBy(month("outage_time")).sum().orderBy(month("outage_time"))
outages_by_month.show()

#by day of week (averaged across day)
outages_by_day = pw_df.select("outage_time","outage_number")
outages_by_day = outages_by_day.withColumn("outage_date", date_trunc("day", "outage_time"))
outages_by_day = outages_by_day.groupBy("outage_date").sum()
outages_by_day = outages_by_day.withColumn("outage_day_of_week", dayofweek("outage_date"))
outages_by_day = outages_by_day.groupBy("outage_day_of_week").avg().orderBy("outage_day_of_week")
outages_by_day.show()

#by hour of day (averaged by hour)
outages_by_hour = pw_df.select("outage_time","outage_number")
outages_by_hour = outages_by_hour.withColumn("outage_date_hour", date_trunc("hour", "outage_time"))
outages_by_hour = outages_by_hour.groupBy("outage_date_hour").sum()
outages_by_hour = outages_by_hour.withColumn("outage_hour", hour("outage_date_hour"))
outages_by_hour = outages_by_hour.groupBy("outage_hour").avg().orderBy("outage_hour")
outages_by_hour.show(30)


#pw_df = pw_df.select("time","core_id","outage_duration","outage_number")
#pw_df = pw_df.groupBy("core_id",month("time"),dayofmonth("time")).sum().orderBy("core_id",month("time"),dayofmonth("time"))
#output to where the execution script specifies the results should go
#pw_df.repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result)

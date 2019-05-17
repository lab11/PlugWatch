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
start_time = '2019-04-01'
end_time = '2019-05-01'

#Roughly one partition per week of data is pretty fast and doesn't take too much chuffling
num_partitions = 10

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
query = ("""
    (SELECT powerwatch.core_id, time, is_powered, product_id, millis, last_unplug_millis,
            last_plug_millis, d.location_latitude, d.location_longitude FROM
    powerwatch
    INNER JOIN (
      SELECT core_id,
        location_latitude,
        location_longitude,
        COALESCE(deployment_start_time, '1970-01-01 00:00:00+0') as st,
        COALESCE(deployment_end_time, '9999-01-01 00:00:00+0') as et
      FROM deployment) d ON powerwatch.core_id = d.core_id
    WHERE time >= st AND time <= et AND """ +
        "time >= '" + start_time + "' AND " +
        "time < '" + end_time + "' AND " +
        "(product_id = 7008 OR product_id = 7009 or product_id = 7010 or product_id = 7011 or product_id = 8462)) alias")

pw_df = spark.read.jdbc(
            url = "jdbc:postgresql://timescale.ghana.powerwatch.io/powerwatch",
            table = query,
            predicates = predicates,
            properties={"user": args.user, "password": args.password, "driver":"org.postgresql.Driver"})

#if you have multiple saves below this prevents reloading the data every time
pw_df.cache()

#We should mark every row with the number of unique sensors reporting in +-5 days so we now the denominator for SAIDI/SAIFI
pw_distinct_core_id = pw_df.select("time","core_id")
pw_distinct_core_id = pw_distinct_core_id.groupBy(F.window("time", '10 days', '1 day')).agg(F.countDistinct("core_id"))
pw_distinct_core_id = pw_distinct_core_id.withColumn("window_mid_point", F.from_unixtime((F.unix_timestamp(col("window.start")) + F.unix_timestamp(col("window.end")))/2))
pw_distinct_core_id = pw_distinct_core_id.select(col("count(DISTINCT core_id)").alias("sensors_reporting"), "window_mid_point")

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
pw_df = pw_df.withColumn("outage_time", F.unix_timestamp("outage_time"))
pw_df = pw_df.withColumn("r_time", udftimeCorrect("time","millis","last_plug_millis"))
pw_df = pw_df.withColumn("r_time", F.unix_timestamp("r_time"))

#now denote the end time of the outage for saidi reasons
time_lead = lead("r_time",1).over(w)
pw_df = pw_df.withColumn("restore_time", time_lead)

#now filter out everything that is not an outage. We should have a time and end_time for every outage
pw_df = pw_df.filter("outage != 0")

pw_df = pw_df.select("core_id", "outage_time", "restore_time", "location_latitude", "location_longitude")

# Okay now that we have the outages and times we should join it with the number of sensors reporting above
# This allows us to calculate the relative portion of each device to SAIDI/SAIFI
#pw_df = pw_df.join(pw_distinct_core_id, F.date_trunc("day", pw_df['outage_time']) == F.date_trunc("day", pw_distinct_core_id["window_mid_point"]))

#record the duration of the outage
#def calculateDuration(startTime, endTime):
#    delta = endTime-startTime
#    seconds = delta.total_seconds()
#    return int(seconds)

#udfcalculateDuration = udf(calculateDuration, IntegerType())
#pw_df = pw_df.withColumn("outage_duration", udfcalculateDuration("outage_time","restore_time"))

#Okay so the best way to actually do outage clustering is through an iterative hierarchical approach

#Steps:
#Iterate:
# Sort by outage time
#   Note the distance to the nearest point in time leading or lagging you
#   Note the distance to of that nearest point to its neighbor
#   If you are closer to your neighbor than it is to it's closest merge and create a new point with a new outage time

def timestamp_average(timestamps):
    seconds = 0
    for i in range(0,len(timestamps)):
        seconds += timestamps[i]

    return (seconds/len(timestamps))

max_cluster_size = 500
for i in range(0,500):
    w = Window.partitionBy(dayofyear(F.from_unixtime("outage_time"))).orderBy(asc("outage_time"))
    lead1 = lead("outage_time",1).over(w)
    lead2 = lead("outage_time",2).over(w)
    lag1 = lag("outage_time",1).over(w)
    lag2 = lag("outage_time",2).over(w)
    pw_df = pw_df.withColumn("lead1",lead1)
    pw_df = pw_df.withColumn("lead2",lead2)
    pw_df = pw_df.withColumn("lag1",lag1)
    pw_df = pw_df.withColumn("lag2",lag2)
    pw_df = pw_df.withColumn("diff_lead1", col("lead1") - col("outage_time"))
    pw_df = pw_df.withColumn("diff_lead2", col("lead2") - col("lead1"))
    pw_df = pw_df.withColumn("diff_lag1", col("outage_time") - col("lag1"))
    pw_df = pw_df.withColumn("diff_lag2", col("lag1") - col("lag2"))

    merge_time = when((col("diff_lead1") < 60) & 
                      (col("diff_lead1") <= col("diff_lead2")) & 
                      (col("diff_lead1") <= col("diff_lag1")), col("lead1")).when(
                              (col("diff_lag1") < 60) & 
                              (col("diff_lag1") < col("diff_lag2")) & 
                              (col("diff_lag1") <= col("diff_lead1")), col("outage_time")).otherwise(None)

    pw_df = pw_df.withColumn("merge_time", merge_time)
    pw_df = pw_df.groupBy("merge_time").agg(F.collect_list("core_id").alias("core_id"),
                                            F.collect_list("outage_time").alias("outage_times"),
                                            F.collect_list("restore_time").alias("restore_time"),
                                            F.collect_list("location_latitude").alias("location_latitude"),
                                            F.collect_list("location_longitude").alias("location_longitude"))

    udfTimestampAverage = udf(timestamp_average, IntegerType())
    pw_df = pw_df.withColumn("outage_time", udfTimestampAverage("outage_times"))

    pw_df.show(1000)


#change this to a range query
#w = Window.partitionBy(dayofyear("outage_time")).orderBy(asc("outage_time")).rowsBetween(-1*window_size,window_size)
#pw_df = pw_df.withColumn("outage_window_list",collect_list(F.struct("outage_time","core_id")).over(w))
#
#def filterOutage(time, core_id, timeList):
#    count = 1
#    used = []
#    used.append(core_id)
#    for i in timeList:
#        if abs((time - i[0]).total_seconds()) < 5 and i[1] not in used:
#            used.append(i[1])
#            count += 1
#
#    if count > window_size:
#        return window_size
#    else:
#        return count
#
#udfFilterTransition = udf(filterOutage, IntegerType())
#pw_df = pw_df.withColumn("outage_cluster_size", udfFilterTransition("outage_time","core_id","outage_window_list"))
#pw_df = pw_df.filter("outage_cluster_size > 1")
#pw_df = pw_df.withColumn("outage_number",lit(1))

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

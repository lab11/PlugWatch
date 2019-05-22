#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, dayofmonth, dayofyear, collect_list, lit, year, date_trunc, dayofweek, when, unix_timestamp, array
import pyspark.sql.functions as F
from pyspark.sql.window import Window
from pyspark.sql.types import FloatType, IntegerType, DateType, TimestampType, LongType
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
end_time = '2018-12-01'
cluster_distance_seconds = 180
CD = cluster_distance_seconds

#Roughly one partition per week of data is pretty fast and doesn't take too much chuffling
num_partitions = int((datetime.strptime(end_time,"%Y-%m-%d").timestamp() - datetime.strptime(start_time,"%Y-%m-%d").timestamp())/(7*24*3600))

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

    return int(seconds/len(timestamps))

max_cluster_size = 500
pw_df = pw_df.select(array("core_id").alias("core_id"),
                    "outage_time",
                    array("restore_time").alias("restore_time"),
                    array(F.struct("location_latitude", "location_longitude")).alias("location"))

pw_df = pw_df.withColumn("outage_times", F.array("outage_time"))

#print("Starting with count:", pw_df.count())
pw_finalized_outages = spark.createDataFrame([], pw_df.schema)

# all of the local checkpoints should probably be switched to just checkpoints
# note the checkpointing is CRITICAL to the function of the algorithm in spark
# otherwise the RDD lineage is recalculated every loop and the plan creation time balloons exponentially
# checkpointing truncates the plan
# it is also critical that you reset the reference of the checkpoint
# spark objects are immutable - there is no such thing as an in place modification
# and checkpointing does modify the lineage of the underlying object
# We *might* be able to get away with caching instead but I was having out of memory problems
pw_finalized_outages = pw_finalized_outages.localCheckpoint(eager = True)
pw_df = pw_df.localCheckpoint(eager = True)

#now run the iterative algorithm to cluster the remainder
while pw_df.count() > 0:
    #first prune any outages that are not getting any larger and union them to finalized outages set
    w = Window.partitionBy(F.weekofyear(F.from_unixtime("outage_time"))).orderBy(asc("outage_time"))
    lead1 = lead("outage_time",1).over(w)
    lag1 = lag("outage_time",1).over(w)
    pw_df = pw_df.withColumn("lead1",lead1)
    pw_df = pw_df.withColumn("lag1",lag1)
    merge_time = when(((col("lead1") - col("outage_time") >= CD) | col("lead1").isNull()) & ((col("outage_time") - col("lag1") >= CD) | col("lag1").isNull()), None).otherwise(lit(0))
    pw_df = pw_df.withColumn("merge_time", merge_time)

    pw_final_outages = pw_df.filter(col("merge_time").isNull())
    pw_final_outages = pw_final_outages.select("core_id","outage_time",
                                                "restore_time",
                                                "location", "outage_times")

    pw_finalized_outages = pw_finalized_outages.union(pw_final_outages)
    pw_finalized_outages = pw_finalized_outages.localCheckpoint()
    pw_df = pw_df.filter(col("merge_time").isNotNull())
    pw_df = pw_df.localCheckpoint(eager = True)
    print("Pruned to:", pw_df.count())

    #now do one step of merging for the ones that are still changing
    w = Window.partitionBy(F.weekofyear(F.from_unixtime("outage_time"))).orderBy(asc("outage_time"))
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

    merge_time = when((col("diff_lead1") < CD) &
                      ((col("diff_lead1") <= col("diff_lead2")) | col("diff_lead2").isNull()) &
                      ((col("diff_lead1") <= col("diff_lag1")) | col("diff_lag1").isNull()), col("lead1")).when(
                              (col("diff_lag1") < CD) &
                              ((col("diff_lag1") <= col("diff_lag2")) | col("diff_lag2").isNull()) &
                              ((col("diff_lag1") <= col("diff_lead1")) | col("diff_lead1").isNull()), col("outage_time")).otherwise(None)

    pw_df = pw_df.withColumn("merge_time", merge_time)
    pw_null_merge_time = pw_df.filter(col("merge_time").isNull())
    pw_df = pw_df.filter(col("merge_time").isNotNull())

    pw_df = pw_df.groupBy("merge_time").agg(F.flatten(F.collect_list("core_id")).alias("core_id"),
                                            F.flatten(F.collect_list("outage_times")).alias("outage_times"),
                                            F.flatten(F.collect_list("restore_time")).alias("restore_time"),
                                            F.flatten(F.collect_list("location")).alias("location"))

    pw_df = pw_df.select("core_id","outage_times","restore_time","location")
    pw_null_merge_time = pw_null_merge_time.select("core_id","outage_times","restore_time","location")
    pw_df = pw_df.union(pw_null_merge_time)

    udfTimestampAverage = udf(timestamp_average, LongType())
    pw_df = pw_df.withColumn("outage_time", udfTimestampAverage("outage_times"))
    pw_df = pw_df.localCheckpoint(eager = True)
    print("Merged to:", pw_df.count())
    print()

#Okay now we have a list of outages, restore_times, locations, core_ids
#First let's calculate some high level metrics

#size of outages
pw_finalized_outages = pw_finalized_outages.withColumn("cluster_size", F.size(F.array_distinct("core_id")))

#standard deviation outage times
pw_finalized_outages = pw_finalized_outages.withColumn("outage_times_stddev", F.explode("outage_times"))

#this expression essentially takes the first value of each column (which should all be the same after the explode)
exprs = [F.first(x).alias(x) for x in pw_finalized_outages.columns if x != 'outage_times_stddev' and x != 'outage_time']
pw_finalized_outages = pw_finalized_outages.groupBy("outage_time").agg(F.stddev_pop("outage_times_stddev").alias("outage_times_stddev"),*exprs)

#range of outage times
pw_finalized_outages = pw_finalized_outages.withColumn("outage_times_range", F.array_max("outage_times") - F.array_min("outage_times"))

#standard deviation and range of restore times
pw_finalized_outages = pw_finalized_outages.withColumn("restore_times", col("restore_time"))
pw_finalized_outages = pw_finalized_outages.withColumn("restore_time", F.explode("restore_time"))

#this expression essentially takes the first value of each column (which should all be the same after the explode)
exprs = [F.first(x).alias(x) for x in pw_finalized_outages.columns if x != 'restore_time' and x != 'outage_time']
pw_finalized_outages = pw_finalized_outages.groupBy("outage_time").agg(F.avg("restore_time").alias("restore_times_mean"),*exprs)

pw_finalized_outages = pw_finalized_outages.withColumn("restore_times_stddev", F.explode("restore_times"))

#this expression essentially takes the first value of each column (which should all be the same after the explode)
exprs = [F.first(x).alias(x) for x in pw_finalized_outages.columns if x != 'restore_times_stddev' and x != 'outage_time']
pw_finalized_outages = pw_finalized_outages.groupBy("outage_time").agg(F.stddev_pop("restore_times_stddev").alias("restore_times_stddev"),*exprs)
pw_finalized_outages = pw_finalized_outages.withColumn("restore_times_range", F.array_max("restore_times") - F.array_min("restore_times"))

#Okay now to effectively calculate SAIDI/SAIFI we need to know the sensor population
#join the number of sensors reporting metric above with our outage groupings
#then we can calculate the relative SAIDI/SAIFI contribution of each outage
pw_finalized_outages = pw_finalized_outages.join(pw_distinct_core_id, F.date_trunc("day", F.from_unixtime(pw_finalized_outages["outage_time"])) == F.date_trunc("day", pw_distinct_core_id["window_mid_point"]))

pw_finalized_outages = pw_finalized_outages.select("outage_time","restore_times_mean","cluster_size","sensors_reporting","outage_times","outage_times_range","outage_times_stddev","restore_times","restore_times_range","restore_times_stddev", "location")
pw_finalized_outages = pw_finalized_outages.withColumn("relative_cluster_size",col("cluster_size")/col("sensors_reporting"))

pw_finalized_with_string = pw_finalized_outages.withColumn("outage_times",F.to_json("outage_times"))
pw_finalized_with_string = pw_finalized_with_string.withColumn("restore_times",F.to_json("restore_times"))
pw_finalized_with_string = pw_finalized_with_string.withColumn("location",F.to_json("location"))

#okay we should save this
pw_finalized_with_string.repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result + '/full_outage_list')




#We need to zero fill for every date and cluster size not already present in the dataset
#to do this create a dataframe for range date_min to date_max and cluster_size cluster_size_min to cluster_size max with 0 rel saifi
#then join it with the actual DF preferentially choosing the non zero value
min_time = pw_finalized_outages.agg(F.min("outage_time")).collect()[0].__getitem__("min(outage_time)")
max_time = pw_finalized_outages.agg(F.max("outage_time")).collect()[0].__getitem__("max(outage_time)") 
min_size = pw_finalized_outages.agg(F.min("cluster_size")).collect()[0].__getitem__("min(cluster_size)")
#the range is exclusive so without the plus one the largest cluster is left out
max_size = pw_finalized_outages.agg(F.max("cluster_size")).collect()[0].__getitem__("max(cluster_size)") + 1

#This time span needs to be the minimum time we are clustering by. In this case 1 hour
outage_dates = spark.range(min_time, max_time, 3600).select(col("id").alias("outage_time"))
cluster_sizes = spark.range(min_size, max_size, 1).select(col("id").alias("cluster_size_zero"))
outage_zeros = outage_dates.crossJoin(cluster_sizes)
outage_zeros = outage_zeros.withColumn("relative_cluster_size_zero", lit(0))
outage_zeros = outage_zeros.select(F.from_unixtime("outage_time").alias("outage_time"),"cluster_size_zero","relative_cluster_size_zero")


### SAIFI/OUTAGE SIZE HISTOGRAM ###
#month - outage cluster size - monthly SAIFI for that cluster size
outages_by_month = pw_finalized_outages.select(F.from_unixtime("outage_time").alias("outage_time"),
                                                                "relative_cluster_size","cluster_size")
outages_by_month = outages_by_month.withColumn("outage_date_month", date_trunc("month", "outage_time"))
outages_by_month = outages_by_month.groupBy("outage_date_month","cluster_size").sum()
outages_by_month = outages_by_month.withColumn("outage_month", month("outage_date_month"))
outages_by_month = outages_by_month.groupBy("outage_month","cluster_size").avg().orderBy("outage_month","cluster_size")
outages_by_month = outages_by_month.select("outage_month","cluster_size",col("avg(sum(relative_cluster_size))").alias("monthly_SAIFI"))
outages_by_month.show(500)
outages_by_month.repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result + '/monthly_SAIFI_size_histogram')

#day of week - outage cluster size - daily SAIFI for that cluster size
outages_by_day = pw_finalized_outages.select(F.from_unixtime("outage_time").alias("outage_time"),
                                                        "relative_cluster_size", "cluster_size")

#trunc and group
outages_by_day = outages_by_day.withColumn("outage_date", date_trunc("day", "outage_time"))
outages_by_day = outages_by_day.groupBy("outage_date","cluster_size").sum()

#trunc and group
outage_zeros_by_day = outage_zeros.withColumn("outage_date_zero",date_trunc("day","outage_time"))
outage_zeros_by_day = outage_zeros_by_day.groupBy("outage_date_zero","cluster_size_zero").sum()

#join to fill gaps
outages_by_day = outages_by_day.join(outage_zeros_by_day, (outage_zeros_by_day["outage_date_zero"] == outages_by_day["outage_date"]) & (outage_zeros_by_day["cluster_size_zero"] == outages_by_day["cluster_size"]), 'full_outer')
outages_by_day = outages_by_day.select(F.coalesce("outage_date", "outage_date_zero").alias("outage_date"), 
                                       F.coalesce("cluster_size","cluster_size_zero").alias("cluster_size"), 
                                       F.coalesce("sum(relative_cluster_size)", "sum(relative_cluster_size_zero)").alias("relative_cluster_size"))

#average
outages_by_day = outages_by_day.withColumn("outage_day_of_week", dayofweek("outage_date"))
outages_by_day = outages_by_day.groupBy("outage_day_of_week","cluster_size").avg().alias("daily_SAIFI").orderBy("outage_day_of_week","cluster_size")
outages_by_day = outages_by_day.select("outage_day_of_week","cluster_size",col("avg(relative_cluster_size)").alias("daily_SAIFI"))
outages_by_day = outages_by_day.filter(col("daily_SAIFI") > 0)
outages_by_day.show(500)
outages_by_day.repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result + '/daily_SAIFI_size_histogram')

#hour of day - outage cluster size - daily SAIFI for that cluster size
outages_by_hour = pw_finalized_outages.select(F.from_unixtime("outage_time").alias("outage_time"),
                                                        "relative_cluster_size", "cluster_size")

#trunc and group
outages_by_hour = outages_by_hour.withColumn("outage_date_hour", date_trunc("hour", "outage_time"))
outages_by_hour = outages_by_hour.groupBy("outage_date_hour", "cluster_size").sum()

#trunc and group
outage_zeros_by_hour = outage_zeros.withColumn("outage_date_hour_zero",date_trunc("hour","outage_time"))
outage_zeros_by_hour = outage_zeros_by_hour.groupBy("outage_date_hour_zero", "cluster_size_zero").sum()

#join to fill gaps
outages_by_hour = outages_by_hour.join(outage_zeros_by_hour, (outage_zeros_by_hour["outage_date_hour_zero"] == outages_by_hour["outage_date_hour"]) & (outage_zeros_by_hour["cluster_size_zero"] == outages_by_hour["cluster_size"]), 'full_outer')
outages_by_hour = outages_by_hour.select(F.coalesce("outage_date_hour", "outage_date_hour_zero").alias("outage_date_hour"), 
                                       F.coalesce("cluster_size","cluster_size_zero").alias("cluster_size"), 
                                       F.coalesce("sum(relative_cluster_size)", "sum(relative_cluster_size_zero)").alias("relative_cluster_size"))

outages_by_hour = outages_by_hour.withColumn("outage_hour", hour("outage_date_hour"))
outages_by_hour = outages_by_hour.groupBy("outage_hour","cluster_size").avg().alias("hourly_SAIFI").orderBy("outage_hour","cluster_size")
outages_by_hour = outages_by_hour.select("outage_hour","cluster_size",col("avg(relative_cluster_size)").alias("hourly_SAIFI"))
outages_by_hour = outages_by_hour.filter(col("hourly_SAIFI") > 0)
outages_by_hour.show(500)
outages_by_hour.repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result + '/hourly_SAIFI_size_histogram')

#now filter the outages so that at least two devices went out
pw_finalized_outages = pw_finalized_outages.filter(col("cluster_size") >= 2)

### SAIFI ###
#month - monthly SAIFI
outages_by_month = pw_finalized_outages.select(F.from_unixtime("outage_time").alias("outage_time"),"relative_cluster_size")
outages_by_month = outages_by_month.withColumn("outage_date_month", date_trunc("month", "outage_time"))
outages_by_month = outages_by_month.groupBy("outage_date_month").sum()
outages_by_month = outages_by_month.withColumn("outage_month", month("outage_date_month"))
outages_by_month = outages_by_month.groupBy("outage_month").avg().alias("monthly_SAIFI").orderBy("outage_month")
outages_by_month = outages_by_month.select("outage_month",col("avg(sum(relative_cluster_size))").alias("monthly_SAIFI"))
outages_by_month.show()
outages_by_month.repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result + '/monthly_SAIFI_cluster_size_gte2')

#by day of week (averaged across day)
# day of week - daily SAIFI
outages_by_day = pw_finalized_outages.select(F.from_unixtime("outage_time").alias("outage_time"),"relative_cluster_size")
outages_by_day = outages_by_day.withColumn("outage_date", date_trunc("day", "outage_time"))
outages_by_day = outages_by_day.groupBy("outage_date").sum()
outages_by_day = outages_by_day.withColumn("outage_day_of_week", dayofweek("outage_date"))
outages_by_day = outages_by_day.groupBy("outage_day_of_week").avg().alias("daily_SAIFI").orderBy("outage_day_of_week")
outages_by_day = outages_by_day.select("outage_day_of_week",col("avg(sum(relative_cluster_size))").alias("daily_SAIFI"))
outages_by_day.show()
outages_by_day.repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result + '/daily_SAIFI_cluster_size_gte2')

# by hour of day (averaged by hour)
# hour of day - hourly SAIFI
outages_by_hour = pw_finalized_outages.select(F.from_unixtime("outage_time").alias("outage_time"),"relative_cluster_size")
outages_by_hour = outages_by_hour.withColumn("outage_date_hour", date_trunc("hour", "outage_time"))
outages_by_hour = outages_by_hour.groupBy("outage_date_hour").sum()
outages_by_hour = outages_by_hour.withColumn("outage_hour", hour("outage_date_hour"))
outages_by_hour = outages_by_hour.groupBy("outage_hour").avg().alias("hourly_SAIFI").orderBy("outage_hour")
outages_by_hour = outages_by_hour.select("outage_hour",col("avg(sum(relative_cluster_size))").alias("hourly_SAIFI"))
outages_by_hour.show(30)
outages_by_hour.repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result + '/hourly_SAIFI_cluster_size_gte2')

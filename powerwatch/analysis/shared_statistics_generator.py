#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, dayofmonth, dayofyear, collect_list, lit, year, date_trunc, dayofweek, when, unix_timestamp, array
import pyspark.sql.functions as F
from pyspark.sql.window import Window
from pyspark.sql.types import FloatType, IntegerType, DateType, TimestampType, LongType, ArrayType
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
start_time = '2019-06-21'
end_time = '2019-06-28'
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
    (SELECT powerwatch.core_id, time, is_powered, site_id, grid_voltage, wit_voltage_volts
    FROM powerwatch
    INNER JOIN (
        select core_id,
        site_id,
        COALESCE(deployment_start_time, '1970-01-01 00:00:00+0') as st,
        COALESCE(deployment_end_time, '9999-01-01 00:00:00+0') as et
        from achimota_device_share) achimota_device_share
    ON powerwatch.core_id = achimota_device_share.core_id""" +
    " WHERE time >= st AND " +
    " time <= et AND " +
    " time >= '" + start_time + "' AND " + 
    " time < '" + end_time + "') alias")

pw_df = spark.read.jdbc(
            url = "jdbc:postgresql://timescale.ghana.powerwatch.io/powerwatch",
            table = query,
            predicates = predicates,
            properties={"user": args.user, "password": args.password, "driver":"org.postgresql.Driver"})

grid = spark.read.jdbc(
            url = "jdbc:postgresql://timescale.ghana.powerwatch.io/powerwatch",
            table = 'achimota_grid_grouped',
            properties={"user": args.user, "password": args.password, "driver":"org.postgresql.Driver"})

site_share = spark.read.jdbc(
            url = "jdbc:postgresql://timescale.ghana.powerwatch.io/powerwatch",
            table = 'achimota_site_share',
            properties={"user": args.user, "password": args.password, "driver":"org.postgresql.Driver"})

#only share the grid the we can share
grid = grid.join(site_share, on='site_id',how='inner');

#now let's resample by core_id. So for each minute let's get 1) was the sensor powered and 2) what was the voltage
#and we can do this with a sliding window. Just leave gaps if there are gaps
pw_df = pw_df.select("core_id","time","is_powered", (col("grid_voltage")/1.414).alias("grid_voltage"),"wit_voltage_volts","site_id")
pw_df_resampled = pw_df.groupBy(col("core_id"),F.window("time", windowDuration='5 minutes',slideDuration='1 minute',startTime='30 seconds')).agg(F.collect_list("grid_voltage").alias("grid_voltage_list"),F.collect_list("is_powered").alias("is_powered_list"),F.first("site_id").alias("site_id"),F.collect_list("wit_voltage_volts").alias("wit_voltage_list"))
pw_df_resampled = pw_df_resampled.withColumn("time", F.from_unixtime((F.unix_timestamp(col("window.start")) + F.unix_timestamp(col("window.end")))/2))
pw_df_resampled = pw_df_resampled.withColumn("grid_voltage_list",F.concat("grid_voltage_list","wit_voltage_list"))

#now collect the resampled list per sensor
def average_voltage(voltage_list):
    if voltage_list == None or len(voltage_list) == 0:
        return None
    else:
        return sum(voltage_list)/len(voltage_list)

udfAverageVoltage = udf(average_voltage, FloatType())
pw_df_resampled = pw_df_resampled.withColumn("grid_voltage", udfAverageVoltage("grid_voltage_list"))

#now collect the resampled list per sensor
def average_is_powered(powered_list):
    if powered_list == None or len(powered_list) == 0:
        return None
    else:
        return round(sum(powered_list)/len(powered_list))

udfPowered = udf(average_is_powered, IntegerType())
pw_df_resampled = pw_df_resampled.withColumn("is_powered", udfPowered("is_powered_list"))

#drop the samples at which both the voltage and powered are null
pw_df_resampled = pw_df_resampled.select("core_id","time","site_id","is_powered","grid_voltage")


#remove all voltages for when the sensor is not receiving power
pw_df_resampled = pw_df_resampled.withColumn("grid_voltage",when(col("is_powered") == 0, None).otherwise(col("grid_voltage")))

#join on the grid
pw_df_resampled = pw_df_resampled.join(grid, on='site_id', how='inner')


#now group by tx_id and time and collect the is_powered and voltage states
pw_df_resampled = pw_df_resampled.groupBy("tx","time").agg(F.collect_list("is_powered").alias("is_powered"),F.collect_list("grid_voltage").alias("grid_voltage"))

def removeNulls(list_with_nulls):
    list_to_return = []
    for element in list_with_nulls:
        if element != None:
            list_to_return.append(element)

    return list_to_return

#drop the nulls from the grid voltage list
udfRemoveNulls = udf(removeNulls, ArrayType(FloatType()))
pw_df_resampled = pw_df_resampled.withColumn("grid_voltage",udfRemoveNulls("grid_voltage"))

#average the voltage for all sensors under the transformer
pw_df_resampled = pw_df_resampled.withColumn("grid_voltage",udfAverageVoltage("grid_voltage"))
pw_df_resampled = pw_df_resampled.withColumn("average_power",udfAverageVoltage("is_powered"))

#average the power state for all sensors under the transformer

valid_voltage_condition = F.sum(F.when((col("grid_voltage") > 0) & col("grid_voltage").isNotNull(), 1).otherwise(0))
under_voltage_condition = F.sum(F.when((col("grid_voltage") > 0) & col("grid_voltage").isNotNull() & (col("grid_voltage") < 207), 1).otherwise(0))
over_voltage_condition = F.sum(F.when((col("grid_voltage") > 0) & col("grid_voltage").isNotNull() & (col("grid_voltage") > 253), 1).otherwise(0))
total_measurements_in_period = F.count("time")
measurements_with_one_sensor = F.sum(F.when((F.size("is_powered") == 1),  1).otherwise(0))
measurements_with_two_sensors = F.sum(F.when((F.size("is_powered") == 2),  1).otherwise(0))
measurements_with_three_sensors = F.sum(F.when((F.size("is_powered") == 3),  1).otherwise(0))
measurements_with_zero_sensors = F.sum(F.when((F.size("is_powered") == 0) | (col("is_powered").isNull()) ,  1).otherwise(0))
power_out = F.sum(F.when(col("average_power") == 0, 1).otherwise(0))
power_on = F.sum(F.when(col("average_power") == 1, 1).otherwise(0))
power_ambiguous = F.sum(F.when((col("average_power") < 1) & (col("average_power") > 0), 1).otherwise(0))

total_summary = pw_df_resampled.groupBy("tx").agg(F.avg("grid_voltage").alias("average_measured_voltage"),
                                                    total_measurements_in_period.alias("total_measurements"),
                                                    valid_voltage_condition.alias("valid_voltage"),
                                                    under_voltage_condition.alias("under_voltage"),
                                                    over_voltage_condition.alias("over_voltage"),
                                                    measurements_with_one_sensor.alias("one_sensor"),
                                                    measurements_with_two_sensors.alias("two_sensors"),
                                                    measurements_with_three_sensors.alias("three_sensors"),
                                                    measurements_with_zero_sensors.alias("zero_sensors"),
                                                    power_out.alias("power_out"),
                                                    power_on.alias("power_on"),
                                                    power_ambiguous.alias("power_ambiguous"))

total_summary = total_summary.select("tx",
                                    F.round(((col("total_measurements")/10085)*100),1).alias("Percent Time Monitored"),
                                    F.round(((col("valid_voltage")/10085)*100),1).alias("Percent Time Voltage Monitored"),
                                    F.round(((col("under_voltage")/10085)*100),1).alias("Percent Total Time Under Voltage"),
                                    F.round(((col("over_voltage")/10085)*100),1).alias("Percent Total Time Over Voltage"),
                                    F.round(((col("under_voltage")/col("valid_voltage"))*100),1).alias("Percent Monitored Time Under Voltage"),
                                    F.round(((col("over_voltage")/col("valid_voltage"))*100),1).alias("Percent Monitored Time Over Voltage"),
                                    F.round(((col("power_out")/10085)*100),1).alias("Percent Total Time Power Out"),
                                    F.round(((col("power_out")/col("total_measurements"))*100),1).alias("Percent Monitored Time Power Out"),
                                    F.round(((col("power_ambiguous")/10085)*100),1).alias("Percent Total Time Power Ambiguous"),
                                    F.round(((col("power_ambiguous")/col("total_measurements"))*100),1).alias("Percent Monitored Time Power Ambiguous"))


total_summary.show(20)
total_summary.repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result + '/weekly_summary')

daily_summary = pw_df_resampled.groupBy("tx",F.date_trunc('day',"time").alias("date_day")).agg(F.avg("grid_voltage").alias("average_measured_voltage"),
                                                    total_measurements_in_period.alias("total_measurements"),
                                                    valid_voltage_condition.alias("valid_voltage"),
                                                    under_voltage_condition.alias("under_voltage"),
                                                    over_voltage_condition.alias("over_voltage"),
                                                    measurements_with_one_sensor.alias("one_sensor"),
                                                    measurements_with_two_sensors.alias("two_sensors"),
                                                    measurements_with_three_sensors.alias("three_sensors"),
                                                    measurements_with_zero_sensors.alias("zero_sensors"),
                                                    power_out.alias("power_out"),
                                                    power_on.alias("power_on"),
                                                    power_ambiguous.alias("power_ambiguous"))

daily_summary = daily_summary.select("tx","date_day",
                                    F.round(((col("total_measurements")/1440)*100),1).alias("Percent Time Monitored"),
                                    F.round(((col("valid_voltage")/1440)*100),1).alias("Percent Time Voltage Monitored"),
                                    F.round(((col("under_voltage")/1440)*100),1).alias("Percent Total Time Under Voltage"),
                                    F.round(((col("over_voltage")/1440)*100),1).alias("Percent Total Time Over Voltage"),
                                    F.round(((col("under_voltage")/col("total_measurements"))*100),1).alias("Percent Monitored Time Under Voltage"),
                                    F.round(((col("over_voltage")/col("total_measurements"))*100),1).alias("Percent Monitored Time Over Voltage"),
                                    F.round(((col("power_out")/1440)*100),1).alias("Percent Total Time Power Out"),
                                    F.round(((col("power_out")/col("total_measurements"))*100),1).alias("Percent Monitored Time Power Out"),
                                    F.round(((col("power_ambiguous")/1440)*100),1).alias("Percent Total Time Power Ambiguous"),
                                    F.round(((col("power_ambiguous")/col("total_measurements"))*100),1).alias("Percent Monitored Time Power Ambiguous"))


daily_summary = daily_summary.orderBy("tx","date_day")
daily_summary.orderBy("tx","date_day").repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result + '/daily_summary')

hourly_summary = pw_df_resampled.groupBy("tx",F.hour("time").alias("hour")).agg(F.avg("grid_voltage").alias("average_measured_voltage"),
                                                    total_measurements_in_period.alias("total_measurements"),
                                                    valid_voltage_condition.alias("valid_voltage"),
                                                    under_voltage_condition.alias("under_voltage"),
                                                    over_voltage_condition.alias("over_voltage"),
                                                    measurements_with_one_sensor.alias("one_sensor"),
                                                    measurements_with_two_sensors.alias("two_sensors"),
                                                    measurements_with_three_sensors.alias("three_sensors"),
                                                    measurements_with_zero_sensors.alias("zero_sensors"),
                                                    power_out.alias("power_out"),
                                                    power_on.alias("power_on"),
                                                    power_ambiguous.alias("power_ambiguous"))

hourly_summary = hourly_summary.select("tx","hour",
                                    F.round(((col("under_voltage")/col("total_measurements"))*100),1).alias("Percent Monitored Time Under Voltage"),
                                    F.round(((col("over_voltage")/col("total_measurements"))*100),1).alias("Percent Monitored Time Over Voltage"),
                                    F.round(((col("power_out")/col("total_measurements"))*100),1).alias("Percent Monitored Time Power Out"),
                                    F.round(((col("power_ambiguous")/col("total_measurements"))*100),1).alias("Percent Monitored Time Power Ambiguous"))


hourly_summary = hourly_summary.orderBy("tx","hour")
hourly_summary.orderBy("tx","hour").repartition(1).write.format("com.databricks.spark.csv").mode('overwrite').option("header", "true").save(args.result + '/hourly_summary')

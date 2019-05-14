#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, dayofmonth, collect_list, lit, year, coalesce, mean
import pyspark.sql.functions as F
from pyspark.sql.window import Window
from pyspark.sql.types import FloatType, IntegerType, DateType, TimestampType
from pyspark import SparkConf
import yaml
import datetime
import os
from math import isnan

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
    if(unplugMillis == 0 or millis == None or unplugMillis == None or isnan(millis) or isnan(unplugMillis)):
        return time
    elif unplugMillis > millis:
        return time
    else:
        return time - datetime.timedelta(microseconds = (int(millis)-int(unplugMillis))*1000)
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
w = Window.orderBy(asc("outage_time")).rowsBetween(-1*window_size,window_size)
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
#now we need to collapse these individual outage events into actual outages
#we can use a similar method of windowing as before but only look at the row before
w = Window.orderBy(asc("outage_time"))
outage_time_lag = lag("outage_time",1).over(w)
def onlyOutages(time, lag_time):
    if(lag_time is not None):
        if((time - lag_time).total_seconds() < 120):
            return 0
        else:
            return 1
    else:
        return 1

udfFilterTransition = udf(onlyOutages, IntegerType())
pw_df = pw_df.withColumn("outage_cluster", udfFilterTransition("outage_time",outage_time_lag))
pw_df = pw_df.filter("outage_cluster = 1")
pw_df = pw_df.select("outage_time","outage_cluster_size")

#okay we now have a list of all Powerwatch outages with an outage time
#now we should take all DW unplug events and for each outage see how many unplug events occur
dw_df = spark.read.jdbc("jdbc:postgresql://timescale.lab11.eecs.umich.edu/powerwatch", "dumsorwatch",
        properties={"user": config['user'], "password": config['password'],"driver":"org.postgresql.Driver"})

#read the data that we care about
dw_df = dw_df.select(dw_df['phone_imei'],dw_df['time'],dw_df['type'],dw_df['fft_cnt'],dw_df['fft_base'])
dw_df = dw_df.filter(year("time") == 2018)

#get the avg fft_cnt as a baseline
#plugged_50_df = dw_df.filter("type = 'plugged' AND fft_cnt > -1 AND (fft_base = 50 OR fft_base = '50hz')")
#plugged_50_df.select(mean('fft_cnt')).show()
#print(plugged_50_df.count())
#plugged_60_df = dw_df.filter("type = 'plugged' AND fft_cnt > -1 AND (fft_base = 60 OR fft_base = '60hz')")
#plugged_60_df.select(mean('fft_cnt')).show()
#print(plugged_60_df.count())
#
#unplugged_50_df = dw_df.filter("type = 'unplugged' AND fft_cnt > -1 AND (fft_base = 50 OR fft_base = '50hz')")
#unplugged_50_df.select(mean('fft_cnt')).show()
#print(unplugged_50_df.count())
#unplugged_60_df = dw_df.filter("type = 'unplugged' AND fft_cnt > -1 AND (fft_base = 60 OR fft_base = '60hz')")
#unplugged_60_df.select(mean('fft_cnt')).show()
#print(unplugged_60_df.count())

dw_df = dw_df.select("phone_imei","time","fft_cnt","fft_base")
#dw_df = dw_df.filter("type = 'unplugged' OR type = 'plugged'")

#okay now we want the fft_cnt drop between plugged and unplugged for each phone imei
#we will first try per-plug differencing (although it may be better to take the averaged plug cnt for each phone)
#w = Window.groupBy('phone_imei').orderBy(asc("time"))
#fft_lag = lag("fft_cnt",1).over(w)


dw_df = dw_df.filter("type = 'unplugged'")
#now we need to join the data on time/outage time
joined_df = pw_df.join(dw_df, col("outage_time") == col("time"), "fullouter")
joined_df = joined_df.withColumn("agg_time",coalesce("outage_time","time"))
joined_df = joined_df.select("agg_time","phone_imei","outage_cluster_size","fft_cnt","fft_base")

#now run a similar metric as above
#for each outage time we need to see how many DW unplug events occurred within some time window
window_size = 100
w = Window.orderBy(asc("agg_time"),).rowsBetween(-1*window_size,window_size)
joined_df = joined_df.withColumn("imei_list",collect_list(F.struct("agg_time","phone_imei")).over(w))

def filterOutage(time, phone_imei, imeiList):
    count = 0

    #we want phone imei to be none
    if phone_imei is not None:
        return -1

    used = []
    for i in imeiList:
        if abs((time - i[0]).total_seconds()) < 15 and i[1] not in used:
            used.append(i[1])
            count += 1

    if count > window_size:
        return window_size
    else:
        return count

udfFilterTransition = udf(filterOutage, IntegerType())
joined_df = joined_df.withColumn("phones_detecting", udfFilterTransition("agg_time","phone_imei","imei_list"))

#For the same window size average the fft_cnt to see if there is a signal in that metric
window_size = 100
w = Window.orderBy(asc("agg_time"),).rowsBetween(-1*window_size,window_size)
joined_df = joined_df.withColumn("fft_list",collect_list(F.struct("agg_time","phone_imei","fft_cnt","fft_base")).over(w))

def filterOutage(time, phone_imei, imeiList, base, return_count):
    count = 0
    fft_cnt = 0

    #we want phone imei to be none
    if phone_imei is not None:
        return -1

    for i in imeiList:
	if(base == 50):
            if abs((time - i[0]).total_seconds()) < 15 and i[2] is not None and (i[3] == '50' or i[3] == '50hz'):
                count += 1
	        fft_cnt += i[2]
	elif(base == 60):
            if abs((time - i[0]).total_seconds()) < 15 and i[2] is not None and (i[3] == '60' or i[3] == '60hz'):
                count += 1
	        fft_cnt += i[2]
    
    if(count == 0):
        return float(-1)
  
    print("{},{},{}".format(base, count, float(fft_cnt)/float(count)))
    if(return_count):
        return float(count)
    else:
        return float(float(fft_cnt)/float(count))

udfFilterTransition = udf(filterOutage, FloatType())
joined_df = joined_df.withColumn("avg_50_fft_cnt", udfFilterTransition("agg_time","phone_imei","fft_list",lit(50),lit(False)))
joined_df = joined_df.withColumn("50_fft_cnt", udfFilterTransition("agg_time","phone_imei","fft_list",lit(50),lit(True)))
joined_df = joined_df.withColumn("avg_60_fft_cnt", udfFilterTransition("agg_time","phone_imei","fft_list",lit(60),lit(False)))
joined_df = joined_df.withColumn("60_fft_cnt", udfFilterTransition("agg_time","phone_imei","fft_list",lit(60),lit(True)))

#now remove all of the phone records
joined_df = joined_df.filter("phones_detecting > -1")
joined_df = joined_df.select("agg_time","outage_cluster_size","phones_detecting","avg_50_fft_cnt","50_fft_cnt","avg_60_fft_cnt","60_fft_cnt")
joined_df.show(1000)


#now join the two datasets together so that we have a 

#pw_df = pw_df.select("time","outage_duration","outage_cluster_size")
#pw_df = pw_df.withColumn("outage_events",lit(1))
#pw_df = pw_df.groupBy(month("time"),"outage_cluster_size").sum().orderBy(month("time"),"outage_cluster_size")
#pw_df = pw_df.select("month(time)","outage_cluster_size","sum(outage_duration)","sum(outage_events)")
#pw_df = pw_df.withColumn("num_outages",pw_df["sum(outage_events)"]/pw_df["outage_cluster_size"])
#pw_df.show(500)
#pw_cp = pw_df
#
##now filter out all single outages
#pw_df = pw_df.filter("outage_cluster_size > 1")
#pw_df = pw_df.select("month(time)","sum(outage_duration)","sum(outage_events)","num_outages")
#pw_df = pw_df.groupBy("month(time)").sum().orderBy("month(time)")
#pw_df = pw_df.show(200)
#
#pw_cp.repartition(1).write.format("com.databricks.spark.csv").option("header", "true").save("monthly_outages_aggregate_cluster_size_time_corrected").groupBy(month("time")).sum().orderBy(month("time"))

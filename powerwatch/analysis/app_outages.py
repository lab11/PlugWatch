#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, year, lit, when, collect_list, struct
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

##now only keep the outages that had another outage
#def filterOutage(timeNow, tb1, tb2, tb3, tb4, tb5, tb6, tb7, tb8, tb9, tb10, ta1, ta2, ta3, ta4, ta5, ta6, ta7, ta8, ta9, ta10):
#    count = 0
#    tb = [tb1, tb2, tb3, tb4, tb5, tb6, tb7, tb8, tb9, tb10]
#    ta = [ta1, ta2, ta3, ta4, ta5, ta6, ta7, ta8, ta9, ta10]
#    for i in range(0,10):
#        if(tb[i] is not None):
#            if(timeNow - tb[i] < datetime.timedelta(seconds=10)):
#                count += 1
#
#    for i in range(0,3):
#        if(ta[i] is not None):
#            if(ta[i] - timeNow < datetime.timedelta(seconds=10)):
#                count += 1
#
#    if count > 10:
#        return 10
#    else:
#        return count
#
#udfFilterTransition = udf(filterOutage, IntegerType())
#w = Window.orderBy(asc("time"))
#
#ta1 = (lead("time",1).over(w))
#ta2 = (lead("time",2).over(w))
#ta3 = (lead("time",3).over(w))
#ta4 = (lead("time",4).over(w))
#ta5 = (lead("time",5).over(w))
#ta6 = (lead("time",6).over(w))
#ta7 = (lead("time",7).over(w))
#ta8 = (lead("time",8).over(w))
#ta9 = (lead("time",9).over(w))
#ta10 = (lead("time",10).over(w))
#
#tb1 = (lag("time",1).over(w))
#tb2 = (lag("time",2).over(w))
#tb3 = (lag("time",3).over(w))
#tb4 = (lag("time",4).over(w))
#tb5 = (lag("time",5).over(w))
#tb6 = (lag("time",6).over(w))
#tb7 = (lag("time",7).over(w))
#tb8 = (lag("time",8).over(w))
#tb9 = (lag("time",9).over(w))
#tb10 = (lag("time",10).over(w))
#pw_df = pw_df.withColumn("outage_cluster_size", udfFilterTransition("time", tb1, tb2, tb3, tb4, tb5, tb6, tb7, tb8, tb9, tb10, ta1, ta2, ta3, ta4, ta5, ta6, ta7, ta8, ta9, ta10))

window_size = 100
w = Window.orderBy(asc("time")).rowsBetween(-1*window_size,window_size)
pw_df = pw_df.withColumn("outage_window_list",collect_list(F.struct("time","phone_imei")).over(w))

def filterOutage(time, imei, timeList):
    count = 0
    used = []
    used.append(imei)
    print("")
    print("")
    print(time)
    for i in timeList:
        if abs((time - i[0]).total_seconds()) < 1.5 and i[1] not in used:
            used.append(i[1])
            print("counting")
            count += 1

    if count > window_size:
        return window_size
    else:
        return count

udfFilterTransition = udf(filterOutage, IntegerType())
pw_df = pw_df.withColumn("outage_cluster_size", udfFilterTransition("time","phone_imei","outage_window_list"))
pw_df = pw_df.withColumn("possible_outage", lit(int(1)))
pw_df.groupBy(month("time"),"outage_cluster_size").sum().orderBy(month("time"),"outage_cluster_size").show(700)

#w = Window.partitionBy(month("time")).orderBy(asc("outage_cluster_size")).rowsBetween(0,Window.unboundedFollowing)
#pw_df = pw_df.withColumn("cluster_size_cumulative",F.sum("possible_outage").over(w))
#pw_df = pw_df.select("time","outage_cluster_size","cluster_size_cumulative")
#pw_df.groupBy(month("time"),"outage_cluster_size").max().orderBy(month("time"),"outage_cluster_size").show(700)

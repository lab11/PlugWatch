#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, mean
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
    .appName("GPS Fix Calculator") \
    .getOrCreate()

config = open('config.yaml')
config = yaml.load(config)

#connect to the database
pw_df = spark.read.jdbc("jdbc:postgresql://timescale.lab11.eecs.umich.edu/powerwatch", "pw_dedupe",
        properties={"user": config['user'], "password": config['password'],"driver":"org.postgresql.Driver"})

#read the data that we care about
pw_df = pw_df.select(pw_df['core_id'],pw_df['time'],pw_df['gps_satellites'],pw_df['product_id'])
pw_df = pw_df.filter(month("time") >= 7)
pw_df = pw_df.select(pw_df['core_id'],pw_df['gps_satellites'],pw_df['product_id'])
pw_df = pw_df.filter("product_id = 7008 OR product_id= 7009")
pw_df = pw_df.filter("NOT isnan(gps_satellites)")

pw_df = pw_df.groupBy("core_id").max()

#now count each outage (really restoration)
def gotFix(value1):
    if(value1 > 0):
        return 1
    else:
        return 0
udfgotFix = udf(gotFix, IntegerType())

pw_df = pw_df.withColumn("got_fix", udfgotFix("max(gps_satellites)"))
pw_df.repartition(1).write.format("com.databricks.spark.csv").option("header", "true").save("gps_fix")
pw_df.select(mean("got_fix")).show()

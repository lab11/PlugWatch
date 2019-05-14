#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf, hour, month, when, lit, sum
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
pw_df = pw_df.select(pw_df['core_id'],pw_df['time'],pw_df['product_id'],pw_df['system_loop_count'])
pw_df = pw_df.filter("product_id = 7008 OR product_id= 7009")
pw_df = pw_df.withColumn("num_loops", lit(1))

w = Window.partitionBy("core_id").orderBy(asc("time"))
loop_count_lag = lag("system_loop_count",1).over(w)
pw_df = pw_df.withColumn("skipped_loops", when(pw_df['system_loop_count'] - loop_count_lag < 0, 0).otherwise(pw_df['system_loop_count'] - loop_count_lag - 1))

pw_df = pw_df.withColumn("theoretical_loops", pw_df['skipped_loops'] + 1)

pw_df = pw_df.groupBy("core_id").sum()
pw_df.show(200)
pw_df.repartition(1).write.format("com.databricks.spark.csv").option("header", "true").save("data_reporting_percent_per_device")

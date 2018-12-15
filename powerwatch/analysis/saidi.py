#!/usr/bin/env python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, window, asc, desc, lead, lag, udf
from pyspark.sql.window import Window
from pyspark.sql.types import IntegerType
from pyspark import SparkConf
import yaml

conf = SparkConf()
conf.set("spark.jars", "/Users/adkins/.ivy2/jars/org.postgresql_postgresql-42.1.1.jar")
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
pw_df = pw_df.select(pw_df['core_id'],pw_df['time'],pw_df['is_powered'])
#pw_df = pw_df.filter("core_id == '2b002c001251363038393739'")

#now we need to created a window function that looks at the leading lagging edge of is powered and detects transitions
#then we can filter out all data that is not a transition
def detectTransition(value1, value2):
    if(value1 == value2):
        return 0
    else:
        return 1

udfDetectTransition = udf(detectTransition, IntegerType())
w = Window.partitionBy("core_id").orderBy(asc("time"))

pw_df = pw_df.withColumn("transition", udfDetectTransition("is_powered",lead("is_powered", 1).over(w)))
pw_df = pw_df.filter("transition != 0")

def countOutage(value1, value2):
    if(value1 == False and value2 == True):
        return 1
    else:
        return 0
udfCountTransition = udf(countOutage, IntegerType())
pw_df = pw_df.withColumn("outage", udfCountTransition("is_powered", lead("is_powered", 1).over(w)))
pw_df = pw_df.groupBy("outage").sum().show()
#pw_df = pw_df.groupBy("core_id",window(col("time").cast("timestamp"),windowDuration="30 days",slideDuration="30 days"))

#!/usr/bin/env python3
from pyspark.sql import SparkSession
from pyspark import SparkConf
import yaml

conf = SparkConf()
conf.set("spark.jars", "Users/adkins/.ivy2/jars/org.postgresql_postgresql-42.1.1.jar")

spark = SparkSession.builder \
    .config(conf=conf) \
    .master("local") \
    .appName("SAIDI Calculator") \
    .getOrCreate()

config = open('config.yaml')
config = yaml.load(config)

pw_df = spark.read.jdbc("jdbc:postgresql://timescale.lab11.eecs.umich.edu/powerwatch", "pw_dedupe",
        properties={"user": config['user'], "password": config['password'],"driver":"org.postgresql.Driver"})

pw_df = pw_df.select(pw_df['core_id'],pw_df['time'],pw_df['is_powered'])

pw_df.printSchema()

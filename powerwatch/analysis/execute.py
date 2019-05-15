#!/usr/bin/env python3
import os
import sys
import argparse
import subprocess
import json

parser = argparse.ArgumentParser(description = 'Create a dataproc cluster')
parser.add_argument('script')
parser.add_argument('-n','--cluster-name', type=str, required=False) #name of the cluster so you can create multiple
parser.add_argument('-m','--master-machine-type', type=str, required=False)
parser.add_argument('-w','--worker-machine-type', type=str, required=False)
parser.add_argument('-j','--num-workers', type=int, required=False)
parser.add_argument('-z','--zone', type=int, required=False)
args = parser.parse_args()


#gather cluster variables
cluster_name = 'powerwatch-analysis'
num_workers = 4
worker_machine_type = 'n1-standard-4'
master_machine_type = 'n1-standard-4'
bucket = 'powerwatch-analysis'
zone = 'us-west1-b'

if(args.zone):
    zone = args.zone

if(args.num_workers):
    num_workers = args.num_workers

if(args.worker_machine_type):
    worker_machine_type = args.worker_machine_type

if(args.master_machine_type):
    master_machine_type = args.master_machine_type

if(args.cluster_name):
    cluster_name = args.cluster_name
    bucket = args.cluster_name

config = None
with open('config.json') as data:
    config = json.load(data)

#create the cloud storage bucket if it doesn't already exist
print("Creating storage bucket...")
try:
    subprocess.check_output(['gsutil', 'mb', 'gs://' + bucket], stderr=subprocess.STDOUT)
except Exception as e:
    if type(e) is subprocess.CalledProcessError and str(e.output, 'utf-8').find('409') != -1:
        print("Storage bucket already exists. Proceeding...")
        print()
    else:
        print(e.output);
        raise e

#list the existing clusters
output = subprocess.check_output(['gcloud', 'dataproc', 'clusters', 'list'])
cluster_exists = str(output,'utf-8').find(cluster_name) != -1;

if(cluster_exists == False):
    #create the cluster
    print("Creating cluster");
    subprocess.check_call(['gcloud', 'beta', 'dataproc','clusters','create',
                            cluster_name,
                            '--zone=' + zone,
                            '--max-idle=30m',
                            '--num-workers=' + str(num_workers),
                            '--worker-machine-type=' + worker_machine_type,
                            '--master-machine-type=' + master_machine_type,
                            '--bucket=' + bucket])

    print()
else:
    print("Cluster already exists. Proceeding...")
    print()

#run the analysis script script with the output argument, and the driver
#first copy the postgres driver to the bucket
print("Setting up driver");
subprocess.check_call(['gsutil', 'cp', './postgres-driver/org.postgresql.jar','gs://' + bucket + '/org.postgresql.jar'])
print()

#next execute the script
result_name = args.script.split('.')[0]
folder_name = args.script.split('/')
if(len(folder_name) > 1):
    folder_name = folder_name[:-1].join('/')
else:
    folder_name = ""

print("Starting job");
try:
    subprocess.check_call(['gcloud', 'dataproc', 'jobs','submit','pyspark',
                          args.script,
                          '--cluster=' + cluster_name,
                          '--jars=gs://' + bucket + '/org.postgresql.jar',
                          '--properties=spark.driver.extraClassPath=gs://' + bucket + '/org.postgresql.jar',
                          '--',
                          'gs://' + bucket + '/' + result_name,
                          config['user'],
                          config['password']])
except:
    print("Error executing analysis script")
    sys.exit(1)

print()
print()

#now copy the results to the local folder
print("Job complete. Copying results");
try:
    subprocess.check_call(['gsutil', 'cp', '-r', 'gs://' + bucket + '/' + result_name, './' + folder_name])
except:
    print("Error retrieving results")

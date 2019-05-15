Running PowerWatch Analysis
===========================

This folder is setup to execute pyspark scripts on a cloud dataproc cluster

These should work on any gcloud project with the dataproc enabled and billing enabled.

I usually use the powerwatch-backend gcloud project.

## Executing scripts

If you want to run one script you can simply 

```
$ ./execute.py my_analysis_folder/my_analysis_script.py
```

This script will in order
 - Check if a cluster exists, create a cluster if it does not. Cluster shuts down after 30 minutes of inactivity.
 - Setup the cluster environment to run the script (including drivers and such)
 - Execute the analysis script and pass in the results location
 - Copy the resulting csv(s) into the folder with the script
 - Hang until done and print output from the job

Persistent Volume Claim Snapshot
===============================

This is a container for automatically creating google cloud snapshots of the 
timescale and influx persistent volumes bound to the dynamic claims. This should incrementally
backup the databases.

It automatically identifies the cluster, project and volumes associated with that cluster in the project

Made to be run as a kubernets CronJob

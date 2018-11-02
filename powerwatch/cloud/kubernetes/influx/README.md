Timescale
=================================

To deploy the timescale instance follow these steps:

## Add secrets

Modify the secrets files to add the database usernames and passwords.
These must be base64 encoded.

```
$ kubectl create -f ./influx-config.yaml
```

## Deploy the cluster
```
$ kubectl create -f influx-deployment.yaml
```

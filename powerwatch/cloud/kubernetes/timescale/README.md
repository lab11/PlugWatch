Timescale
=================================

To deploy the timescale instance follow these steps:

## Setup a configuration file

This mounts a file to initialize the grafana user

```
$ kubectl create configmap timescale-initialization --from-file=initialize-read-user.sh
```

## Add secrets

Modify the secrets files to add the database usernames and passwords

```
$ kubectl create -f ./timescale-config.yaml
```

## Deploy the cluster
```
$ kubectl create -f timescale-deployment.yaml
```

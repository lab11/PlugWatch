Timescale
=================================

To deploy the grafana instance follow these steps:

## Setup a configuration file

```
$ kubectl create configmap grafana-provisioning --from-file=datasource.yaml --from-file=dashboard.yaml
```

## Add secrets

Modify the secrets files to add the database usernames and passwords

```
$ kubectl create -f ./grafana-config.yaml
```

## Deploy the cluster
```
$ kubectl create -f ./grafana-deployment.yaml
```

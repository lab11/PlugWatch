Powerwatch Deployment Manager
=================================

## Setup your configuration files

Modify postgres-config.json, survey-config.json, oink-config.json to fit the parameters of your
deployment then add then as a kubernetes configmap

```
$ kubectl create configmap deployment-manager-config --from-file=postgres-config.json --from-file=survey-config.json
```

## Add secrets

Modify the secrets files to add the database usernames and passwords.

Note that the values in manually created secret files are base64 encoded

```
$ kubectl create secret generic oink-service-account --from-file=oink-service-account.json
$ kubectl create -f ./keybase-paperkey.yaml
$ kubectl create -f ./survey-user-pass.yaml
$ kubectl create -f ./postgres-user-pass.yaml
```

## Deploy the cluster
```
$ kubectl apply -f powerwatch-deployment-manager-deployment.yaml
```

Powerwatch Data Poster Deployment
=================================

To deploy the powerwatch visualization perform the following steps. Ideally
they are done in some kind of managed kubernetes cluster engine.

## Setup your configuration files

Modify postgres.json to fit the parameters of your
deployment then add then as a kubernetes configmap. Do not run this command
if you config is already present in the cluster!

```
$ kubectl create configmap config --from-file=postgres.json
```

## Add secrets

Modify the secrets files to add the database usernames and passwords.

```
$ kubectl create -f ./postgres-user-pass.yaml
```

## Deploy the cluster
```
$ kubectl create -f powerwatch-visualization-deployment.yaml
```

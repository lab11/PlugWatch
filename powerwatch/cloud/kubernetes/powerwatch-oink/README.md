Powerwatch Oink
===============

Cron jobs to run powerwatch oink

## config files

```
$ kubectl create configmap config --from-file=postgres.json --from-fil=oink.json
```

## secrets

```
$ kubectl create -f ./twilio-key.yaml
$ kubectl create -f ./korba-key.yaml
$ kubectl create -f ./postgres-user-pass.yaml
```

## deploy
```
$ kubectl create -f powerwatch-oink.yaml
```

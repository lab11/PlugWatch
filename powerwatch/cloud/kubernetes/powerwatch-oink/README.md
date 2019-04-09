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

##getting the whitelisted IP
Korba needs a whitelisted IP. You need to set a label on a node in your pool
```korba-node=true```
Then assign that node as static IP described here:
https://stackoverflow.com/questions/41133755/static-outgoing-ip-in-kubernetes

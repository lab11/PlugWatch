Powerwatch MQTT Poster Deployment
=================================

To deploy the powerwatch mqtt poster perform the following steps. Ideally
they are done in some kind of managed kubernetes cluster engine.

## Setup your configuration files

Modify config.json, webhook.json to fit the parameters of your
deployment then add then as a kubernetes configmap

```
$ kubectl create configmap config --from-file=config.json --from-file=webhook.json
```

## Add secrets

Modify the secrets files to add the database usernames and passwords
as well as the particle authentication token

```
$ kubectl create -f ./particle-auth-token.yaml
$ kubectl create -f ./webhook-pass.yaml
```

## Deploy the cluster
```
$ kubectl apply -f powerwatch-mqtt-poster.yaml
```

## The easy way

Running ./instantiate.py will setup everything if pointed at the right cluster and gcloud project

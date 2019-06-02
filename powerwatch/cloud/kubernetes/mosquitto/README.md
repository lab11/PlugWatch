Mosquitto MQTT
=================================

A barebones pub/sub to run inside the kubernetes cluster. Mainly used for
easy stream processing at the moment. Only accessible within the cluster.

## Deploy the cluster
```
$ kubectl apply -f ./mosquitto-deployment.yaml
```

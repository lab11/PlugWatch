PowerWatch Cloud Services
=========================

The powerwatch cloud is a set of services that consume data from sensors,
store that data, visualize the data, manage the sensor deployment, send
feedback about performance to the field team, and pay our participants.

This is broken down in to a set of discrete services that run in K8s.

Some of these services (like timescale and grafana) are pre-existing and
we just write manifests for them. Some of them we write ourselves and 
build Docker containers for.

A picture of the current architecture is outlined below.

<img src="./PowerWatch_Cloud.svg">

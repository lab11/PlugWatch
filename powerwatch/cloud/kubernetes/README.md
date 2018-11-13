Deploying a Powerwatch Backend
==============================

## Requirements

To deploy a powerwatch backend instance you need to have google cloud installed
and logged into a google account which has been granted an admin role on the 
powerwatch-backend project.

To check:
```
gcloud config set project powerwatch-backend
```

## Start a new powerwatch backend

To startup a new powerwatch backend you should 
run the provisioning script with the deployment name and particle product IDs
```
$ ./powerwatch_provision.py --name venezuela --product 7300 --product 7301
```

This will perform the following steps

1) Create a default cluster in gcloud under the powerwatch-backend project
2) Generate, substitute and print passwords for the following individual services:
timescale (admin user, poster user, grafana user)
influxdb (admin user, poster user, grafana user)
grafana (admin user)
3) Generate and substitute a particle auth token for those products
4) Packages up all the deployment files and 
save them to a deployment folder
5) Deploy a new timescale, influx, grafana, and the powerwatch poster services
to the target cluster
6) Print the IP address of the grafana ingress so that you can configure a new
subdomain for this deployment's grafana

## Update a powerwatch backend
The powerwatch-data-poster and powerwatch-visualization pods are custom pods
running images hosted by the lab11 docker hub. They are currently set to pull
the production branch of this repository. To update these images:

1) Push your changes to the production branch (probably just merge production with master or test).
2) Wait for docker hub to build the images
3) Pull the images - copy down their sha256 hash
```
$ docker pull lab11/container-name:production
```
4) Point your kubectl at the cloud cluster you are updating (copy the 'connect' command text from the gcloud web UI)
5) Update the images to the sha256 hash. For instance to update the influx poster run the following command:
```
$ kubectl set image deployment powerwatch-data-poster powerwatch-influx-poster=lab11/powerwatch-influx-poster@sha256:HASH
```

You can also update multiple images at once. This will perform a no-downtime rolling update of the containers that you specified.

Pointing to a commit hash is better than a branch because it enables easier rollback and it is easier to identify exactly
which version of a container you are running (if you just pull the 'production' container 
docker hub's production tag will get out of sync with the tag running in the cluster as more commits are pushed).

## Future Work

There are several clear things that need updating moving forward:

 - The internal cluster communications should use SSL
 - We need to get the powerwatch visualization up and running and get its SSL working
 - Make a splash page for the each deployment
 - Potential hooks to lastpass for password storage for the group

Deploying a Powerwatch Backend
==============================

## This is still a work in progress

To startup a new powerwatch backend you should 
run the provisioning script with the deployment name and particle product IDs
```
$ ./powerwatch_provision.py --name venezuela --product 7300
```

This will perform the following steps

1) Create a default cluster in gcloud under the powerwatch project
2) Generate, substitute and print passwords for the following individual services:
timescale (admin user, poster user, grafana user)
influxdb (admin user, poster user, grafana user)
grafana (admin user)
3) Generate and substitute a particle auth token for those products
4) Packages up all the deployment files (with passwords redacted) and 
save them to a deployment folder
5) Deploy a new timescale, influx, grafana, and the powerwatch poster services
to the target cluster
6) Print the IP address of the grafana ingress so that you can configure a new
subdomain for this deployment's grafana

The script may ask you to do things like login to google cloud or the
particle CLI.

## Future Work

There are several clear things that need updating moving forward:

 - The Grafana page should have an SSL Cert through Let's Encrypt
 - The internal cluster communications should use SSL
 - We need to get the powerwatch visualization up and running and get its SSL working
 - We could look into auto-registering the domain name with google domains
    and making a splash page for the each deployment/modifying a central
    page that acts as a registry for each deployment
 - Potential hooks to lastpass for password storage for the group

#!/usr/bin/env python3
import googleapiclient.discovery
from kubernetes import client, config
import requests
import time
import os

#get all the persistent volume claims
if "KUBERNETES_SERVICE_HOST" in os.environ:
    config.load_incluster_config()
else:
    config.load_kube_config()

v1 = client.CoreV1Api()
pvcs = v1.list_persistent_volume_claim_for_all_namespaces()

#we really need the source disk name for the pvcs that we care about
pvs = []
for item in pvcs.items:
    if(item.metadata.name == 'influx-pv-claim' or item.metadata.name == 'timescale-pv-claim'):
        pvs.append((item.metadata.uid,item.metadata.name.split('-')[0]))

#get the project ID and region that this instance is a part of
r = requests.get("http://metadata.google.internal/computeMetadata/v1/project/project-id",headers={'Metadata-Flavor':'Google'})
print(r.text)
project = r.text
r = requests.get("http://metadata.google.internal/computeMetadata/v1/instance/attributes/cluster-name",headers={'Metadata-Flavor':'Google'})
print(r.text)
cluster = r.text

compute = googleapiclient.discovery.build('compute','v1')

#get all zones used by the project
available = compute.zones().list(project=project).execute()
zones = []
for zone in available['items']:
    zones.append(zone['name'])

#get all the disks in the project
disks = []
for zone in zones:
    disk = compute.disks().list(project=project, zone=zone).execute()
    if 'items' in disk:
        for d in disk['items']:
            disks.append((d['name'], zone))

#find the full disk names for what was being used by kubernetes above
disks_to_snapshot = []
for d in disks:
    for volume in pvs:
        if d[0].find(volume[0]) != -1:
            disks_to_snapshot.append((d,volume))

print('Creating the following snapshots:')
print(disks_to_snapshot)
#now create snapshots of each disk to snapshot
for disk in disks_to_snapshot:
    compute.disks().createSnapshot(project=project,
                                zone=disk[0][1], #we stored the zone in the disk tuple
                                disk=disk[0][0],
                                body={"name":'-'.join(
                                    [cluster,
                                    disk[1][1],
                                    str(int(time.time()))])}).execute() #name=cluster-influx/timescale-int(epoch)

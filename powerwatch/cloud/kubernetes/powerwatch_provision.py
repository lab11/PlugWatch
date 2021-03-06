#!/usr/bin/env python3.6
import sys
import os
import secrets
import string
alphabet = string.ascii_letters + string.digits
import argparse
import requests
import getpass
import json
import shutil
import base64
import glob
import subprocess

parser = argparse.ArgumentParser(description = 'Provision and deploy powerwatch backend')
parser.add_argument('-p','--product', type=int, required=True, action='append')
parser.add_argument('-n','--name', type=str, required=True)
parser.add_argument('-c','--cluster', type=str, required=True)
parser.add_argument('-j','--redundancy', type=int, required=False)
parser.add_argument('-r','--region', type=str, required=False)
args = parser.parse_args()

region = ""
if(args.region is None):
    region = "us-west1"
else:
    region = args.region

# Gather/generate the necessary information
timescale_user = str(base64.b64encode('powerwatch'.encode('ascii')), 'utf-8')
timescale_password = str(base64.b64encode(''.join(secrets.choice(alphabet) for i in range(12)).encode('ascii')), 'utf-8')
influx_user = str(base64.b64encode('powerwatch'.encode('ascii')), 'utf-8')
influx_password = str(base64.b64encode(''.join(secrets.choice(alphabet) for i in range(12)).encode('ascii')), 'utf-8')
influx_admin_user = str(base64.b64encode('admin'.encode('ascii')), 'utf-8')
influx_admin_password = str(base64.b64encode(''.join(secrets.choice(alphabet) for i in range(12)).encode('ascii')), 'utf-8')
grafana_database_user = str(base64.b64encode('grafana'.encode('ascii')), 'utf-8')
grafana_database_user_clear = 'grafana'
grafana_database_password_clear = ''.join(secrets.choice(alphabet) for i in range(12))
grafana_database_password = str(base64.b64encode(grafana_database_password_clear.encode('ascii')), 'utf-8')
grafana_admin_password = str(base64.b64encode(''.join(secrets.choice(alphabet) for i in range(12)).encode('ascii')), 'utf-8')
product_ids = str(args.product)

#now get the particle authentication token using the particle API
print()
print('Generating a particle access token...')
username = input('Particle username:')
password = getpass.getpass('Particle password:')
resp =  requests.post('https://api.particle.io/oauth/token', data={'username':username, 'password':password,'grant_type':'password','expires_in':0}, auth=('particle', 'particle'))
access_token = str(base64.b64encode(json.loads(resp.text)['access_token'].encode('ascii')), 'utf-8')

print()
print('Configuring new cluster for these products...')
#check and see if a deployment of this type already exists
if os.path.isdir(args.name+'_deployment'):
    answer = input('Local deployment folder with name ' + args.name + ' already exists. Overwrite this folder? [Y/n]')
    if answer == 'y' or answer == 'Y' or answer == 'Yes' or answer == 'yes':
        #copy all of the kubernetes configuration files into a staging area
        dest = args.name+'_deployment'
        shutil.rmtree(dest)
        os.mkdir(dest)
        shutil.copytree('timescale', dest + '/timescale')
        shutil.copytree('influx', dest + '/influx')
        shutil.copytree('grafana', dest + '/grafana')
        shutil.copytree('powerwatch-data-poster', dest + '/powerwatch-data-poster')
        shutil.copytree('powerwatch-visualization', dest + '/powerwatch-visualization')
        shutil.copytree('certificate', dest + '/certificate')
        shutil.copytree('namespace', dest + '/namespace')
    else:
        print('Remove folder or specify a different name.')
        sys.exit(1)
else:
    #copy all of the kubernetes configuration files into a staging area
    dest = args.name+'_deployment'
    os.mkdir(dest)
    shutil.copytree('timescale', dest + '/timescale')
    shutil.copytree('influx', dest + '/influx')
    shutil.copytree('grafana', dest + '/grafana')
    shutil.copytree('powerwatch-data-poster', dest + '/powerwatch-data-poster')
    shutil.copytree('powerwatch-visualization', dest + '/powerwatch-visualization')
    shutil.copytree('certificate', dest + '/certificate')
    shutil.copytree('namespace', dest + '/namespace')

#Make sure the project is set correctly
try:
    subprocess.check_call(['gcloud', 'config','set','project','powerwatch-backend'])
except Exception as e:
    print('Error setting the cloud project - do you the google cloud SDK isntalled and logged into an account with access to the powerwatch-backend project?')
    shutil.rmtree(dest)
    raise e

#check to see if a cluster of this name already exists or if we need to create a new one
#to do this try to deploy a cluster of the name set by cluster
print()
print('Deploying a new google cloud container cluster if necessary (this could take several minutes)...')
output = None
try:
    if args.redundancy is None:
        output = subprocess.check_output(['gcloud', 'container','clusters','create',args.cluster,
                            '--region', region,
                            '--num-nodes', '2',
                            '--disk-size', '10GB',
                            '--machine-type', 'n1-standard-1'], stderr=subprocess.STDOUT)
    else:
        output = subprocess.check_output(['gcloud', 'container','clusters','create',args.cluster,
                            '--region', region,
                            '--num-nodes', str(args.redundancy),
                            '--disk-size', '10GB',
                            '--machine-type', 'n1-standard-1'], stderr=subprocess.STDOUT)
except Exception as e:
    if type(e) is subprocess.CalledProcessError and str(e.output,'utf-8').find('Already exists') != -1:
        print('Cluster with name ' + args.cluster + ' already exists in this zone. Using existing cluster.')
    else:
        print(e.output)
        raise e

#CREATE regional IP addresses for loadbalancer services
timescale_ip_address = ''
try:
    subprocess.check_call(['gcloud', 'compute','addresses','create',
                        args.name+'-timescale','--region',region])
except Exception as e:
    print(e)

try:
    timescale_ip_address = subprocess.check_output(['gcloud', 'compute','addresses','describe',
                        args.name+'-timescale','--region',region])
    timescale_ip_address = timescale_ip_address.decode('utf-8').replace('\n',' ').split(' ')[1]
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','start',
                        '--zone','powerwatch'])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','add',
                        '--zone','powerwatch',
                        '--name','timescale.'+args.name+'.powerwatch.io',
                        '--type','A',
                        '--ttl','300',timescale_ip_address])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','execute',
                        '--zone','powerwatch'])
except Exception as e:
    os.remove('transaction.yaml')
    print(e)

#CREATE regional IP addresses for loadbalancer services
udp_ip_address = ''
try:
    subprocess.check_call(['gcloud', 'compute','addresses','create',
                        args.name+'-udp','--region',region])
except Exception as e:
    print(e)

try:
    udp_ip_address = subprocess.check_output(['gcloud', 'compute','addresses','describe',
                        args.name+'-udp','--region',region])
    udp_ip_address = udp_ip_address.decode('utf-8').replace('\n',' ').split(' ')[1]
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','start',
                        '--zone','powerwatch'])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','add',
                        '--zone','powerwatch',
                        '--name','udp.'+args.name+'.powerwatch.io',
                        '--type','A',
                        '--ttl','300',udp_ip_address])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','execute',
                        '--zone','powerwatch'])
except Exception as e:
    os.remove('transaction.yaml')
    print(e)


#CREATE a new globally routable ip address
grafana_ip_address = ''
try:
    subprocess.check_call(['gcloud', 'compute','addresses','create',
                        args.name+'-grafana','--global'])
except Exception as e:
    print(e)

try:
    grafana_ip_address = subprocess.check_output(['gcloud', 'compute','addresses','describe',
                        args.name+'-grafana','--global'])
    grafana_ip_address = grafana_ip_address.decode('utf-8').replace('\n',' ').split(' ')[1]
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','start',
                        '--zone','powerwatch'])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','add',
                        '--zone','powerwatch',
                        '--name','graphs.'+args.name+'.powerwatch.io',
                        '--type','A',
                        '--ttl','300',grafana_ip_address])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','execute',
                        '--zone','powerwatch'])
except Exception as e:
    os.remove('transaction.yaml')
    print(e)

#CREATE a new globally routable ip address
visualization_ip_address = ''
try:
    subprocess.check_call(['gcloud', 'compute','addresses','create',
                        args.name+'-powerwatch-visualization','--global'])
except Exception as e:
    print(e)

try:
    visualization_ip_address = subprocess.check_output(['gcloud', 'compute','addresses','describe',
                        args.name+'-powerwatch-visualization','--global'])
    visualization_ip_address = visualization_ip_address.decode('utf-8').replace('\n',' ').split(' ')[1]
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','start',
                        '--zone','powerwatch'])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','add',
                        '--zone','powerwatch',
                        '--name','vis.'+args.name+'.powerwatch.io',
                        '--type','A',
                        '--ttl','300',visualization_ip_address])
    subprocess.check_call(['gcloud', 'dns','record-sets','transaction','execute',
                        '--zone','powerwatch'])
except Exception as e:
    os.remove('transaction.yaml')
    print(e)

#now search and replace all of the templating variables with the generated values
for filepath in glob.iglob('./'+dest+'/**', recursive=True):
    if(not os.path.isdir(filepath)):
        with open(filepath) as file:
            s = file.read()

        s = s.replace('${NAMESPACE}', args.name)
        s = s.replace('${TIMESCALE_USER}', timescale_user)
        s = s.replace('${TIMESCALE_PASSWORD}', timescale_password)
        s = s.replace('${INFLUX_USER}', influx_user)
        s = s.replace('${INFLUX_PASSWORD}', influx_password)
        s = s.replace('${INFLUX_ADMIN_USER}', influx_admin_user)
        s = s.replace('${INFLUX_ADMIN_PASSWORD}', influx_admin_password)
        s = s.replace('${GRAFANA_DATABASE_USER}', grafana_database_user)
        s = s.replace('${GRAFANA_DATABASE_USER_CLEAR}', grafana_database_user_clear)
        s = s.replace('${GRAFANA_DATABASE_PASSWORD}', grafana_database_password)
        s = s.replace('${GRAFANA_DATABASE_PASSWORD_CLEAR}', grafana_database_password_clear)
        s = s.replace('${GRAFANA_PASSWORD}', grafana_admin_password)
        s = s.replace('${PRODUCT_IDS}', product_ids)
        s = s.replace('${PARTICLE_AUTH_TOKEN}', access_token)
        s = s.replace('${GRAFANA_IP_ADDRESS}', args.name+'-grafana')
        s = s.replace('${GRAFANA_DOMAIN_NAME}', 'graphs.'+args.name+'.powerwatch.io')
        s = s.replace('${TIMESCALE_IP_ADDRESS}', timescale_ip_address)
        s = s.replace('${TIMESCALE_DOMAIN_NAME}', 'timescale.'+args.name+'.powerwatch.io')
        s = s.replace('${UDP_IP_ADDRESS}', udp_ip_address)
        s = s.replace('${UDP_DOMAIN_NAME}', 'udp.'+args.name+'.powerwatch.io')
        s = s.replace('${POWERWATCH_VISUALIZATION_IP_ADDRESS}', args.name+'-powerwatch-visualization')
        s = s.replace('${POWERWATCH_VISUALIZATION_DOMAIN_NAME}', 'vis.'+args.name+'.powerwatch.io')
        with open(filepath, "w") as file:
            file.write(s)



#point the kubernetes python API at the new cluster
try:
    subprocess.check_call(['gcloud', 'container', 'clusters', 'get-credentials', args.cluster,
                            '--region', region,
                            '--project', 'powerwatch-backend'])
except Exception as e:
    shutil.rmtree(dest)
    raise e

#add our new namespace to the cluster
try:
    print("Creating deployment namespace.")
    subprocess.check_output(['kubectl', 'create', '-f', args.name + '_deployment/namespace/deployment-namespace.yaml'], stderr=subprocess.STDOUT)
except Exception as e:
    print(str(e.output,'utf-8'))
    if type(e) is subprocess.CalledProcessError and str(e.output,'utf-8').find('already exists') != -1:
        pass
    else:
        raise(e)

#get the cluster context. Google uses this as the cluster usename we need for the next step
output = None
try:
    output = subprocess.check_output(['kubectl', 'config', 'current-context'])
except Exception as e:
    raise(e)
output = str(output,'utf-8').rstrip()
print("Received current context {}".format(output))

#Create a cluster context for this deployment then start using it
try:
    subprocess.check_output(['kubectl', 'config', 'set-context', output + '-' + args.name,
                            '--cluster=' + output,
                            '--user=' + output,
                            '--namespace='+args.name])
except Exception as e:
    if type(e) is subprocess.CalledProcessError and str(e.output,'utf-8').find('Already exists') != -1:
        pass
    else:
        raise(e)

#Use the context you just created
try:
    subprocess.check_output(['kubectl', 'config', 'use-context', output + '-' + args.name])
except Exception as e:
    raise(e)

#now everything we do should be namespaced to our cluster and our context
#Add the tiller rbac role
try:
    subprocess.check_output(['kubectl', 'apply', '-f', 'cluster-rolebinding/tiller-rolebinding.yaml'])
except Exception as e:
    if type(e) is subprocess.CalledProcessError and str(e.output,'utf-8').find('Already exists') != -1:
        pass
    else:
        raise(e)

#Install tiller on the cluster
try:
    subprocess.check_output(['helm', 'init', '--service-account', 'tiller','--wait'])
except Exception as e:
    raise e

#install the helm cert-manager
try:
    subprocess.check_output(['helm', 'install',
                            '--name', 'cert-manager',
                            '--namespace', 'kube-system',
                            'stable/cert-manager',
                            '--version','v0.5.0'], stderr=subprocess.STDOUT)
except Exception as e:
    if type(e) is subprocess.CalledProcessError and str(e.output,'utf-8').find('already exists') != -1:
        pass
    else:
        shutil.rmtree(dest)
        raise(e)

#create the cluster issuer
try:
    subprocess.check_call(['kubectl', 'apply', '-f', 'cluster-issuer/letsencrypt-staging.yaml'])
    subprocess.check_call(['kubectl', 'apply', '-f', 'cluster-issuer/letsencrypt-prod.yaml'])
except Exception as e:
    shutil.rmtree(dest)
    raise e


#A retained storage class for SDDs
try:
    subprocess.check_call(['kubectl', 'apply', '-f', 'storage-class/retained-storage-class.yaml'])
except:
    pass

#Timescale deployment
try:
    subprocess.check_call(['kubectl', 'delete', 'configmap', 'timescale-initialization'])
except:
    pass

try:
    subprocess.check_call(['kubectl', 'create', 'configmap', 'timescale-initialization',
                            '--from-file='+dest+'/timescale/initialize-read-user.sh'])
except Exception as e:
    shutil.rmtree(dest)
    raise e

subprocess.check_call(['kubectl', 'apply', '-f', dest+'/timescale/timescale-config.yaml'])
subprocess.check_call(['kubectl','apply','-f', dest+'/timescale/timescale-deployment.yaml'])

#Influx deployment
subprocess.check_call(['kubectl','apply','-f', dest+'/influx/influx-config.yaml'])
subprocess.check_call(['kubectl','apply','-f', dest+'/influx/influx-deployment.yaml'])

#Grafana deployment
try:
    subprocess.check_call(['kubectl', 'delete', 'configmap', 'grafana-provisioning'])
except:
    pass

try:
    subprocess.check_call(['kubectl', 'delete', 'configmap', 'grafana-dashboards'])
except:
    pass

try:
    subprocess.check_call(['kubectl', 'create', 'configmap', 'grafana-provisioning',
                            '--from-file='+dest+'/grafana/datasource.yaml',
                            '--from-file='+dest+'/grafana/dashboard.yaml'])
    subprocess.check_call(['kubectl', 'create', 'configmap', 'grafana-dashboards',
                            '--from-file='+dest+'/grafana/Powerwatch_General.json'])
except Exception as e:
    shutil.rmtree(dest)
    raise e

subprocess.check_call(['kubectl','apply','-f', dest+'/grafana/grafana-config.yaml'])
subprocess.check_call(['kubectl','apply','-f', dest+'/grafana/grafana-deployment.yaml'])
#TODO: gcloud compute backend-services update $BACKEND --global --timeout=3600
# the default timeout is 30 which doesn't seem to work for grafana

#Powerwatch poster deployment
try:
    subprocess.check_call(['kubectl', 'delete', 'configmap', 'config'])
except:
    pass

try:
    subprocess.check_call(['kubectl', 'create', 'configmap', 'config',
                            '--from-file='+dest+'/powerwatch-data-poster/config.json',
                            '--from-file='+dest+'/powerwatch-data-poster/influx.json',
                            '--from-file='+dest+'/powerwatch-data-poster/postgres.json'])
except Exception as e:
    shutil.rmtree(dest)
    raise e

subprocess.check_call(['kubectl','apply','-f', dest+'/powerwatch-data-poster/particle-auth-token.yaml'])
subprocess.check_call(['kubectl','apply','-f', dest+'/powerwatch-data-poster/influx-user-pass.yaml'])
subprocess.check_call(['kubectl','apply','-f', dest+'/powerwatch-data-poster/postgres-user-pass.yaml'])
subprocess.check_call(['kubectl','apply','-f', dest+'/powerwatch-data-poster/powerwatch-data-poster-deployment.yaml'])

#issue the grafana certificate
subprocess.check_call(['kubectl','apply','-f', dest+'/certificate/grafana-certificate.yaml'])

#start the disk backup job
subprocess.check_call(['kubectl','apply','-f', 'cluster-rolebinding/pvc-rolebinding.yaml'])
subprocess.check_call(['kubectl','apply','-f', 'pvc-snapshot/pvc-snapshot.yaml'])

#Powerwatch Visualization
#subprocess.check_call(['kubectl','apply','-f', dest+'/powerwatch-visualization/powerwatch-visualization-deployment.yaml'])

print()
print('Generated usernames and passwords (save to lastpass)')
#Print the information generated above
print('Timescale User: ' + str(base64.b64decode(timescale_user),'utf-8'))
print('Timescale Password: ' + str(base64.b64decode(timescale_password),'utf-8'))
print('Influx User: ' + str(base64.b64decode(influx_user),'utf-8'))
print('Influx Password: ' + str(base64.b64decode(influx_password),'utf-8'))
print('Influx Admin User: ' + str(base64.b64decode(influx_admin_user),'utf-8'))
print('Influx Admin Password: ' + str(base64.b64decode(influx_admin_password),'utf-8'))
print('Grafana Database User: ' + grafana_database_user_clear)
print('Grafana Database Password: ' + grafana_database_password_clear)
print('Grafana Admin User: admin')
print('Grafana Admin Password: ' + str(base64.b64decode(grafana_admin_password),'utf-8'))
print('Particle access token: ' + str(base64.b64decode(access_token),'utf-8'))
print('Grafana globally accessible IP address: ' + str(grafana_ip_address))
print('Timescale globally accessible IP address: ' + str(timescale_ip_address))
print('UDP globally accessible IP address: ' + str(udp_ip_address))
print('Visualization globally accessible IP address: ' + str(visualization_ip_address))

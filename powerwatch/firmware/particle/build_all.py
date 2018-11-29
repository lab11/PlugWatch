#!/usr/bin/env python3
import sys
import yaml
import subprocess

with open("build_settings.yaml", 'r') as stream:
    data = yaml.load(stream)

    #now for each build in the build list
    for build in data['build']:
        #write the product_id header file with the two defines
        header = open(build['folder'] + '/src/product_id.h', 'w')
        header.write("#define PRODUCT " + str(build['product_id']) + '\n')
        header.write('#define DEPLOYMENT "' + build['name'] + '"\n')
        header.close()

        #build the firmware
        subprocess.call(['particle',
                        'compile',
                        'electron',
                        build['folder'],
                        '--saveTo',
                        'build/'+build['name']+'_'+str(build['product_id'])+'.bin'])

        #erase that header file
        header = open(build['folder'] + '/src/product_id.h', 'w')
        header.close()

#!/usr/bin/env node

var powerwatch_parser = require('lab11-powerwatch-parser');
var timescale_insert = require('timescale-insert');
var command = require('commander');
var express = require('express');
var request = require('request');
var async = require('async');
var app = express();
app.use(express.json())

command.option('-c, --config [config]', 'Particle configuration file.')
        .option('-d, --database [database]', 'Database configuration file.')
        .option('-w, --webhook  [webhook]', 'webhook configuration file')
        .option('-a, --auth [auth]', 'Particle auth token')
        .option('-u, --username [username]', 'Database username file')
        .option('-b, --webhookpass [webhookpass]', 'webhook password file')
        .option('-p, --password [password]', 'Database password file').parse(process.argv);

var particle_config = null;
if(typeof command.config !== 'undefined') {
    particle_config = require(command.config);
    particle_config.authToken = fs.readFileSync(command.auth,'utf8').trim()
} else {
    particle_config = require('./particle-config.json');
}

var webhook_config = null;
if(typeof command.webhook !== 'undefined') {
    webhook_config = require(command.webhook);
    webhook_config.passowrd = fs.readFileSync(command.webhookpass,'utf8').trim()
} else {
    webhook_config = require('./webhook-config.json');
}

var timescale_config = null;
if(typeof command.database !== 'undefined') {
    timescale_config = require(command.database);
    timescale_config.username = fs.readFileSync(command.username,'utf8').trim()
    timescale_config.password = fs.readFileSync(command.password,'utf8').trim()
} else {
    timescale_config = require('./postgres-config.json');
}

var timescale_options = {
    host: timescale_config.host,
    port: timescale_config.port,
    database: timescale_config.database,
    user: timescale_config.username,
    password: timescale_config.password,
};

var insert = new timescale_insert(timescale_options);

function parseHexString(str) {
    var result = [];
    while (str.length >= 2) {
        result.push(parseInt(str.substring(0, 2), 16));

        str = str.substring(2, str.length);
    }

    return result;
}

function post_error(event) {

    var r = powerwatch_parser.parse_error(event);
    var fields = r[0];
    var tags = r[1];
    var timestamp = r[2];

    if(fields && tags && timestamp) {

        //put it all into one json blob
        for(var key in tags) {
            fields[key] = tags[key];
        }

        // we need an iso string timestamp
        var tstring = new Date(timestamp).toISOString();

        // Only publish if there is some data
        console.log("Attempting to push to timescale");

        insert.insertOne('powerwatch_error', tstring, fields, function(err) {
            if(err) {
                console.log(err);
            }
        });
    }
}

function post_event(event) {

    var r = powerwatch_parser.parse_packet(event);
    var fields = r[0];
    var tags = r[1];
    var timestamp = r[2];

    if(fields && tags && timestamp) {

        //put it all into one json blob
        for(var key in tags) {
            fields[key] = tags[key];
        }

        // we need an iso string timestamp
        var tstring = new Date(timestamp).toISOString();

        // Only publish if there is some data
        console.log("Attempting to push to timescale");
        //is there a table that exists for this device?
        //console.log("Checking for table!");
        insert.insertOne('powerwatch', tstring, fields, function(err) {
            if(err) {
                console.log(err);
            } else {
                console.log('Post successful');
            }
        });
    }
}

function delete_old_webhooks(event_name, products, url, access_token, callback) {
    async.forEachLimit(products, 1, function(product, callback) {
        console.log('Deleting integrations for product', product, 'with event', event_name);

        //list the integrations by product
        var  uri = 'https://api.particle.io/v1/products/'+product.toString()+'/integrations?access_token='+access_token;
        request(uri, function(err, response, body) {
            async.forEachLimit(JSON.parse(body), 1, function(integration, callback) {
                console.log(integration);
                //is it the same as what I am trying to do
                if(integration.event == event_name && integration.url == url + '/' + product.toString()) {
                    console.log('Found overlapping integration for product', product, 'with id', integration.id);
                    console.log('Deleting');
                    //delete that integration
                    var  uri = 'https://api.particle.io/v1/products/'+product.toString()+'/integrations/'+ integration.id +
                                '?access_token='+access_token;

                    request.delete(uri, function(err, response, body) {
                        if(!err) {
                            console.log('Delete successful');
                        } else {
                            console.log('Delete error');
                            console.log(err);
                        }
                        callback(err);
                    });

                } else {
                    callback();
                }
            }, function(err) {
                callback(err);
            });
        });

    }, function(err) {
        if(err) {
            console.log(err);
        }
        callback(err);
    });
}

function setup_webhooks(event_name, products, url, access_token, callback) {

    delete_old_webhooks(event_name, products, url, access_token, function(err) {
        if(err) {
            return callback(err);
        }

        async.forEachLimit(products, 1, function(value, callback) {
            //setup the webhook to this address with particle
            var form = {
                'integration_type': 'Webhook',
                'event': event_name,
                'json': { "event": "{{{PARTICLE_EVENT_NAME}}}",
                          "data": "{{{PARTICLE_EVENT_VALUE}}}",
                          "coreid": "{{{PARTICLE_DEVICE_ID}}}",
                          "published_at": "{{{PARTICLE_PUBLISHED_AT}}}",
                          "userid": "{{{PRODUCT_USER_ID}}}",
                          "version": "{{{PRODUCT_VERSION}}}",
                          "public": "{{{PARTICLE_EVENT_PUBLIC}}}",
                          "password": webhook_config.password },
                'noDefaults': false,
                'url': url+'/'+value.toString(),
            };

            var  uri = 'https://api.particle.io/v1/products/'+value.toString()+'/integrations?access_token='+access_token;

            request.post(uri, {'form':form}, function(err, response, body) {
                if(err) {
                    console.log(err);
                } else {
                    console.log('Setup webhook');
                }
                callback(err);
            });
        }, function(err) {
            if(err) {
                console.log(err);
            }
            callback(err);
        });
    });
}

//okay we need to get data and errors
//have one listener for data
setup_webhooks('g', particle_config.product_ids, webhook_config.url + '/data', particle_config.authToken, function(err) {
    if(err) {
        console.log('Error creating webhook');
        console.log(err);
    } else {
        console.log('Success creating webhooks');
    }
});

app.post('/data*', (req, res) => {
    console.log(req.body);
    event = req.body;

    if(typeof event['password'] == 'undefined' || event['password'] != webhook_config.password) {
        res.send(401, "No authorized");
    }

    //extract the end of the path
    console.log(req.path);
    var product = req.path.split('/')[2];
    event['productID'] = product;


    post_event(event);
    res.send('OK');
});

setup_webhooks('!', particle_config.product_ids, webhook_config.url + '/error', particle_config.authToken, function(err) {
    if(err) {
        console.log('Error creating webhook');
        console.log(err);
    } else {
        console.log('Success creating webhooks');
    }
});

//have one listener for errors
app.post('/errors*', (req, res) => {
    console.log(req.body);
    event = req.body;

    if(typeof event['password'] == 'undefined' || event['password'] != webhook_config.password) {
        res.send(401, "No authorized");
    }

    //extract the end of the path
    console.log(req.path);
    var product = req.path.split('/')[2];
    event['productID'] = product;

    post_error(req.body);
    res.send('OK');
});

app.listen(5000, () => console.log('Listening on port 80'));

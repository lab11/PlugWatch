#!/usr/bin/env node


var Particle = require('particle-api-js');
var powerwatch_parser = require('lab11-powerwatch-parser');
var particle = new Particle();
var dgram = require('dgram');
var fs = require('fs')
var server = dgram.createSocket({type: 'udp4', reuseAddr: true}).bind(5001);

var command = require('commander');

command.option('-c --config [config]', 'Particle configuration file.')
        .option('-d, --database [database]', 'Database configuration file.')
        .option('-a, --auth [auth]', 'Particle auth token')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file').parse(process.argv);

var particle_config = null; 
if(typeof command.config !== 'undefined') {
    particle_config = require(command.config);
    particle_config.authToken = fs.readFileSync(command.auth,'utf8').trim()
} else {
    particle_config = require('./particle-config.json'); 
}

var influx_config = null; 
if(typeof command.database !== 'undefined') {
    influx_config = require(command.database);
    influx_config.username = fs.readFileSync(command.username,'utf8').trim()
    influx_config.password = fs.readFileSync(command.password,'utf8').trim()
} else {
    influx_config = require('./influxdb-config.json'); 
}

var INFLUX_LINE_LIMIT = 200000;
var INFLUX_TIME_LIMIT = 15*1000;

var InfluxPoster = require('influx-poster');
var influx_poster = new InfluxPoster({
    host: influx_config.host,
    database: influx_config.database,
    port: influx_config.port,
    protocol: influx_config.protocol,
    username: influx_config.username,
    password: influx_config.password,
}, INFLUX_LINE_LIMIT, INFLUX_TIME_LIMIT);

function post_error(event) {
    
    var r = powerwatch_parser.parse_error(event);
    var fields = r[0];
    var tags = r[1];
    var timestamp = r[2];
    
    if(fields && tags && timestamp) {
        for( var key in fields) {
            var point = [
                key,
                tags,
                {value: fields[key]},
                timestamp
            ];

            influx_poster.write_data(point);
        }
    } else {
        console.log('Parsing error');
    }
}

function post_event(event) {

    var r = powerwatch_parser.parse_packet(event);
    var fields = r[0];
    var tags = r[1];
    var timestamp = r[2];

    if(fields && tags && timestamp) {
    
        // Try posting all of this to influx
        for(var key in fields) {
            if(Array.isArray(fields[key])) {
                for(var i = 0; i < fields[key].length; i++) {
                    var point = [
                        key,
                        tags,
                        {value: fields[key][i]},
                        timestamp+i
                    ];

                    influx_poster.write_data(point);
                }
            } else {

                var point = [
                    key,
                    tags,
                    {value: fields[key]},
                    timestamp
                ];

                influx_poster.write_data(point);
            }
        }
    } else {
        console.log('Parsing error');
    }
}

var global_data_streams = [];

function restart_data_streams() {

    console.log('Restarting data stream');

    // If we have a stream going, kill it
    for (var i = 0; i < global_data_streams.length; i++) {
        if(global_data_streams[i]) {
            global_data_streams[i].abort();
        }
    }

    // Pop all of those streams off of the list
    for (var i = 0; i < global_data_streams.length; i++) {
        global_data_streams.pop();
    }

    // Get the particle event stream
    for (var j = 0; j < particle_config.product_ids.length; j++) {
        var eventStream = particle.getEventStream({ product:particle_config.product_ids[j], auth:particle_config.authToken, name:'g' });
        eventStream.catch(function(err) {
            console.log("Error in event stream");
            console.log(err);
        });
        
        eventStream.then(
            function(stream) {
                console.log('Setting data stream');
                global_data_streams.push(stream);
                stream.on('event', function(event) {
                    try {
                        post_event(event);
                    } catch(error) {
                        console.log('Event handling error: ' + error)
                    }
                });
                stream.on('error', function(event) {
                    console.log('Stream had error:');
                    console.log(event);
                });
            }, function(err) {
                console.log("Failed to getEventStream: ", err);
            }
        );
    }
}

restart_data_streams();
setInterval(restart_data_streams, 600000);

var global_error_streams = [];

function restart_error_streams() {

    console.log('Restarting error stream');

    for (var i = 0; i < global_error_streams.length; i++) {
        if(global_error_streams[i]) {
            global_error_streams[i].abort();
        }
    }

    // Pop all of those streams off of the list
    for (var i = 0; i < global_error_streams.length; i++) {
        global_error_streams.pop();
    }

    // Get the particle event stream
    for (var j = 0; j < particle_config.product_ids.length; j++) {
        var eventStream = particle.getEventStream({ product:particle_config.product_ids[j], auth:particle_config.authToken, name:'!' });
        eventStream.catch(function(err) {
            console.log("Error in event stream");
            console.log(err);
        });
        
        eventStream.then(
            function(stream) {
                console.log('Setting error stream');
                global_error_streams.push(stream);

                stream.on('event', function(event) {
                    post_error(event);
                });
                stream.on('error', function(event) {
                    console.log('Stream had error:');
                    console.log(event);
                });
            }, function(err) {
                console.log("Failed to getEventStream: ", err);
            }
        );
    }
}

restart_error_streams();
setInterval(restart_error_streams, 600000);

var global_spark_streams = [];

function restart_spark_streams() {

    console.log('Restarting spark stream');

    for (var i = 0; i < global_spark_streams.length; i++) {
        if(global_spark_streams[i]) {
            global_spark_streams[i].abort();
        }
    }

    // Pop all of thosestreams off of the list
    for (var i = 0; i < global_spark_streams.length; i++) {
        global_spark_streams.pop();
    }

    // Get the particle event stream
    for (var j = 0; j < particle_config.product_ids.length; j++) {
        var eventStream = particle.getEventStream({ product:particle_config.product_ids[j], auth:particle_config.authToken});
        eventStream.catch(function(err) {
            console.log("Error in event stream");
            console.log(err);
        });

        eventStream.then(
            function(stream) {
                console.log('Setting spark stream');
                global_spark_streams.push(stream);

                stream.on('event', function(event) {
                    if(event.name.includes('spark')) {
                        console.log(event);
                    }
                });
                stream.on('error', function(event) {
                    console.log('Stream had error:');
                    console.log(event);
                });
            }, function(err) {
                console.log("Failed to getEventStream: ", err);
            }
        );
    }
}

restart_spark_streams();
setInterval(restart_spark_streams, 600000);


//This is the udp section of the listener
server.on('error', function(error) {
    console.log('UDP error:');
    console.log(error);
});

server.on('listening', function() {
    console.log('UDP listening on ' + server.address().address + ':' + server.address().port);
});

server.on('message', function(msg, rinfo) {
    console.log(msg);
    console.log(rinfo);

    //Turn msg into json
    try {
        msgString = msg.toString('utf8');
        evt = JSON.parse(msgString);
        console.log(evt);
    } catch(e) {
        console.log('Error converting to JSON');
        console.log(e)
    }

    try {
        post_event(evt);
    } catch(e) {
        console.log('UDP handling error: ' + e)
    }
});



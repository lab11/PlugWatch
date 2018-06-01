#!/usr/bin/env node

var particle_config = require('./particle-config.json'); 
var influx_config = require('./influxdb-config.json'); 

var Particle = require('particle-api-js');
var powerwatch_parser = require('../powerwatch-parser');
var particle = new Particle();
var dgram = require('dgram');
var server = dgram.createSocket({type: 'udp4', reuseAddr: true}).bind(5000);

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

var global_data_stream = null;

function restart_data_stream() {

    console.log('Restarting data stream');

    // If we have a stream going, kill it
    if(global_data_stream) {
        global_data_stream.abort();
    }

    // Get the particle event stream
    var eventStream = particle.getEventStream({ product:particle_config.product_id, auth:particle_config.authToken, name:'g' })
    eventStream.catch(function(err) {
        console.log("Error in event stream");
        console.log(err);
    });
    
    eventStream.then(
        function(stream) {
            console.log('Setting data stream');
            global_data_stream = stream;
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

restart_data_stream();
setInterval(restart_data_stream, 600000);


var global_error_stream = null;

function restart_error_stream() {

    console.log('Restarting error stream');

    if(global_error_stream) {
        global_error_stream.abort();
    }

    // Get the particle event stream
    var eventStream = particle.getEventStream({ product:particle_config.product_id, auth:particle_config.authToken, name:'!' })
    eventStream.catch(function(err) {
        console.log("Error in event stream");
        console.log(err);
    });
    
    eventStream.then(
        function(stream) {
            console.log('Setting error stream');
            global_error_stream = stream;

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

restart_error_stream();
setInterval(restart_error_stream, 600000);

var global_spark_stream = null;

function restart_spark_stream() {

    console.log('Restarting spark stream');

    if(global_spark_stream) {
        global_spark_stream.abort();
    }

    // Get the particle event stream
    var eventStream = particle.getEventStream({ product:particle_config.product_id, auth:particle_config.authToken});
    eventStream.catch(function(err) {
        console.log("Error in event stream");
        console.log(err);
    });

    eventStream.then(
        function(stream) {
            console.log('Setting spark stream');
            global_spark_stream = stream;

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

restart_spark_stream();
setInterval(restart_spark_stream, 600000);


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



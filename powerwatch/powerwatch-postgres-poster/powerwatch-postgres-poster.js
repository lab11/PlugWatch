#!/usr/bin/env node

var particle_config = require('./particle-config.json'); 
var timescale_config = require('./postgres-config.json'); 

const { Pool }  = require('pg');
var format      = require('pg-format');
var Particle = require('particle-api-js');
var powerwatch_parser = require('../powerwatch-parser');
var particle = new Particle();

const pg_pool = new  Pool( {
    user: timescale_config.username,
    host: timescale_config.host,
    database: timescale_config.database,
    password: timescale_config.password,
    port: timescale_config.port,
    max: 20,
})

console.log("Using timescale at " + timescale_config.host +
        ":" + timescale_config.port + "  db=" + timescale_config.database);

function get_type(meas) {
    switch(typeof meas) {
        case "string":
            return 'TEXT';
        break;
        case "boolean":
            return 'BOOLEAN';
        break;
        case "number":
            return 'DOUBLE PRECISION';
        break;
        default:
        if(Array.isArray(meas)) {
            if(meas.length > 0) {
                switch(typeof meas[0]) {
                    case "string":
                        return 'TEXT[]';
                    break;
                    case "boolean":
                        return 'BOOLEAN[]';
                    break;
                    case "number":
                        return 'DOUBLE PRECISION[]';
                    break;
                    default:
                        return 'err';
                    break;
                } 
            } else {
                return 'err';
            }   
        } else {
            return 'err';
        }
    }
}

function insert_data(device, timestamp, table_obj) {
    //console.log("Insterting the data now!");

    var cols = "";
    var vals = "";
    var i = 2;
    for (var key in table_obj) {
        cols = cols + ", %I";
        vals = vals + ", $" + i.toString();
        i = i + 1
    }

    var names = [];
    var values = [];
    names.push(device);
    values.push(timestamp);
    for (var key in table_obj) {
        names.push(key);
        var meas = table_obj[key];
        values.push(meas);
    }

    var qstring = format.withArray("INSERT INTO %I (TIME" + cols + ") VALUES ($1" + vals + ")",names);
    console.log(qstring); 
    pg_pool.query(qstring, values, (err, res) => {
        if(err) {
            console.log(err)
            //was this error due to adding a field?
            if(err.code == 42703) {
                console.log('Attempting to alter the table!');
                //we can pull the erroneous column out of the err code
                var column_name = err.toString().split("\"")[1];
                var params = [];

                var type = get_type(table_obj[column_name]);
                if(type != 'err') {
                    params.push(device);
                    params.push(column_name);
                    params.push(type);
                } else {
                    console.log('Error with field ' + key);
                    console.log('Table alteration failed');
                    return;
                }

                //then add the column to the table
                console.log(params);
                var astring = format.withArray("ALTER TABLE %I ADD COLUMN %I %s",params);
                console.log(astring);
                pg_pool.query(astring, (err, res) => {
                    if(err) {
                        console.log("Failed to alter table");
                    } else {
                        console.log("Table alteration succeeded - attempting to insert again");
                        insert_data(device, timestamp, table_obj);
                    }
                });
            }
        } else {
            console.log('posted successfully!');
        }
    });
}

function create_table(device, timestamp, table_obj) {
    //how many rows is the table
    var cols = "";
    for (var key in table_obj) {
        cols = cols + ", %I %s";
    }

    //I think this can be done better with postgres internal data converter!!
    var names = [];
    names.push(device);
    for (var key in table_obj) {
        var meas = table_obj[key]
        var type = get_type(meas);
        if(type != 'err') {
            names.push(key);
            names.push(type);
        } else {
            console.log('Error with field ' + key);
        }
    }
     
    console.log("Trying to create table!");
    //These are dynamic queries!!!
    //Which means the are prone to sql injection attacks
    //Postgres supports 'format' execution statements to prevent against this
    //but I can't get that to work, so I'm going to run it client-side
    //Therefore we are as safe as the node-pg-format library
    var qstring = format.withArray('CREATE TABLE %I (TIME TIMESTAMPTZ NOT NULL' + cols + ')',names);
    console.log(qstring);
    pg_pool.query(qstring, [], (err, res) => {
        if(err) {
            console.log(err)
        } else {
            //make it a hyptertable!
            pg_pool.query("SELECT create_hypertable($1,'time')",[device], (err, res) => {
                if(err) {
                    console.log(err)
                } else {
                    console.log("Created successfully!");
                    insert_data(device, timestamp, table_obj);
                }
            });
        }
    });
}

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
        //is there a table that exists for this device?
        //console.log("Checking for table!");
        pg_pool.query("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = $1)",['powerwatch_error'], (err, res) => {
            if (err) {
                console.log(err);
            } else {
                //console.log(res.rows[0].exists);
                if(res.rows[0].exists == false) {
                    //create one
                    //console.log('Calling create table');
                    create_table('powerwatch_error', tstring, fields);
                } else {
                    //it exists- post the data
                    insert_data('powerwatch_error', tstring, fields);
                }
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
        pg_pool.query("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = $1)",['powerwatch'], (err, res) => {
            if (err) {
                console.log(err);
            } else {
                //console.log(res.rows[0].exists);
                if(res.rows[0].exists == false) {
                    //create one
                    //console.log('Calling create table');
                    create_table('powerwatch', tstring, fields);
                } else {
                    //it exists- post the data
                    insert_data('powerwatch', tstring, fields);
                }
            }
        });
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
    particle.getEventStream({ product:particle_config.product_id, auth:particle_config.authToken, name:'g' }).then(
    
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
    particle.getEventStream({ product:particle_config.product_id, auth:particle_config.authToken, name:'!' }).then(
    
        function(stream) {
            console.log('Setting error stream');
            global_error_stream = stream;

            stream.on('event', function(event) {
                post_error(event);
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
    particle.getEventStream({ product:particle_config.product_id, auth:particle_config.authToken}).then(
    
        function(stream) {
            console.log('Setting spark stream');
            global_spark_stream = stream;

            stream.on('event', function(event) {
                if(event.name.includes('spark')) {
                    console.log(event);
                }
            });
        }, function(err) {
            console.log("Failed to getEventStream: ", err);
        }
    );
}

restart_spark_stream();
setInterval(restart_spark_stream, 600000);

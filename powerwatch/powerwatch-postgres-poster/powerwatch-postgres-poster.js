#!/usr/bin/env node

var particle_config = require('./particle-config.json'); 
var timescale_config = require('./postgres-config.json'); 

const { Pool }  = require('pg');
var format      = require('pg-format');
var Particle = require('particle-api-js');
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
    console.log(event);
    if(event.version && event.data) {
        if(parseInt(event.version) >= 14) {
            var fields = {};

            fields['firmware_version'] = parseInt(event.version);
            fields['core_id'] = event.coreid;
            fields['product_id'] = event.productID;

            var timestamp = new Date(event.published_at).toISOString();
            fields['error_string'] = event.data;

            if(event.data.includes("Reset after")) {
                chunks = event.data.split(' ');
                fields['error_state'] = parseInt(chunks[chunks.length -1]);
                fields['logging_error'] = false;
                fields['hanging_error'] = true;
            }

            if(event.data.includes("Data logging") || event.data.includes("Event logging")) {
                fields['logging_error'] = true;
                fields['hanging_error'] = false;
            }

            console.log(fields);
            console.log(timestamp);

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
                        create_table('powerwatch_error', timestamp, fields);
                    } else {
                        //it exists- post the data
                        insert_data('powerwatch_error', timestamp, fields);
                    }
                }
            });
        }
    }
}

function post_event(event) {
    console.log(event);
    if(event.version && event.data) {
        if(parseInt(event.version) >= 14) {
            var major_field_list = event.data.split(";");
            var fields = {};
            
            fields['firmware_version'] = event.version;
            fields['core_id'] = event.coreid;
            fields['product_id'] = event.productID;

            // Time
            if(parseInt(event.version) < 20) {
                var timestamp = new Date(major_field_list[0]).toISOString();
            } else {
                var timestamp = new Date(major_field_list[0].split('|')[0]).toISOString();
                fields['millis'] = parseInt(major_field_list[0].split('|')[1]);
            }
            
            // Charge State
            var charge_fields = major_field_list[1].split('|');
            fields['state_of_charge'] = parseInt(charge_fields[0]);
            fields['cell_voltage'] = parseFloat(charge_fields[1]);

            if(charge_fields[2] == "0") {
                fields['is_charging'] = false;
            } else if(charge_fields[2] == "1") {
                fields['is_charging'] = true;
            }

            if(charge_fields[3] == "0") {
                fields['is_powered'] = false;
            } else if(charge_fields[3] == "1") {
                fields['is_powered'] = true;
            }

            if(parseInt(event.version) > 20) {
                fields['last_unplug_millis'] = parseInt(charge_fields[4]);
                fields['last_plug_millis'] = parseInt(charge_fields[5]);
            }

            // MPU
            var mpu_fields = major_field_list[2].split('|');
            if(mpu_fields[0] == "0") {
                fields['was_moved'] = false;
            } else if(mpu_fields[0] == "1") {
                fields['was_moved'] = true;
            }

            fields['die_temperature'] = parseFloat(mpu_fields[1]);
            
            // WIFI
            if(major_field_list[3] == '!') {
                fields['wifi_error'] = true; 
            } else {
                fields['wifi_error'] = false; 
                var wifi_fields = major_field_list[3].split('|');
                var num_networks = parseInt(wifi_fields[0]);
                fields['num_wifi_networks'] = num_networks;

                fields['wifi_networks'] = [];

                for(var i = 0; i < num_networks; i++) {
                    fields['wifi_networks'][i] = wifi_fields[i+1];
                }
            }


            // Cell Status
            var cell_fields = major_field_list[4].split('|');
            fields['particle_firmware_revision'] = cell_fields[0];
            fields['particle_firmware_number'] = cell_fields[1];
            fields['cellular_imei'] = cell_fields[2];
            fields['sim_iccid'] = cell_fields[3];
            fields['free_memory'] = parseInt(cell_fields[4]);
            fields['cellular_rssi'] = parseInt(cell_fields[5]);
            fields['cellular_quality'] = parseInt(cell_fields[6]);

            var bands = cell_fields[7];
            if(bands === 'No Bands Avail') {
                fields['num_cellular_bands'] = 0;
            } else {
                band_nums = bands.split(',');
                fields['num_cellular_bands'] = band_nums.length;
                fields['cellular_bands'] = [];
                for(var i = 0; i < band_nums.length; i++) {
                    fields['cellular_bands'][i] = parseInt(band_nums[i]);
                }
            }

            // SD
            if(major_field_list[5] === '0') {
                fields['sd_present'] = false;
            } else if(major_field_list[5] === '1') {
                fields['sd_present'] = true;
            }

            // Light
            fields['light_lux'] = parseInt(major_field_list[6]);
            
            // BLE
            var adv_string = major_field_list[7];
            fields['wit_adv'] = adv_string;
            if(adv_string == '!') {
                fields['wit_error'] = true; 
            } else {
                fields['wit_error'] = false; 
                var adv_array = parseHexString(adv_string);

                if(adv_array[0] == 0xFF) {
                    fields['wit_present'] = true;
                    fields['wit_status'] = adv_array[1];
                    fields['wit_power_factor'] = adv_array[2]/100.0;
                    var current_decimal_point_value = ((adv_array[3] & 0xC0) >> 6);
                    var wattage_decimal_point_value = ((adv_array[3] & 0x30) >> 4);
                    fields['wit_voltage_volts'] = (((adv_array[3] & 0x0F) << 8) + adv_array[4])/10;
                
                    var current_value = (((adv_array[5] >> 4) & 0xF)*1000 
                                            + (adv_array[5] & 0xF)*100 
                                            + ((adv_array[6] >> 4) & 0xF)*10
                                            + (adv_array[6] & 0xF));
                    fields['wit_current_amps'] = (current_value/(1000/(Math.pow(10,current_decimal_point_value))));

                    var wattage_value = (((adv_array[7] >> 4) & 0xF)*1000 
                                            + (adv_array[7] & 0xF)*100 
                                            + ((adv_array[8] >> 4) & 0xF)*10
                                            + (adv_array[8] & 0xF));
                    fields['wit_power_watts'] = (wattage_value/(1000/(Math.pow(10,wattage_decimal_point_value))));

                } else {
                    fields['wit_present'] = false;
                }
            }

            // GPS
            if(parseInt(event.version) > 20) {
                gps_subfields = major_field_list[8].split('|');
                if(gps_subfields[0] == '-1'){
                    fields['gps_fix'] = false;
                } else {
                    fields['gps_fix'] = true;
                    gps_fields = gps_subfields[0].split(',');
                    fields['gps_latitude'] = parseFloat(gps_fields[0]);
                    fields['gps_longitude'] = parseFloat(gps_fields[1]);
                }

                fields['gps_time_millis'] = parseInt(gps_subfields[1]);
                fields['gps_satellites'] = parseInt(gps_subfields[2]);
            } else {
                if(major_field_list[8] == '-1'){
                    fields['gps_fix'] = false;
                } else {
                    fields['gps_fix'] = true;
                    gps_fields = major_field_list[8].split(',');
                    fields['gps_latitude'] = parseFloat(gps_fields[0]);
                    fields['gps_longitude'] = parseFloat(gps_fields[1]);
                }
            }
            

            if(major_field_list.length > 9) {
                //These fields should go in version 18 and greater
                system_status_fields = major_field_list[9].split('|');
                fields['system_loop_count'] = parseInt(system_status_fields[0]);
                fields['shield_id'] = system_status_fields[1];

                sd_status_fields = major_field_list[10].split('|');
                fields['sd_log_count'] = parseInt(sd_status_fields[0]);
                fields['sd_log_size'] = parseInt(sd_status_fields[1]);
                if(parseInt(event.version) > 19) {
                    fields['sd_log_name'] = sd_status_fields[2];
                }
            }

            console.log(fields);
            console.log(timestamp);


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
                        create_table('powerwatch', timestamp, fields);
                    } else {
                        //it exists- post the data
                        insert_data('powerwatch', timestamp, fields);
                    }
                }
            });
        }
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

#!/usr/bin/env node

var particle_config = require('./particle-config.json'); 
var influx_config = require('./influxdb-config.json'); 

var Particle = require('particle-api-js');
var particle = new Particle();

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

function parseHexString(str) { 
    var result = [];
    while (str.length >= 2) { 
        result.push(parseInt(str.substring(0, 2), 16));

        str = str.substring(2, str.length);
    }

    return result;
}

function post_error(event) {
    if(event.version && event.data) {
        if(parseInt(event.version) >= 14) {
            var fields = {};
            var tags = {};

            tags['firmware_version'] = event.version;
            tags['core_id'] = event.coreid;
            tags['product_id'] = event.productID;

            var timestamp = new Date(event.published_at).getTime();
            fields['error_string'] = event.data;

            if(event.data.includes("Reset after")) {
                fields['error_state'] = parseInt(event.data.slice(-1));
                fields['logging_error'] = false;
                fields['hanging_error'] = true;
            }

            if(event.data.includes("Data logging") || event.data.includes("Event logging")) {
                fields['logging_error'] = true;
                fields['hanging_error'] = false;
            }

            console.log(fields);
            console.log(tags);
            console.log(timestamp);

            for( var key in fields) {
                var point = [
                    key,
                    tags,
                    {value: fields[key]},
                    timestamp
                ];

                influx_poster.write_data(point);
            }
        }
    }
}

function post_event(event) {
    if(event.version && event.data) {
        if(parseInt(event.version) >= 14) {
            var major_field_list = event.data.split(";");
            var fields = {};
            
            // Get our tagset
            var tags = {};
            tags['firmware_version'] = event.version;
            tags['core_id'] = event.coreid;
            tags['product_id'] = event.productID;
            //More tags after parsing some of the cellular data

            // Time
            var timestamp = new Date(major_field_list[0]).getTime();
            
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
            tags['particle_firmware_revision'] = cell_fields[0];
            tags['particle_firmware_number'] = cell_fields[1];
            tags['cellular_imei'] = cell_fields[2];
            tags['sim_iccid'] = cell_fields[3];
            fields['cellular_rssi'] = cell_fields[5];
            fields['cellular_quality'] = cell_fields[6];

            var bands = cell_fields[7];
            if(bands === 'No Bands Avail') {
                fields['num_cellular_bands'] = 0;
            } else {
                band_nums = bands.split(',');
                fields['num_cellular_bands'] = band_nums.length;
                fields['cellular_bands'] = [];
                for(var i = 0; i < band_nums.length; i++) {
                    fields['cellular_bands'][i] = band_nums[i];
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
                console.log(adv_array);

                if(adv_array[0] == 0xFF) {
                    fields['wit_present'] = true;
                    fields['wit_status'] = adv_array[1];
                    fields['wit_power_factor'] = adv_array[2];
                    var current_decimal_point_value = (adv_array[3] & 0xC0) >> 6;
                    var wattage_decimal_point_value = (adv_array[3] & 0xA0) >> 4;
                    fields['wit_voltage_volts'] = (((adv_array[3] & 0x0F) << 8) + adv_array[4])/10;
                    fields['wit_current_amps'] = (((adv_array[5] >> 4) & 0xF)*1000 
                                            + (adv_array[5] & 0xF)*100 
                                            + ((adv_array[6] >> 4) & 0xF)*10
                                            + (adv_array[6] & 0xF))/current_decimal_point_value;
                    fields['wit_power_watts'] = (((adv_array[7] >> 4) & 0xF)*1000 
                                            + (adv_array[7] & 0xF)*100 
                                            + ((adv_array[8] >> 4) & 0xF)*10
                                            + (adv_array[8] & 0xF))/wattage_decimal_point_value;
                } else {
                    fields['wit_present'] = false;
                }
            }

            // GPS
            if(major_field_list[8] == '-1'){
                fields['gps_fix'] = false;
            } else {
                fields['gps_fix'] = true;
                gps_fields = major_field_list[8].split(',');
                fields['gps_latitude'] = parseFlot(gps_fields[0]);
                fields['gps_longitude'] = parseFlot(gps_fields[1]);
            }
            console.log(fields);
            console.log(tags);
            console.log(timestamp);

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
        }
    }
}

// Get the particle event stream
particle.getEventStream({ product:particle_config.product_id, auth:particle_config.authToken, name:'g' }).then(

    function(stream) {
        stream.on('event', function(event) {
            post_event(event);
        });
    }, function(err) {
        console.log("Failed to getEventStream: ", err);
    }
);

// Get the particle event stream
particle.getEventStream({ product:particle_config.product_id, auth:particle_config.authToken, name:'!' }).then(

    function(stream) {
        stream.on('event', function(event) {
            post_error(event);
        });
    }, function(err) {
        console.log("Failed to getEventStream: ", err);
    }
);

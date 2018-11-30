
function parseHexString(str) { 
    var result = [];
    while (str.length >= 2) { 
        result.push(parseInt(str.substring(0, 2), 16));

        str = str.substring(2, str.length);
    }

    return result;
}

function parse_packet(event) {
    console.log(JSON.stringify(event,null,null));
    if(event.version && event.data) {
        if(parseInt(event.version) >= 14) {
            var major_field_list = event.data.split(";");
            var fields = {};
            
            // Get our tagset
            var tags = {};
            tags['firmware_version'] = parseInt(event.version);
            tags['core_id'] = event.coreid;
            tags['product_id'] = event.productID;
            //More tags after parsing some of the cellular data

            // Time
            if(parseInt(event.version) < 20) {
                var timestamp = new Date(major_field_list[0]).getTime();
            } else if(parseInt(event.version) < 24) {
                var timestamp = new Date(major_field_list[0].split('|')[0]).getTime();
                fields['millis'] = parseInt(major_field_list[0].split('|')[1]);
            } else {
                var timestamp = parseInt(major_field_list[0].split('|')[0])*1000;
                fields['millis'] = parseInt(major_field_list[0].split('|')[1]);
            }

            
            //If the time more than a year ago (i.e. no sync) assume the time is now
            if(timestamp < 1496367912000) {
                timestamp = Date.now();
                tags['time_source'] = 'cloud';
            } else {
                tags['time_source'] = 'particle';
            }
            
            // Charge State
            var charge_fields = major_field_list[1].split('|');
            fields['state_of_charge'] = parseInt(charge_fields[0]);
            fields['cell_voltage'] = parseFloat(charge_fields[1]);
            
            if(parseInt(event.version) < 24) {
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
            } else {
                var both = parseInt(charge_fields[2]);
                if(both & 0x02) {
                    fields['is_charging'] = true;
                } else {
                    fields['is_charging'] = false;
                }

                if(both & 0x01) {
                    fields['is_powered'] = true;
                } else {
                    fields['is_powered'] = false;
                }

                if(parseInt(event.version) > 20) {
                    fields['last_unplug_millis'] = parseInt(charge_fields[3]);
                    fields['last_plug_millis'] = parseInt(charge_fields[4]);
                }
            }

            if(parseInt(event.version) >= 100) {
                fields['grid_voltage'] = parseInt(charge_fields[5]);
                fields['ac_l_probe_count'] = parseInt(charge_fields[6]);
                fields['ac_n_probe_count'] = parseInt(charge_fields[7]);
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
            if(parseInt(event.version) < 24) {
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
            } else {
                if(major_field_list[3] == '!') {
                    fields['wifi_error'] = true; 
                } else {
                    fields['wifi_error'] = false; 
                    var buf = Buffer.from(major_field_list[3], 'base64');
                    var num = buf[0];
                    fields['num_wifi_networks'] = num;

                    fields['wifi_networks'] = [];
                    for(var i = 0; i < num; i++) {
                        fields['wifi_networks'][i] = buf[i+1].toString(16).toUpperCase();
                    }
                }
            }

            // Cell Status
            if(parseInt(event.version) < 24) {
                var cell_fields = major_field_list[4].split('|');
                tags['particle_firmware_revision'] = cell_fields[0];
                tags['particle_firmware_number'] = cell_fields[1];
                tags['cellular_imei'] = cell_fields[2];
                tags['sim_iccid'] = cell_fields[3];
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
            } else {
                var cell_fields = major_field_list[4].split('|');
                tags['particle_firmware_revision'] = cell_fields[0];
                tags['cellular_imei'] = '35258008' + cell_fields[1];
                fields['free_memory'] = parseInt(cell_fields[2]);
                fields['cellular_rssi'] = parseInt(cell_fields[3]);
                fields['cellular_quality'] = parseInt(cell_fields[4]);

                var bands = cell_fields[5];
                if(bands === '!') {
                    fields['num_cellular_bands'] = 0;
                } else {
                    band_nums = bands.split(',');
                    fields['num_cellular_bands'] = band_nums.length;
                    fields['cellular_bands'] = [];
                    for(var i = 0; i < band_nums.length; i++) {
                        fields['cellular_bands'][i] = parseInt(band_nums[i]);
                    }
                }
            }

            // SD
            if(major_field_list[5] === '0') {
                fields['sd_present'] = false;
            } else if(major_field_list[5] === '1') {
                fields['sd_present'] = true;
            }
            
            if(parseInt(event.version) < 100) {
                // Light
                fields['light_lux'] = parseInt(major_field_list[6]);
                
                // BLE
                if(parseInt(event.version) < 24) {
                    var adv_string = major_field_list[7];
                    fields['wit_adv'] = adv_string;
                    if(adv_string == '!') {
                        fields['wit_error'] = true; 
                        fields['wit_present'] = false;
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
                } else {
                    var string = major_field_list[7];
                    fields['wit_string'] = string;
                    if(string == '!') {
                        fields['wit_present'] = false; 
                        fields['wit_error'] = true; 
                    } else {
                        fields['wit_present'] = true; 
                        fields['wit_error'] = false; 

                        var buf = Buffer.from(string, 'base64');
                        fields['wit_voltage_volts'] = buf.readInt32LE(0)/10000.0;
                        fields['wit_current_amps'] = buf.readInt32LE(4)/10000.0;
                        fields['wit_power_watts'] = buf.readInt32LE(8)/10000.0;
                        fields['wit_frequnecy_hertz'] = buf.readInt32LE(12)/10000.0;
                    }
                }
            }

            // GPS
            if(parseInt(event.version) > 20) {
                gps_subfields = null;
                if(parseInt(event.version) >= 100) { 
                    gps_subfields = major_field_list[6].split('|');
                } else {
                    gps_subfields = major_field_list[8].split('|');
                }

                if(gps_subfields[0] == '-1'){
                    fields['gps_fix'] = false;
                } else {
                    fields['gps_fix'] = true;
                    gps_fields = gps_subfields[0].split(',');
                    fields['gps_latitude'] = parseFloat(gps_fields[0]);
                    fields['gps_longitude'] = parseFloat(gps_fields[1]);
                }

                fields['gps_time'] = parseInt(gps_subfields[1]);
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
            

            if(major_field_list.length > 9 && parseInt(event.version) < 100) {
                //These fields should go in version 18 and greater
                system_status_fields = major_field_list[9].split('|');
                fields['system_loop_count'] = parseInt(system_status_fields[0]);
                tags['shield_id'] = system_status_fields[1];

                sd_status_fields = major_field_list[10].split('|');
                fields['sd_log_count'] = parseInt(sd_status_fields[0]);
                fields['sd_log_size'] = parseInt(sd_status_fields[1]);
                if(parseInt(event.version) > 19) {
                    fields['sd_log_name'] = sd_status_fields[2];
                }
            } else if (parseInt(event.version) >= 100) {
                //These fields should go in version 18 and greater
                system_status_fields = major_field_list[7].split('|');
                fields['system_loop_count'] = parseInt(system_status_fields[0]);
                tags['shield_id'] = system_status_fields[1];

                sd_status_fields = major_field_list[8].split('|');
                fields['sd_log_count'] = parseInt(sd_status_fields[0]);
                fields['sd_log_size'] = parseInt(sd_status_fields[1]);
                fields['sd_log_name'] = sd_status_fields[2];
            }

            console.log(JSON.stringify(fields,null,null));
            console.log(JSON.stringify(tags,null,null));
            console.log(timestamp);

            return [fields, tags, timestamp];
        }
    }    
}

function parse_error(event) {
    console.log(JSON.stringify(event,null,null));
    if(event.version && event.data) {
        if(parseInt(event.version) >= 14) {
            var fields = {};
            var tags = {};

            tags['firmware_version'] = parseInt(event.version);
            tags['core_id'] = event.coreid;
            tags['product_id'] = event.productID;

            var timestamp = new Date(event.published_at).getTime();
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

            console.log(JSON.stringify(fields,null,null));
            console.log(JSON.stringify(tags,null,null));
            console.log(timestamp);

            return [fields, tags, timestamp];
        }
    }
}

module.exports = {
    parse_packet: parse_packet,
    parse_error: parse_error
}

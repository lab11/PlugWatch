#!/usr/bin/env node

const { Pool }  = require('pg');
var format = require('pg-format');
const request = require('request');
const fs = require('fs');
const { exec } = require('child_process');
const csv = require('csvtojson');
var async = require('async');
var diff = require('deep-diff');
const git  = require('isomorphic-git');
var moment = require('moment');


//get the usernames and passwords necessary for this task
var command = require('commander');
command.option('-d, --database [database]', 'Database configuration file.')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file')
        .option('-s, --survey [survey]', 'Survey configuration file')
        .option('-U, --surveyusername [surveyusername]', 'SurveyCTO username file')
        .option('-P, --surveypassword [surveypassword]', 'SurveyCTO passowrd file').parse(process.argv);

var timescale_config = null;
if(typeof command.database !== 'undefined') {
    timescale_config = require(command.database);
    timescale_config.username = fs.readFileSync(command.username,'utf8').trim();
    timescale_config.password = fs.readFileSync(command.password,'utf8').trim();
} else {
    timescale_config = require('./postgres-config.json');
}

var survey_config = {};
if(typeof command.surveyusername !== 'undefined') {
    survey_config = require(command.survey);
    survey_config.username = fs.readFileSync(command.surveyusername,'utf8').trim();
    survey_config.password = fs.readFileSync(command.surveypassword,'utf8').trim();
} else {
    survey_config = require('./survey-config.json');
}

//Initialize the postgres information
const pg_pool = new  Pool( {
    user: timescale_config.username,
    host: timescale_config.host,
    database: timescale_config.database,
    password: timescale_config.password,
    port: timescale_config.port,
    max: 2,
});

function get_type(name, meas) {
    if(name.split('_')[name.split('_').length - 1] == 'time') {
       return 'TIMESTAMPTZ';
    } else if (name.split('_')[name.split('_').length - 1] == 'times') {
       return 'TIMESTAMPTZ[]';
    } else {
        switch(typeof meas) {
            case "string":
                return 'TEXT';
            case "boolean":
                return 'BOOLEAN';
            case "number":
                return 'DOUBLE PRECISION';
            default:
                if(Array.isArray(meas)) {
                     switch(typeof meas[0]) {
                        case "string":
                            return 'TEXT[]';
                        case "boolean":
                            return 'BOOLEAN[]';
                        case "number":
                            return 'DOUBLE PRECISION[]';
                     }
                }
        }
    }

    return 'err';
}

function dropTempTableGeneric(table_name, callback) {
    pg_pool.query('DROP TABLE IF EXISTS ' + table_name + '_temp', (err, res) => {
        callback(err);
    });
}

function writeGenericTablePostgres(objects, table_name, outer_callback) {

    //If we didn't get any array move on
    if(objects.length == 0) {
        console.log("No records to write to table");
        return outer_callback();
    }

    function createTempTable(objects, table_name, callback) {
        //Find the object in the object array with the most fields that are not null
            //So that we get all the fields for creating the table
            var max = 0;
            var index = 0;
            for(var i = 0; i < objects.length; i++) {
                var count = 0;
                for(var key in objects[i]) {
                    if(objects[i][key] != null) {
                        count++;
                    }
                }

                if(count > max) {
                    max = count;
                    index = i;
                }
            }

            var cols = "";
            var names = [];
            names.push(table_name + '_temp');
            for(var key in objects[index]) {
                if(objects[index].hasOwnProperty(key)) {
                    var meas = objects[index][key];
                    var type = get_type(key, meas);
                    if(type != 'err') {
                        names.push(key);
                        names.push(type);
                        cols = cols + "%I %s,";
                    } else if (typeof meas == 'object') {
                        for(var subkey in meas) {
                            if(meas.hasOwnProperty(subkey)) {
                                var submeas = meas[subkey];
                                var type = get_type(subkey, submeas);
                                if(type != 'err') {
                                    names.push(key + '_' + subkey);
                                    names.push(type);
                                    cols = cols + "%I %s,";
                                } else {
                                    console.log('Error with field', key, 'and subkey', subkey);
                                    console.log('With value', submeas);
                                }
                            }
                        }
                    } else {
                        console.log('Error with field ' + key);
                        console.log('With value', meas);
                    }
                }
            }
            cols = cols.substring(0, cols.length-1);

            console.log("Creating new temporary table");
            var qstring = format.withArray('CREATE TABLE %I (' + cols + ')', names);
            pg_pool.query(qstring, (err, res) => {
                callback(err);
            });
    }

    function addObjects(objects, table_name, callback) {

        //Add all the respondents
        //Don't try to speed this up or else you will screw up the transaction
        //No parrallelism
        async.forEachLimit(objects, 1, function(value, callback) {
            var cols = "";
            var vals = "";
            var names = [];
            var values = [];
            var i = 1;
            names.push(table_name + '_temp');
            for (var name in value) {
                if(value.hasOwnProperty(name)) {
                    if(typeof value[name] == 'object') {
                       if(Array.isArray(value[name])) {
                           cols = cols + "%I, ";
                           names.push(name);
                           vals = vals + "$" + i.toString() + ',';
                           values.push(value[name]);
                           i = i + 1;
                       } else {
                           for(var subname in value[name]) {
                                if(value[name].hasOwnProperty(subname)) {
                                    cols = cols + "%I, ";
                                    names.push(name + '_' + subname);
                                    vals = vals + "$" + i.toString() + ',';
                                    values.push(value[name][subname]);
                                    i = i + 1;
                                }
                            }
                       }
                    } else {
                        cols = cols + "%I, ";
                        names.push(name);
                        vals = vals + "$" + i.toString() + ',';
                        values.push(value[name]);
                        i = i + 1;
                    }
                }
            }

            cols = cols.substring(0, cols.length-2);
            vals = vals.substring(0, vals.length-1);

            var qstring = format.withArray("INSERT INTO %I (" + cols + ") VALUES (" + vals + ")", names);
            pg_pool.query(qstring, values, (err, res) => {
                if(err) {
                    console.log("Error inserting into temp table");
                    console.log(err);
                    callback(err);
                } else {
                    console.log('posted successfully!');
                    callback();
                }
            });
        }, function(err) {
            callback(err);
        });
    }

    function changeTableNames(table_name, callback) {

        function renameOld(table_name, new_name, callback) {
            pg_pool.query('ALTER TABLE IF EXISTS ' + table_name + ' RENAME to ' + new_name, (err, res) => {
                callback(err);
            });
        }

        function changeSchema(new_name, callback) {
            pg_pool.query('ALTER TABLE IF EXISTS ' + new_name + ' SET SCHEMA backup', (err, res) => {
                callback(err);
            });
        }

        function correctName(table_name, callback) {
            pg_pool.query('ALTER TABLE ' + table_name + '_temp RENAME to ' + table_name, (err, res) => {
                callback(err);
            });
        }

        var new_name = table_name + '_' + Math.round((Date.now()/1000)).toString();

        async.series([async.apply(renameOld, table_name, new_name),
                      async.apply(changeSchema, new_name),
                      async.apply(correctName, table_name)],
            function(err, res) {
                callback(err);
            });
    }

    function begin(callback) {
        pg_pool.query('BEGIN', (err, res) => {
            callback(err);
        });
    }

    console.log("Starting writing asnyc");
    async.series([begin,
                  async.apply(dropTempTableGeneric, table_name),
                  async.apply(createTempTable, objects, table_name),
                  async.apply(addObjects, objects, table_name),
                  async.apply(changeTableNames, table_name)],
          function(err, res) {
              if(err) {
                  console.log(err);
                  console.log('rolling back');
                  pg_pool.query('ROLLBACK', (err) => {
                      if(err) {
                          console.log(err);
                          console.log('error rolling back');
                          outer_callback(err);
                      } else {
                          console.log('rolled back');
                          outer_callback(err);
                      }
                  });
              } else {
                  console.log('committing');
                  pg_pool.query('COMMIT', (err) => {
                      if(err) {
                          console.log(err);
                          console.log('Error committing');
                          outer_callback(err);
                      } else {
                          console.log('committed');
                          outer_callback(err);
                      }
                  });
              }
          });

}

function writeRespondentTablePostgres(respondents, outer_callback) {
    console.log();
    console.log("Writing respondents array to postgres.");
    var array = [];
    for(var key in respondents) {
        if(respondents.hasOwnProperty(key)) {
            array.push(respondents[key]);
        }
    }

    writeGenericTablePostgres(array, 'respondents', outer_callback);
}

function writeDevicesTablePostgres(devices, outer_callback) {
    console.log();
    console.log("Writing deployment array to postgres.");
    writeGenericTablePostgres(devices, 'deployment', outer_callback);
}

function writeEntryTablePostgres(entrySurveys, outer_callback) {
    console.log();
    console.log("Writing pilot errors array to postgres.");
    error_table = [];
    for(var i = 0; i < entrySurveys.length; i++) {
        var entry = {
               respondent_id: entrySurveys[i].a_respid,
               respondent_firstname: entrySurveys[i].e_firstname,
               respondent_surnname: entrySurveys[i].e_surnames,
               respondent_popularname: entrySurveys[i].e_popularname,
               phone_number: entrySurveys[i].e_phonenumber,
               error: entrySurveys[i].error,
               error_field: entrySurveys[i].error_field,
               error_comment: entrySurveys[i].error_comment,
               value_of_error_field: null,
               fo_name: entrySurveys[i].surveyor_name,
               site_id: entrySurveys[i].site_id,
               gps: entrySurveys[i].gps,
               gps_accurate: entrySurveys[i].g_gps_accurate,
               carrier: entrySurveys[i].e_carrier,
               second_phone_number: entrySurveys[i].e_otherphonenumber,
               alternate_contact_name: entrySurveys[i].e_othercontact_person_name,
               alternate_phone_number: entrySurveys[i].e_othercontact_person_number,
               survey_time: entrySurveys[i].endtime,
               survey_id: entrySurveys[i].instanceID
        };

        if(entry.gps == null) {
            entry.gps = {};
        }

        if(entry.gps_accurate == null) {
            entry.gps_accurate = {};
        }

        if(typeof entrySurveys[i][entrySurveys[i].error_field] != 'undefined') {
            entry.value_of_error_field = entrySurveys[i][entrySurveys[i].error_field];
        } else {
            entry.value_of_error_field = '';
        }

        if(typeof entrySurveys[i].error_extra != 'undefined') {
            entry.error_extra = entrySurveys[i].error_extra;
        }

        if(typeof entrySurveys[i].error_extra2 != 'undefined') {
            entry.error_extra2 = entrySurveys[i].error_extra2;
        }

        error_table.push(entry);
    }
    writeGenericTablePostgres(error_table, 'pilot_errors', outer_callback);
}

function writeBackcheckTablePostgres(entrySurveys, outer_callback) {
    console.log();
    console.log("Writing backcheck table to postgres.");
    backcheck_table = [];
    for(var i = 0; i < entrySurveys.length; i++) {
        var entry = {
               respondent_id: entrySurveys[i].a_respid,
               backcheck_group: entrySurveys[i].backcheck_group,
               respondent_firstname: entrySurveys[i].e_firstname,
               respondent_surnname: entrySurveys[i].e_surnames,
               respondent_popularname: entrySurveys[i].e_popularname,
               phone_number: entrySurveys[i].e_phonenumber,
               fo_name: entrySurveys[i].surveyor_name,
               site_id: entrySurveys[i].site_id,
               gps: entrySurveys[i].gps,
               gps_accurate: entrySurveys[i].g_gps_accurate,
               carrier: entrySurveys[i].e_carrier,
               second_phone_number: entrySurveys[i].e_otherphonenumber,
               alternate_contact_name: entrySurveys[i].e_othercontact_person_name,
               alternate_phone_number: entrySurveys[i].e_othercontact_person_number,
               survey_time: entrySurveys[i].endtime,
               survey_id: entrySurveys[i].instanceID
        };

        backcheck_table.push(entry);
    }
    writeGenericTablePostgres(backcheck_table, 'backcheck', outer_callback);
}

function writeExitTablePostgres(exitSurveys, outer_callback) {
    console.log();
    console.log("Writing change errors array to postgres.");
    error_table = [];
    for(var i = 0; i < exitSurveys.length; i++) {
         var entry = {
               respondent_id: exitSurveys[i].a_respid,
               error: exitSurveys[i].error,
               error_field: exitSurveys[i].error_field,
               error_comment: exitSurveys[i].error_comment,
               value_of_error_field: null,
               fo_name: exitSurveys[i].surveyor_name,
               gps: exitSurveys[i].gps,
               survey_time: exitSurveys[i].endtime,
               survey_id: exitSurveys[i].instanceID
        };

        if(entry.gps == null) {
            entry.gps = {};
        }

        if(typeof exitSurveys[i][exitSurveys[i].error_field] != 'undefined') {
            entry.value_of_error_field = exitSurveys[i][exitSurveys[i].error_field];
        } else {
            entry.value_of_error_field = '';
        }

        if(typeof exitSurveys[i].error_extra != 'undefined') {
            entry.error_extra = exitSurveys[i].error_extra;
        }

        if(typeof exitSurveys[i].error_extra2 != 'undefined') {
            entry.error_extra2 = exitSurveys[i].error_extra2;
        }

        error_table.push(entry);
    }
    writeGenericTablePostgres(error_table, 'change_errors', outer_callback);
}

function updateTrackingTables(respondents, devices, entrySurveys, exitSurveys, callback) {
    //Write the respondents and devices table to postgres
    async.series([async.apply(writeRespondentTablePostgres, respondents),
                  async.apply(writeDevicesTablePostgres, devices),
                  async.apply(writeEntryTablePostgres,entrySurveys),
                  async.apply(writeExitTablePostgres,exitSurveys)],
        function(err, result) {
            console.log(err);
            callback(err);
        });
}

function lookupCoreID(core_id, devices) {

    for(var i = 0; i < devices.length; i++) {
        if(devices[i].core_id == core_id.toLowerCase()) {
            return [devices[i].core_id.toLowerCase(), devices[i].shield_id.toLowerCase()];
        }
    }

    return null;
}

function lookupShieldID(shield_id, devices) {

    for(var i = 0; i < devices.length; i++) {
        if(devices[i].shield_id == shield_id.toUpperCase()) {
            return [devices[i].core_id.toLowerCase(), devices[i].shield_id.toUpperCase()];
        }
    }

    return null;
}

function getDevicesTable(callback) {
    //Query devices table
    pg_pool.query('SELECT core_id, shield_id, product_id FROM devices', (err, res) => {
        if(err) {
            console.log(err);
            return callback(null);
        } else {
            if(res.rows.length > 0) {
                return callback(res.rows);
            } else {
                return callback(null);
            }
        }
    });
}

function getAppID(survey) {

    var QRn = survey.g_appQR_nr1.toUpperCase();
    var QRbar = survey.g_appQRbar.toUpperCase();

    if(QRbar.length == 15 || QRbar.length == 16) {
        return QRbar;
    } else {
        if(QRn.length == 15 || QRn.length == 16) {
            return QRn;
        } else {
            return null;
        }
    }
}

function getGenericID(survey, qrBarField, manualField, devices) {
    //Attempt to parse the QR code
    var parsed_core_id = null;
    var parsed_shield_id = null;

    //If the qrbar field is good we should just go with that
    if(typeof survey[qrBarField] != 'undefined' && survey[qrBarField] != null) {
       let id_to_parse = survey[qrBarField].split(':');
       if(id_to_parse.length == 3) {
          parsed_core_id = id_to_parse[1];
          parsed_shield_id = id_to_parse[2];
          let ids = lookupCoreID(parsed_core_id, devices);
          if(ids != null) {
             return ids;
          }
       }
    }

    //Okay lastly try the manually entered field
    let ids = lookupShieldID(survey[manualField] + '0000', devices);

    //This could be null but it's all we could do
    return ids;
}

function getExitGiveDeviceID(survey, devices) {
    //g_deviceID_retrieve
    //deviceRetrieveQR

    return getGenericID(survey, 'g_deviceQRbar_give', 'g_deviceID_give', devices);
}

function getExitRetrieveDeviceID(survey, devices) {
    //g_deviceID_retrieve
    //deviceRetrieveQR

    return getGenericID(survey, 'g_deviceQRbar_retrieve', 'g_deviceID_retrieve', devices);
}

function getEntryDeviceID(survey, devices) {
    //g_deviceID
    //deviceQR

    return getGenericID(survey, 'g_deviceQRbar', 'g_deviceID', devices);
}

function getEntryCoordinates(survey) {
    //Which GPS numbers have been recorded
    var gps = ['g_gps_accurate','g_gps','gps'];

    var min_accuracy = null;
    var min_index = null;

    for(var i = 0; i < gps.length; i++){
        if(typeof survey[gps[i]] != 'undefined' && survey[gps[i]].Accuracy != '') {
            return [parseFloat(survey[gps[i]].Latitude),parseFloat(survey[gps[i]].Longitude)];
        }
    }

    return null;
}

var carrier_map = {};
carrier_map['1'] = 'MTN';
carrier_map['2'] = 'Airtel';
carrier_map['3'] = 'Vodaphone';
carrier_map['4'] = 'Tigo';
carrier_map['5'] = 'GLO';

function generateTrackingTables(entrySurveys, exitSurveys, device_table, callback) {
    //Sort the surveys by submission time
    //This assumption makes it easier to process the surveys
    entrySurveys.sort(function(a,b) {
       return Date.parse(a.endtime) - Date.parse(b.endtime);
    });

    exitSurveys.sort(function(a,b) {
       return Date.parse(a.endtime) - Date.parse(b.endtime);
    });

    //Okay the high level idea here is to generate a json blob describing
    //the entire deployment from the surveys. We actually want two - a respondent-centric
    //set and device-centric set
    //
    //To do that the method will be loop through entry Surveys
    //When you find a valid device deployment create entries for the respondent
    //and entries for the device - do this for all devices
    //Then loop through all exit/redeployment surveys looking for device pickups/
    //redeployments
    //Then pivot back and forth between the two until you stop making progress
    //This is the only way I can think of to ensure ordering and correctness
    //Surveys that look erroneous get flagged
    //Devices that look erroneous get flagged
    //and It all can be cleaned up by modifying the R script
    var devices = [];
    var respondents = {};
    var still_making_progress = true;
    while(still_making_progress) {
       //This will be set to true when you deploy a device or remove a device
       still_making_progress = false;

       var surveys_to_remove = [];
       for(let i = 0; i < entrySurveys.length; i++) {
           var device_info = null;
           var respondent_info = null;
           let surveySuccess = true;
           let respondent_id = entrySurveys[i].a_respid.toUpperCase();
           let old_respondent = false;

           if(parseInt(respondent_id) > 0 && parseInt(respondent_id) < 30000) {
               old_respondent = true;
           }

           console.log("Processing entry survey for respondent", respondent_id);

           //Make sure that the R script didn't report an error for this survey
           if(typeof entrySurveys[i].error != 'undefined' && (entrySurveys[i].error == 'TRUE')) {
               console.log("Cleaning script marked survey as errored. Skipping survey.");
               surveySuccess = false;
               continue;
           }

           //Make sure that we need to process this survey
           //if(entrySurveys[i].g_download == '0' && entrySurveys[i].g_install == '0') {
           //   //This survey did not result in an app download or a powerwatch install
           //   //Exiting
           //   console.log('No app install or powerwatch install. Skipping survey');
           //   surveys_to_remove.push(i);
           //   continue;
           //}

           //Is the respondent ID valid?
          if(respondent_id.length != 8 && !old_respondent) {
               //This is a duplicate
               console.log("Invalid respondent ID for", entrySurveys[i].instanceID,"Skipping suvey.");
               surveySuccess = false;
               entrySurveys[i].error = true;
               entrySurveys[i].error_field = 'a_respid';
               entrySurveys[i].error_comment = 'Invalid Respondent ID';
               continue;
          }

           //Okay, first, have we already processed an entry survey for
           //this respondent ID
           if(typeof respondents[respondent_id] != 'undefined') {
               //This is a duplicate
               console.log("Respondent duplicate of", respondents[respondent_id].pilot_survey_id,"Skipping suvey.");
               surveySuccess = false;
               entrySurveys[i].error = true;
               entrySurveys[i].error_field = 'a_respid';
               entrySurveys[i].error_comment = 'Duplicate respondent ID of ' + respondents[respondent_id].pilot_survey_id;
               continue;
           }

           //Do we already have a respondent with this phone number?
           for(var key in respondents) {
               if(respondents[key].phoneNumber == entrySurveys[i].e_phonenumber) {
                   //This is a duplicate
                   console.log("Phone number duplicate of respondent", key, "Skipping suvey.");
                   surveySuccess = false;
                   entrySurveys[i].error = true;
                   entrySurveys[i].error_field = 'e_phonenumber';
                   entrySurveys[i].error_comment = 'Duplicate phone number of respondent ' + key;
                   continue;
               }
           }

           //Okay this is a unique respondent with a unique phone number
           //Process the survey

           //Get the most accurate latitude and longitude possible
           var coords = getEntryCoordinates(entrySurveys[i]);
           if(coords == null) {
               console.log("Invalid coordinates. Skipping suvey.");
               surveySuccess = false;
               entrySurveys[i].error = true;
               entrySurveys[i].error_field = 'g_gps_accuracy';
               entrySurveys[i].error_comment = 'No valid GPS coordinates found';
               continue;
           }

           var carrier = ''
           if(typeof carrier_map[entrySurveys[i].e_carrier] != 'undefined') {
               carrier = carrier_map[entrySurveys[i].e_carrier]
           } else {
               console.log('Unkown carrier. Skipping survey');
               carrier = 'Unkown';
               surveySuccess = false;
               entrySurveys[i].error = true;
               entrySurveys[i].error_field = 'e_carrier';
               entrySurveys[i].error_comment = 'Unkown/other carrier. Respondent cannot be paid.';
               continue;
           }

           respondent_info = {
               respondent_id: respondent_id,
               respondent_firstname: entrySurveys[i].e_firstname,
               respondent_surnname: entrySurveys[i].e_surnames,
               respondent_popularname: entrySurveys[i].e_popularname,
               fo_name: entrySurveys[i].surveyor_name,
               site_id: entrySurveys[i].site_id,
               phone_number: entrySurveys[i].e_phonenumber,
               carrier: carrier,
               second_phone_number: entrySurveys[i].e_otherphonenumber,
               alternate_contact_name: entrySurveys[i].e_othercontact_person_name,
               alternate_phone_number: entrySurveys[i].e_othercontact_person_number,
               location_latitude: coords[0],
               location_longitude: coords[1],
               pilot_survey_time: entrySurveys[i].endtime,
               pilot_survey_id: entrySurveys[i].instanceID
           };

           //Did this user download the app?
           if(entrySurveys[i].g_download == '1') {
               console.log("Respondent downloaded app. Processing download information.");
               //Set the user to active
               respondent_info.currently_active = true;

               //extract the unique key presented by the app
               var appID = getAppID(entrySurveys[i]);
               if(appID == null) {
                   console.log("Could not get appID. Skipping survey.");
                   surveySuccess = false;
                   entrySurveys[i].error = true;
                   entrySurveys[i].error_field = 'g_appQR_nr';
                   entrySurveys[i].error_comment = 'App QR code not recorded correctly';
                   continue;
               } else {
                  respondent_info.app_id = appID;
               }
           } else {
               console.log("Respondent did not download the app. Set currently active to false.");
               //Set the user to active
               respondent_info.currently_active = false;
           }

           //if there is a powerwatch device collect the same information about powerwatch
           if(entrySurveys[i].g_install == '1') {
              console.log("Respondent installed powerwatch. Processing powerwatch information.");

              //Process all the IDs present in a survey and cross reference it  with the device table
              let ids = getEntryDeviceID(entrySurveys[i], device_table);
              if(ids == null) {
                  //This is an error, post the error
                  console.log("Did not get valid device ID. Skipping");
                  surveySuccess = false;
                  entrySurveys[i].error = true;
                  entrySurveys[i].error_field = 'g_deviceID';
                  entrySurveys[i].error_comment = 'Unknown or invalid device ID';
                  entrySurveys[i].error_extra = entrySurveys[i].g_deviceQR;
                  entrySurveys[i].error_extra2 = entrySurveys[i].g_deviceQRbar;
                  continue;
              } else {
                  let core_id = ids[0];
                  let shield_id = ids[1];

                  //Make sure this is is not a duplicate device
                  for(let j = 0; j < devices.length; j++) {
                     if(devices[j].core_id == core_id && devices[j].currently_deployed) {
                        //This devices is currently deployed
                        console.log("Device already deployed. Skipping");
                        surveySuccess = false;
                        entrySurveys[i].error = true;
                        entrySurveys[i].error_field = 'g_deviceID';
                        entrySurveys[i].error_comment = 'Device already deployed';
                        entrySurveys[i].error_extra = entrySurveys[i].g_deviceQR;
                        entrySurveys[i].error_extra2 = entrySurveys[i].g_deviceQRbar;
                        continue;
                     }
                  }

                  device_info = {
                      core_id: core_id,
                      shield_id: shield_id,
                      respondent_id: respondent_id,
                      respondent_firstname: entrySurveys[i].e_firstname,
                      respondent_surnname: entrySurveys[i].e_surnames,
                      respondent_popularname: entrySurveys[i].e_popularname,
                      fo_name: entrySurveys[i].surveyor_name,
                      site_id: entrySurveys[i].site_id,
                      deployment_start_time: entrySurveys[i].endtime,
                      deployment_end_time: null,
                      phone_number: entrySurveys[i].e_phonenumber,
                      second_phone_number: entrySurveys[i].e_otherphonenumber,
                      alternate_contact_name: entrySurveys[i].e_othercontact_person_name,
                      alternate_phone_number: entrySurveys[i].e_othercontact_person_number,
                      carrier: carrier,
                      currently_deployed: true,
                      location_latitude: coords[0],
                      location_longitude: coords[1],
                      installed_outage: entrySurveys[i].g_installoutage,
                      deployment_survey_time: entrySurveys[i].endtime,
                      deployment_survey_id: entrySurveys[i].instanceID,
                  };

                  //Update the respondent to say that they do have a powerwatch
                  respondent_info.powerwatch = true;
                  respondent_info.deployment_number = 1;
                  respondent_info.powerwatch_core_ids = [core_id];
                  respondent_info.powerwatch_shield_ids = [shield_id];
                  respondent_info.powerwatch_deployment_start_times = [entrySurveys[i].endtime];
              }//end device IDs are valid
           } else {//end device was installed
               //User did not get a powerwatch
               respondent_info.powerwatch = false;
           }

           //If this survey was processed successfully, remove it from
           //the set of surveys to process
           if(surveySuccess) {
               console.log("Success processing entry survey for respondent", respondent_id);
               console.log(respondent_info);

               still_making_progress = true;

               //Add the respondent to the respondent ID table
               respondents[respondent_id] = respondent_info;

               //Add this deployment to the table
               if(device_info != null) {
                   console.log(device_info);
                   devices.push(device_info);
               }

               //Add this index to the surveys to be removed
               surveys_to_remove.push(i);
           }
       } // end for loop

       //Actually remove the surveys
       for(let i = surveys_to_remove.length -1; i >= 0; i--) {
          entrySurveys.splice(surveys_to_remove[i],1);
       }

       //Now loop through the exit surveys
       surveys_to_remove = [];
       for(let i = 0; i < exitSurveys.length; i++) {
           let surveySuccess = true;
           var device_removal_info = null;
           var removed_device = null;
           var device_add_info = null;
           let respondent_id = exitSurveys[i].a_respid;
           console.log("Processing exit survey for respondent ", respondent_id);

           //Does this repondent exist?
           if(typeof respondents[respondent_id] == 'undefined') {
               surveySuccess = false;
               exitSurveys[i].error = true;
               exitSurveys[i].error_field = 'a_respid';
               exitSurveys[i].error_comment = 'Unknown respondent id';
               continue;
           }

           //Okay exit surveys should be all about retrieving and redeploying
           //devices
           // For each one we look to see if there is a device already deployed
           // for that core_id, and if so we remove it, and update the associated
           // respondent
           //
           // We also deploy a new device there if necessary


           //did we remove a device?
           if(exitSurveys[i].a_retrieve == '1') {
              console.log('Device was retrieved - processing');
              let ids = getExitRetrieveDeviceID(exitSurveys[i], device_table);
              if(ids == null) {
                  //This is an error, post the error
                  console.log("Error processing device retrieval ID");
                  surveySuccess = false;
                  exitSurveys[i].error = true;
                  exitSurveys[i].error_field = 'g_deviceID_retrieve';
                  exitSurveys[i].error_comment = 'Unknown or invalid device ID';
                  exitSurveys[i].error_extra = exitSurveys[i].g_deviceQR_retrieve;
                  exitSurveys[i].error_extra2 = exitSurveys[i].g_deviceQRbar_retrieve;
                  continue;
              } else {
                  let core_id = ids[0];

                  //Okay now look for that device in the devices table
                  for(let j = 0; j < devices.length; j++) {
                     if(devices[j].currently_deployed && devices[j].core_id == core_id) {
                        if(devices[j].respondent_id == respondent_id) {
                           //We are removing this device
                           removed_device = devices[j];
                           device_removal_info = {};
                           device_removal_info.index = j;
                           device_removal_info.removal_time = exitSurveys[i].endtime;
                        }
                     }
                  }

                  if(device_removal_info == null) {
                     //We didn't fine the device to remove, which is an error
                     console.log("Device does not exist to remove");
                     surveySuccess = false;
                     exitSurveys[i].error = true;
                     exitSurveys[i].error_field = 'g_deviceID_retrieve';
                     exitSurveys[i].error_comment = 'Reported device not currently deployed';
                     exitSurveys[i].error_extra = exitSurveys[i].g_deviceQR_retrieve;
                     exitSurveys[i].error_extra2 = exitSurveys[i].g_deviceQRbar_retrieve;
                     continue;
                  }
              }
           }

           //Are we still able to process this survey? Did we try to install a new device?
           if(exitSurveys[i].a_give == '1') {
              //Okay now we should redeploy a device if possible
              //Process all the IDs present in a survey and cross reference it  with the device table
              console.log('Providing repsondent with new device');
              var ids = getExitGiveDeviceID(exitSurveys[i], device_table);
              if(ids == null) {
                  //This is an error, post the error
                  console.log('Error with new device ID');
                  surveySuccess = false;
                  exitSurveys[i].error = true;
                  exitSurveys[i].error_field = 'g_deviceID_give';
                  exitSurveys[i].error_comment = 'Unknown or invalid device ID';
                  continue;
              } else {
                  var core_id = ids[0];
                  var shield_id = ids[1];

                  //Make sure this is is not a duplicate device
                  for(let j = 0; j < devices.length; j++) {
                     if(devices[j].core_id == core_id && devices[j].currently_deployed) {
                        console.log(devices[j]);
                        //This devices is currently deployed
                        console.log("Device already deployed, skipping");
                        surveySuccess = false;
                        exitSurveys[i].error = true;
                        exitSurveys[i].error_field = 'g_deviceID_give';
                        exitSurveys[i].error_comment = 'Device already deployed. Cannot be deployed again.';
                        exitSurveys[i].error_extra = exitSurveys[i].g_deviceQR_give;
                        exitSurveys[i].error_extra2 = exitSurveys[i].g_deviceQRbar_give;
                        continue;
                     }
                  }

                  device_add_info = {
                      core_id: core_id,
                      shield_id: shield_id,
                      respondent_id: respondent_id,
                      respondent_firstname: respondents[respondent_id].respondent_firstname,
                      respondent_surnname: respondents[respondent_id].respondent_surnname,
                      respondent_popularname: respondents[respondent_id].respondent_popularname,
                      fo_name: exitSurveys[i].surveyor_name,
                      site_id: respondents[respondent_id].site_id,
                      deployment_start_time: exitSurveys[i].endtime,
                      phone_number: respondents[respondent_id].phone_number,
                      second_phone_number: respondents[respondent_id].second_phone_number,
                      alternate_contact_name: respondents[respondent_id].alternate_contact_name,
                      alternate_phone_number: respondents[respondent_id].alternate_phone_number,
                      carrier: respondents[respondent_id].carrier,
                      currently_deployed: true,
                      location_latitude: respondents[respondent_id].location_latitude,
                      location_longitude: respondents[respondent_id].location_longitude,
                      installed_outage: exitSurveys[i].installed_outage,
                      deployment_survey_time: exitSurveys[i].endtime,
                      deployment_survey_id: exitSurveys[i].instanceID,
                  };
              }
           }

           if(surveySuccess) {
               still_making_progress = true;

               //remove the removed device
               //was a device removed
               if(exitSurveys[i].a_retrieve == '1') {
                   console.log("Updating device to be removed with following info");
                   console.log(device_removal_info);
                   devices[device_removal_info.index].currently_deployed = false;
                   devices[device_removal_info.index].deployment_end_time = device_removal_info.removal_time;
                   console.log("Updated device info:", devices[device_removal_info.index])

                   //Update the respondent
                   respondents[respondent_id].powerwatch = false;

                   if(typeof respondents[respondent_id].change_survey_ids == 'undefined') {
                      respondents[respondent_id].change_survey_ids = [];
                   }

                   if(typeof respondents[respondent_id].deployment_end_times == 'undefined') {
                      respondents[respondent_id].powerwatch_deployment_end_times = [];
                   }

                   respondents[respondent_id].change_survey_ids.push(exitSurveys[i].instanceID);
                   respondents[respondent_id].powerwatch_deployment_end_times.push(device_removal_info.removal_time);
                   console.log("Updated respondent info:", respondents[respondent_id])
               }


               //Was a device deployed
               if(device_add_info != null) {
                  console.log("Adding device with info");
                  console.log(device_add_info);
                  devices.push(device_add_info);

                  //Update the respondent to say that they do have a powerwatch
                  respondents[respondent_id].powerwatch = true;
                  respondents[respondent_id].deployment_number += 1;
                  respondents[respondent_id].powerwatch_core_ids.push(device_add_info.core_id);
                  respondents[respondent_id].powerwatch_shield_ids.push(device_add_info.shield_id);
                  respondents[respondent_id].powerwatch_deployment_start_times.push(device_add_info.deployment_start_time);

                  if(respondents[respondent_id].change_survey_ids.indexOf(exitSurveys[i].instanceID) == -1) {
                      respondents[respondent_id].change_survey_ids.push(exitSurveys[i].instanceID);
                  }
               }

               surveys_to_remove.push(i);
           }
       }

       //Actually remove the surveys
       for(let i = surveys_to_remove.length - 1; i >= 0; i--) {
          exitSurveys.splice(surveys_to_remove[i],1);
       }
   }

   //Okay we are done making progress
   //We should write out any unprocessed surveys and their reasons to postgres
   //We should also write the updated respondent and device tables to postgres
   console.log();
   console.log("Updating tracking tables after processing surveys");
   updateTrackingTables(respondents, devices, entrySurveys, exitSurveys, function(err) {
      callback(err);
   });
}

function processSurveys(entrySurveys, exitSurveys, callback) {
    //This should enter a powerwatch user into the postgres deployment table and the oink table
    getDevicesTable(function(devices) {
       //Okay we should not have completely processed entry and exit surveys
       generateTrackingTables(entrySurveys, exitSurveys, devices, function(err) {
          callback(err);
       });
    });
}

function gitAddCommitPush(file, repoPath, callback) {
    //add the file
    //remove the first directory from the filePath
    file_split = file.split('/');
    file_split.shift();
    file = file_split.join('/');

    git.add({fs, dir: repoPath, filepath: file}).then(function(result) {
        git.commit({fs, dir: repoPath, message: "Auto updating newly cleaned file",
                        author:{name: "Deployment_Management_Service", email: "adkins@berkeley.edu"}}).then(function(result) {
            exec('git -C ' + repoPath + ' push origin master', (error, stdout, stderr) => {
                if(error) {
                    console.log('Error pushing file')
                    callback(error);
                } else {
                    console.log("Pushed new", file)
                    callback();
                }
            });
        }, function(err) {
            console.log("Error committing file");
            callback(err);
        });
    }, function(err) {
        console.log("Error adding file");
        callback(err);
    });
}

//function to fetch surveys from surveyCTO
function fetchSurveys(formid, cleaning_path, repoPath, callback) {

    //fetch all surveys from the start of time - we prevent double writing anyways
    var uri = 'https://' + survey_config.host + '/api/v1/forms/data/wide/csv/' +
                                                formid +
                                                '?r=approved|rejected|pending';

    var options = {
        uri: uri,
        auth: {
            user: survey_config.username,
            pass: survey_config.password,
            sendImmediately: false
        }
    };

    request(options, function(error, response, body) {
        //Okay now we should write this out to a file and call the cleaning script
        //on it
        if(error) {
           return callback([], false, error);
        }

        if(body.length == 0) {
           //We don't have any forms submitted so just return an empty array
           return callback([], false, null);
        } else {
            //get the root of the cleaning path
            var path_parts = cleaning_path.split('/')
            path_parts.pop();
            var path = path_parts.join('/');
            path = path + '/';

            fs.writeFile(path + formid + '.csv', body, function(err) {
                if(err) {
                    console.log("Encountered file writing error, can't clean");
                    return callback(null, "File writing error for cleaning");
                } else {
                    //load the last file we cleaned
                    var last_json = null;
                    csv().fromFile(path + formid + '_cleaned.csv').then(function(json) {
                        console.log("Loaded old file for form ", formid, " for compairson.");
                        last_json = json;
                        //Clean the file using the rscript
                        exec('Rscript ' + cleaning_path + ' ' + path + formid + '.csv ' +
                                                           path + formid + '_cleaned.csv',
                                                 function(error, stdout, stderr) {

                            if(error) {
                                console.log(error, stderr);
                                return callback(null, false, "Error cleaning file with provided script");
                            } else {
                                csv().fromFile(path + formid + '_cleaned.csv').then(function(json) {
                                    //compare json to last json
                                    var differences = diff(last_json, json);
                                    var changed = (typeof differences != 'undefined');
                                    //commit cleaned file
                                    if(changed == true) {
                                        console.log("Committing cleaned file");
                                        gitAddCommitPush(path + formid + '_cleaned.csv', repoPath, function(err) {
                                            return callback(json, changed, err);
                                        });
                                    } else {
                                        console.log("No changes to commit");
                                        return callback(json, changed, err);
                                    }
                                }, function(err) {
                                    console.log("Error reading file");
                                    return callback(null, false, err);
                                });
                            }
                        });
                    }, function(err) {
                        console.log("Error loading past file.");
                        console.log(err);
                        last_json = null;

                        //Clean the file using the rscript
                        exec('Rscript ' + cleaning_path + ' ' + path + formid + '.csv ' +
                                        path + formid + '_cleaned.csv',
                                        function(error, stdout, stderr) {

                            if(error) {
                                console.log(error, stderr);
                                return callback(null, false, "Error cleaning file with provided script");
                            } else {
                                csv().fromFile(path + formid + '_cleaned.csv').then(function(json) {
                                    console.log('Committing cleaned file');
                                    gitAddCommitPush(path + formid + '_cleaned.csv', repoPath, function(err) {
                                        return callback(json, true, err);
                                    });
                                    console.log("Proceeding assuming changes.");
                                }, function(err) {
                                    console.log("Error reading file");
                                    return callback(null, false, err);
                                });
                            }
                        });
                    });
                }
            });
        }
    });
}

function pullGitRepo(repoURL, repoPath, callback) {
    console.log('Pulling git repo');
    git.log({fs, dir: repoPath}).then(function(paths) {
        console.log("Get repo exists - moving on to pull");
        exec('git -C ' + repoPath + ' pull', (error, stdout, stderr) => {
            if(error) {
                console.log("Error pulling git repo")
                console.log(error);
                callback(error);
            } else {
                console.log(stdout);
                console.log("Pulled repo successfully")
                callback();
            }
        });

    }, function(err) {
        console.log('Error pulling git repo');
        if(err.name == 'ResolveRefError') {
            console.log('Cloning new repo', repoURL);
            exec('git clone ' + repoURL, (error, stdout, stderr) => {
                if(error) {
                    console.log("Error cloning git repo")
                    console.log(error);
                    callback(error);
                } else {
                    console.log("Repo cloned successfully")
                    callback();
                }
            });
        }
    });
}

var seed = 1;
function random() {
    var x = Math.sin(seed++) * 10000;
    return x - Math.floor(x);
}

function generateBackcheckList(entrySurveys) {
    //remove all surveys without g_install
    surveys_to_remove = []
    for(let i = 0; i < entrySurveys.length; i++) {
        if(entrySurveys[i]['g_install'] != '1') {
            surveys_to_remove.push(i)
        }
    }

    for(let i = surveys_to_remove.length -1; i >= 0; i--) {
       entrySurveys.splice(surveys_to_remove[i],1);
    }

    //Sort the array by time
    entrySurveys.sort(function(a,b) {
       return Date.parse(a['endtime']) - Date.parse(['endtime']);
    });

    //Now draw from 10% from this list using a stable seed

    //The methodology here will be to generate the complete list of
    //random numbers - more than we need, then draw from them in order
    //until we reach 10% of the current sample size
    var array = []
    var count = 0;
    while(true) {
        num = Math.round(random()*207);
        if(!array.includes(num)) {
            array.push(num);
            count++;
        }

        if(count >= 21) {
            break;
        }
    }

    //sort the array
    array.sort();

    //output any less than our length
    var list = 1;
    backcheck_surveys = [];
    for(let i = 0; i < entrySurveys.length; i++) {
        if(array.includes(i)) {
            list ^= 1;
            if(list == 0) {
                entrySurveys[i].backcheck_group = 1;
            } else {
                entrySurveys[i].backcheck_group = 2;
            }
            backcheck_surveys.push(entrySurveys[i]);
        }
    }

    writeBackcheckTablePostgres(backcheck_surveys, function(err) {
        if(err) {
            console.log("Error writing backcheck table")
        } else {
            console.log("Success writing backcheck table")
        }
    })
}

function updatePayments(callback) {
   console.log('Updating to payments');
   function drop(callback) {
      pg_pool.query('DROP VIEW IF EXISTS payments_per_respondent', (err, res) => {
         callback(err);
      });
   }

   function create(callback) {
      pg_pool.query('CREATE VIEW payments_per_respondent AS ' +
		   `SELECT max(respondents.respondent_id) AS respondent_id,
  		      count(payments.amount) AS number_payments_issued,
  		      sum(payments.amount) AS total_amount_paid,
  		      max(payments.time_submitted) AS last_payment_time,
  		      array_to_string(array_agg(date(payments.time_submitted)), ',') AS list_of_payment_dates,
  		      array_to_string(array_agg(payments.amount), ',') AS list_of_payment_amounts,
  		      array_to_string(array_agg(payments.external_transaction_id), ',') AS list_of_payment_ids,
  		      max(respondents.respondent_firstname) AS respondent_firstname,
  		      max(respondents.respondent_surnname) AS respondent_surname,
  		      max(respondents.respondent_popularname) AS respondent_popularname,
  		      max(respondents.phone_number) AS phone_number,
  		      max(respondents.carrier) AS carrier,
  		      max(respondents.second_phone_number) AS second_phone_number,
  		      max(respondents.alternate_contact_name) AS alternate_contact_name,
  		      max(respondents.alternate_phone_number) AS alternate_phone_number,
  		      bool_and(respondents.powerwatch) AS powerwatch
  		     FROM respondents
  		       RIGHT JOIN payments ON respondents.respondent_id = payments.respondent_id
  		    WHERE payments.status = 'complete'
  		    GROUP BY respondents.respondent_id` , (err, res) => {
         callback(err);
      });
   }

   async.series([drop, create], function(err, res) {
      callback(err);
   });
}
function updateVisit(callback) {
   console.log('Updating to visit');
   function drop(callback) {
      pg_pool.query('DROP VIEW IF EXISTS powerwatches_to_visit', (err, res) => {
         callback(err);
      });
   }

   function create(callback) {
      pg_pool.query('CREATE VIEW powerwatches_to_visit AS ' +
                 `SELECT d.respondent_id,
  		  d.respondent_firstname,
  		  d.respondent_surnname,
  		  d.respondent_popularname,
  		  d.fo_name,
  		  d.site_id,
  		  d.phone_number,
  		  d.second_phone_number,
  		  d.alternate_contact_name,
  		  d.alternate_phone_number,
  		  d.currently_deployed,
  		  d.core_id
  		 FROM backup.deployment_1556868406 d
  		   LEFT JOIN ( SELECT powerwatch.core_id,
  		          min(now() - powerwatch."time") AS min
  		         FROM powerwatch
  		        WHERE powerwatch."time" > (now() - '5 days'::interval)
  		        GROUP BY powerwatch.core_id) p ON d.core_id = p.core_id
  		WHERE p.core_id IS NULL AND d.currently_deployed = true
			AND (now() - d.deployment_start_time) > '5 days'::interval`, (err, res) => {
         callback(err);
      });
   }

   async.series([drop, create], function(err, res) {
      callback(err);
   });
}

function updateSiteSummary(callback) {
   console.log('Updating site summary');
   function drop(callback) {
      pg_pool.query('DROP VIEW IF EXISTS site_deployment_count', (err, res) => {
         callback(err);
      });
   }

   function create(callback) {
      pg_pool.query('CREATE VIEW site_deployment_count AS ' +
                    'SELECT CAST(deployment.site_id AS INTEGER) AS site, count(deployment.core_id) AS count ' +
                    'FROM deployment ' +
                    'WHERE deployment.currently_deployed = true ' +
                    'GROUP BY deployment.site_id ' +
                    'ORDER BY CAST(deployment.site_id AS INTEGER) ASC', (err, res) => {
         callback(err);
      });
   }

   async.series([drop, create], function(err, res) {
      callback(err);
   });
}

function updateViews(callback) {
    console.log('Updating Views');
    async.series([updateVisit,
		  updateSiteSummary,
		  updatePayments], function(err, res) {
         callback(err);
    });
}

//function to fetch surveys from surveyCTO
function fetchNewSurveys() {
    //fetch all surveys moving forward
    //send the API requests to surveyCTO - we probable also need attachments to process pictures
    fetchSurveys(survey_config.entrySurveyName, survey_config.entryCleaningPath, survey_config.gitRepoPath, function(entrySurveys, entry_changed, err) {
        if(err) {
            console.log("Error fetching and processing forms");
            console.log(err);
            return;
        } else {
            //We can go ahead and generate the backcheck table here
            //We need to recreate the object so it doesn't mess up future asynchronous processing
            newEntrySurveys = JSON.parse(JSON.stringify(entrySurveys));
            generateBackcheckList(newEntrySurveys);

            fetchSurveys(survey_config.DWSurveyName, survey_config.DWCleaningPath, survey_config.gitRepoPath, function(DWSurveys, dw_changed, err) {
                if(err) {
                    console.log("Error fetching and processing forms");
                    console.log(err);
                    return;
                } else {
                    fetchSurveys(survey_config.exitSurveyName, survey_config.exitCleaningPath, survey_config.gitRepoPath, function(exitSurveys, exit_changed, err) {
                        if(err) {
                            console.log("Error fetching and processing forms");
                            console.log(err);
                            return;
                        } else {
                            //now get the two csv from the achimota deployment
                            csv().fromFile(survey_config.gitRepoPath +  '/old_deployment/deployment.csv').then(function(deployment) {
                                csv().fromFile(survey_config.gitRepoPath +  '/old_removal/removal.csv').then(function(removal) {
                                    //now append the json arrays to the entry and exit surveys
                                    entrySurveys = entrySurveys.concat(deployment);
                                    entrySurveys = entrySurveys.concat(DWSurveys);
                                    exitSurveys = exitSurveys.concat(removal);
                                    async.series([async.apply(processSurveys, entrySurveys, exitSurveys),
                                                  updateViews], function(err,res) {
                                                      console.log(err);
                                    });
                                    if(entry_changed || exit_changed || dw_changed) {
                                       async.series([async.apply(processSurveys, entrySurveys, exitSurveys),
                                                     updateViews], function(err,res) {
                                                         console.log(err);
                                       });
                                    } else {
                                        console.log("No new surveys, no new processing scripts. Exiting.")
                                    }
                                });
                            });
                        }
                    });
                }
            });
        }
    });
}

//Call it once to start
console.log("Starting survey processing");
pullGitRepo(survey_config.gitRepoURL, survey_config.gitRepoPath, function(err) {
    if(err) {
        console.log('Error pulling git repo');
    } else {
        fetchNewSurveys();
    }
});

//Periodically query surveyCTO for new surveys - if you get new surveys processing them on by one
setInterval(function() {
    console.log("Starting survey processing");
    pullGitRepo(survey_config.gitRepoURL, survey_config.gitRepoPath, function(err) {
        if(err) {
            console.log('Error pulling git repo');
        } else {
            fetchNewSurveys();
        }
    });
}, 1200000);

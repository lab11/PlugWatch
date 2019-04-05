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
var path = require('path');

//get the usernames and passwords necessary for this task
var command = require('commander');
command.option('-d, --database [database]', 'Database configuration file.')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file')
        .option('-c, --config [config]', 'Deployment management configuration file')
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

var config = null;
if(typeof command.config !== 'undefined') {
    config = require(command.config);
} else {
    config = require('./deployment-manager-config.json');
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
    max: 20,
});


function get_type(name, meas) {
    if(name.split('_')[name.split('_').length - 1] == 'time') {
       return 'TIMESTAMPTZ';
    } else {
        switch(typeof meas) {
            case "string":
                return 'TEXT';
            case "boolean":
                return 'BOOLEAN';
            case "number":
                return 'DOUBLE PRECISION';
        }
    }

    return 'err';
}

function dropTempTableGeneric(table_name, callback) {

    function tableExists(table_name, callback) {
        //Remove the temp table if it exists
        pg_pool.query("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = $1)",
                        [table_name+'_temp'], (err, res) => {
            callback(err, res.rows[0]);
        });
    }

    function dropTable(table_name, row, callback) {
        if(rows.exists == true) {
            //Just go ahead and move the shadow table to respondnets
            pg_pool.query('DROP TABLE ' + table_name + '_temp', (err, res) => {
                callback(err);
            });
        } else {
            console.log("Temp table doesnt exits. Proceeding");
            callback();
        }
    }

    async.waterfall([
            async.apply(tableExists, table_name),
            async.apply(dropTable, table_name)
    ], function(err) {
        callback(err);
    });
}

function writeGenericTablePostgres(objects, table_name, outer_callback) {

    //If we didn't get any array move on
    if(objects.length == 0) {
        console.log("No records to write to table");
        return outer_callback();
    }

    function createTempTableFromObject(objects, table_name, callback) {
        //Find the object in the object array with the most fields
        //So that we get all the fields for creating the table
        var max = 0;
        var index = 0;
        for(var i = 0; i < objects.length; i++) {
            if(Object.keys(objects[i]).length > max) {
                max = Object.keys(objects[i]).length;
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
        //console.log("Issuing query: ", qstring);
        pg_pool.query(qstring, callback);
    }

    function insertValueInTable(objects, table_name, outer_callback) {
         console.log("Created temp table successfully. Inserting values");

         //Add all the respondents
         async.forEachLimit(objects, 10, function(value, callback) {
             var cols = "";
             var vals = "";
             var names = [];
             var values = [];
             var i = 1;
             names.push(table_name + '_temp');
             for (var name in value) {
                 if(value.hasOwnProperty(name)) {
                     if(typeof value[name] == 'object') {
                         for(var subname in value[name]) {
                             if(value[name].hasOwnProperty(subname)) {
                                 cols = cols + "%I, ";
                                 names.push(name + '_' + subname);
                                 vals = vals + "$" + i.toString() + ',';
                                 values.push(value[name][subname]);
                                 i = i + 1;
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
             pg_pool.query(qstring, values, callback);
         } , function(err) {
             outer_callback(err);
         });
    }

    function checkTableExists(table_name, callback) {
        //Okay now that we have successfully created the temp table
        //Let's move the table that already exists and change
        //the name of this one to the primary table
        //is there a table that exists for this device?
        console.log("Checking for table existence");
        pg_pool.query("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = $1)",[table_name], callback);

    }

    function renameTables(table_name, res, callback) {
        if(res.rows[0].exists == false) {
            //Just go ahead and move the shadow table to respondnets
            console.log("Table does not exist. Altering temp table name");
            pg_pool.query('ALTER TABLE ' + table_name + '_temp RENAME to ' + table_name, callback);
        } else {

           function renameOldTable(new_name, callback) {
               //Rename, then move the table
               console.log("Table does exist. Renaming old table and moving it's schema");
               pg_pool.query('ALTER TABLE ' + table_name + ' RENAME to ' + new_name, callback);
           }

           function changeSchema(new_name, callback) {
               pg_pool.query('ALTER TABLE ' + new_name + ' SET SCHEMA backup', callback);
           }

           function renameNewTable(table_name, callback) {
               pg_pool.query('ALTER TABLE ' + table_name + '_temp RENAME to ' + table_name, callback);
           }

           var new_name = table_name + '_' + Math.round((Date.now()/1000)).toString();

           async.series([
                async.apply(renameOldTable, new_name),
                async.apply(changeSchema, new_name),
                async.apply(renameNewTable, table_name)
           ] , function(err) {
                console.log("Error renaming tables");
                callback(err);
           });
        }
    }

    async.series([
            async.apply(dropTempTableGeneric, table_name),
            async.apply(createTempTableFromObject, objects, table_name),
            async.apply(insertValueInTable, objects, table_name),
            async.waterfall([
                async.apply(checkTableExists, table_name),
                async.apply(renameTables, table_name)
            ], function(err) {
                console.log("Error checking and renaming tables");
            }),
    ], function(err) {
        console.log("Error adding data to generic table");
        outer_callback(err);
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
               gps: entrySurveys[i].gps,
               gps_accurate: entrySurveys[i].g_gps_accurate,
               survey_time: exitSurveys[i].endtime,
               survey_id: exitSurveys[i].instanceID
        };

        if(typeof exitSurveys[i][exitSurveys[i].error_field] != 'undefined') {
            entry.value_of_error_field = exitSurveys[i][exitSurveys[i].error_field];
        }

        error_table.push(entry);
    }
    writeGenericTablePostgres(error_table, 'change_errors', outer_callback);
}

function updateTrackingTables(respondents, devices, entrySurveys, exitSurveys) {
    //Write the respondents and devices table to postgres
    async.series([
           async.apply(writeRespondentTablePostgres, respondents),
           async.apply(writeDevicesTablePostgres, devices),
           async.apply(writeEntryTablePostgres, entrySurveys),
           async.apply(writeExitTablePostgres, exitSurveys)
    ], function(err, result) {
        console.log(err);
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
    pg_pool.query('SELECT core_id, shield_id, product_id FROM devices', callback);
}

function getAppID(id_list) {
    //g_appQR_nr
    //appQR1
    var QR1 = null;
    if(survey.appQR1 != null) {
        QR1 = survey.appQR1.toUpperCase();
    }

    var QRn = survey.g_appQR_nr1.toUpperCase();
    var QRbar = survey.g_appQRbar.toUpperCase();

    if(QRbar.length == 15 || QRbar.length == 16) {
        return QRbar;
    } else if(typeof QR1 != 'undefined' && QR1 != null && (QR1.length == 15 || QR1.length == 16)) {
        return QR1;
    } else {
        if(QRn.length == 15 || QRn.length == 16) {
            return QRn;
        } else {
            return null;
        }
    }
}

function getDeviceID(id_list, devices) {
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

    //Now lets try the normal QR field
    if(typeof survey[qrField] != 'undefined' && survey[qrField] != null) {
       let id_to_parse = survey[qrField].split(':');
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

function getEntryCoordinates(survey) {
    //Which GPS numbers have been recorded
    var gps = ['g_gps_accurate','g_gps','gps'];

    var min_accuracy = null;
    var min_index = null;

    for(var i = 0; i < gps.length; i++){
        if(survey[gps[i]].Accuracy != '') {
            return [parseFloat(survey[gps[i]].Latitude),parseFloat(survey[gps[i]].Longitude)];
        }
    }

    return null;
}

function generateTrackingTables(deployment, redeployment, pickup, devices) {
    //Sort the surveys by submission time
    //This assumption makes it easier to process the surveys
    deployment.sort(function(a,b) {
       return Date.parse(a.survey_time) - Date.parse(b.survey_time);
    });

    redeployment.sort(function(a,b) {
       return Date.parse(a.survey_time) - Date.parse(b.survey_time);
    });

    pickup.sort(function(a,b) {
       return Date.parse(a.survey_time) - Date.parse(b.survey_time);
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
       for(let i = 0; i < deployment.length; i++) {

           var item = deployment[i];

           var device_info = null;
           var respondent_info = null;
           let surveySuccess = true;
           let respondent_id = item.respondent_id.toUpperCase();
           console.log("Processing entry survey for respondent", respondent_id);

           //Make sure that the R script didn't report an error for this survey
           if(typeof item.error != 'undefined' && (item.error == 'TRUE')) {
               console.log("Cleaning script marked survey as errored. Skipping survey.");
               surveySuccess = false;
               continue;
           }

          //Is the respondent ID valid?
          if(respondent_id.length != 8) {
               //This is a duplicate
               console.log("Invalid respondent ID for", item.survey_id,"Skipping suvey.");
               surveySuccess = false;
               item.error = true;
               item.error_field = 'a_respid';
               item.error_comment = 'Invalid Respondent ID';
               continue;
          }

           //Okay, first, have we already processed an entry survey for
           //this respondent ID
           if(typeof respondents[respondent_id] != 'undefined') {
               //This is a duplicate
               console.log("Respondent duplicate of", respondents[respondent_id].survey_id,"Skipping suvey.");
               surveySuccess = false;
               item.error = true;
               item.error_field = 'a_respid';
               item.error_comment = 'Duplicate respondent ID of ' + respondents[respondent_id].survey_id;
               continue;
           }

           //Do we already have a respondent with this phone number?
           for(var key in respondents) {
               if(respondents[key].phoneNumber == item.phone_number) {
                   //This is a duplicate
                   console.log("Phone number duplicate of respondent", key, "Skipping suvey.");
                   surveySuccess = false;
                   item.error = true;
                   item.error_field = 'e_phonenumber';
                   item.error_comment = 'Duplicate phone number of respondent ' + key;
                   continue;
               }
           }

           //Okay this is a unique respondent with a unique phone number
           //Process the survey

           //Get the most accurate latitude and longitude possible
           var coords = getEntryCoordinates(item);
           if(coords == null) {
               console.log("Invalid coordinates. Skipping suvey.");
               surveySuccess = false;
               item.error = true;
               item.error_field = 'g_gps_accuracy';
               item.error_comment = 'No valid GPS coordinates found';
               continue;
           }

           if(typeof item.carrier == 'undefined') {
               carrier = carrier_map[item.carrier]
           } else {
               console.log('Unkown carrier. Skipping survey');
               carrier = 'Unkown';
               surveySuccess = false;
               item.error = true;
               item.error_field = 'e_carrier';
               item.error_comment = 'Unkown/other carrier. Respondent cannot be paid.';
               continue;
           }

           respondent_info = {
               respondent_id: item.respondent_id,
               respondent_firstname: item.respondent_firstname,
               respondent_surname: item.respondent_surname,
               respondent_popularname: item.respondent_popularname,
               fo_name: item.surveyor_name,
               site_id: item.site_id,
               phone_number: item.phone_number,
               carrier: carrier,
               second_phone_number: item.second_phone_number,
               alternate_contact_name: item.alternate_contact_name,
               alternate_phone_number: item.alternatE_phone_number,
               location_latitude: coords[0],
               location_longitude: coords[1],
               pilot_survey_time: item.survey_time,
               pilot_survey_id: item.survey_id
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
                      depoyment_survey_time: entrySurveys[i].endtime,
                      deployment_sruvey_id: entrySurveys[i].instanceID,
                  };

                  //Update the respondent to say that they do have a powerwatch
                  respondent_info.powerwatch = true;
                  respondent_info.powerwatch_core_id = core_id;
                  respondent_info.powerwatch_shield_id = shield_id;
                  respondent_info.powerwatch_deployment_time = entrySurveys[i].endtime;
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
                     exitSurveys[i].error_comment = 'Reported device not currently deployed with reported respondent';
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
                        //This devices is currently deployed
                        console.log("Device already deployed, skipping");
                        surveySuccess = false;
                        exitSurveys[i].error = true;
                        exitSurveys[i].error_field = 'g_deviceID_give';
                        exitSurveys[i].error_comment = 'Device already deployed. Cannot be deployed again.';
                        continue;
                     }
                  }

                  device_add_info = {
                      core_id: core_id,
                      shield_id: shield_id,
                      respondent_id: respondent_id,
                      respondent_firstname: respondents[respondent_id].e_firstname,
                      respondent_surnname: respondents[respondent_id].e_surnames,
                      respondent_popularname: respondents[respondent_id].e_popularname,
                      fo_name: exitSurveys[i].surveyor_name,
                      site_id: respondents[respondent_id].site_id,
                      deployment_start_time: exitSurveys[i].endtime,
                      phone_number: respondents[respondent_id].phone_number,
                      second_phone_number: respondents[respondent_id].second_phone_number,
                      alternate_contact_name: respondents[respondent_id].alternate_contact_name,
                      alternate_phone_number: respondents[respondent_id].alternate_phone_number,
                      carrier: respondents[respondent_id].carrier,
                      currently_deployed: true,
                      location_latitude: removed_device.entryLatitude,
                      location_longitude: removed_device.entryLongitude,
                      installed_outage: exitSurveys[i].installed_outage,
                      deployment_survey_time: exitSurveys[i].endtime,
                      deployment_survey_id: exitSurveys[i].instanceID,
                  };
              }
           }

           if(surveySuccess) {
               still_making_progress = true;

               //remove the removed device
               console.log("Updating device to be removed with following info");
               console.log(device_removal_info);
               devices[device_removal_info.index].currently_deployed = false;
               devices[device_removal_info.index].deployment_end_time = device_removal_info.removal_time;
               console.log("Updated device info:", devices[device_removal_info.index])

               //Update the respondent
               respondents[respondent_id].powerwatch = false;
               respondents[respondent_id].change_survey_time = device_removal_info.removal_time;
               respondents[respondent_id].change_survey_id = exitSurveys[i].instanceID;
               respondents[respondent_id].powerwatch_removal_time = device_removal_info.removal_time;
               console.log("Updated respondent info:", respondents[respondent_id])

               //Was a device deployed
               if(device_add_info != null) {
                  console.log("Adding device with info");
                  console.log(device_add_info);
                  devices.push(device_add_info);

                  //Update the respondent to say that they do have a powerwatch
                  respondents[respondent_id].powerwatch = true;
                  respondents[respondent_id].powerwatch_core_id = device_add_info.core_id;
                  respondents[respondent_id].powerwatch_shield_id = device_add_info.shield_id;
                  respondents[respondent_id].powerwatch_deployment_time = device_add_info.deployment_start_time;
               }

               surveys_to_remove.push(i);
           }
       }

       //Actually remove the surveys
       for(let i = 0; i < surveys_to_remove.length; i++) {
          exitSurveys.splice(surveys_to_remove[i],1);
       }
   }

   //Okay we are done making progress
   //We should write out any unprocessed surveys and their reasons to postgres
   //We should also write the updated respondent and device tables to postgres
   console.log();
   console.log("Updating tracking tables after processing surveys");
   //updateTrackingTables(respondents, devices, entrySurveys, exitSurveys);
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

function generateSiteSummary(entrySurveys) {
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

    //Now count the surveys per site
    site_summary = {};
    for(let i = 0; i < entrySurveys.length; i++) {
        if(typeof site_summary[entrySurveys[i].site_id] == 'undefined') {
            site_summary[entrySurveys[i].site_id] = {};
            site_summary[entrySurveys[i].site_id].deployed_total = 0;
            site_summary[entrySurveys[i].site_id].deployed_without_error = 0;
            site_summary[entrySurveys[i].site_id].deployed_with_error = 0;
        }

        if(typeof entrySurveys[i].error != 'undefined' && entrySurveys[i].error == 'TRUE') {
            site_summary[entrySurveys[i].site_id].deployed_with_error += 1;
        } else if(typeof entrySurveys[i].error != 'undefined' && entrySurveys[i].error == 'FALSE') {
            site_summary[entrySurveys[i].site_id].deployed_without_error += 1;
        } else {
            site_summary[entrySurveys[i].site_id].deployed_without_error += 1;
        }

        site_summary[entrySurveys[i].site_id].deployed_total += 1;
    }

    //now convert this to an array
    table_array = [];
    for(var site in site_summary) {
        if(site_summary.hasOwnProperty(site)) {
            entry = {
                site: site,
                deployed_total: site_summary[site].deployed_total,
                deployed_without_error: site_summary[site].deployed_without_error,
                deployed_with_error: site_summary[site].deployed_with_error,
            }

            table_array.push(entry)
        }
    }

    writeGenericTablePostgres(table_array, 'site_summary', function(err) {
        if(err) {
            console.log('Error writing site summary:', err);
        } else {
            console.log('Wrote site summary successfully');
        }
    });
}

function gitAddCommitPush(file, repoPath, callback) {
    //add the file
    //remove the first directory from the filePath
    file_split = file.split('/');
    file_split.shift();
    file = file_split.join('/');

    console.log("Committing",file,"in repo",repoPath);

    git.add({fs, dir: repoPath, filepath: file}).then(function(result) {
        git.commit({fs, dir: repoPath, message: "Auto updating newly cleaned file",
                        author:{name: "Deployment_Management_Service", email: "adkins@berkeley.edu"}}).then(function(result) {
                exec('git -C ' + repoPath + ' push origin master', callback);
            } , function(err) {
                callback(err);
            });
    }, function(err) {
        callback(err);
    });
}

function pullGitRepo(repoURL, repoName, callback) {
    console.log('Pulling git repo');
    git.log({fs, dir: repoName}).then(function(paths) {
        console.log("Get repo exists - moving on to pull");
        exec('git -C ' + repoName + ' pull',  callback);
    } , function(err) {
        console.log('Error pulling git repo');
        if(err.name == 'ResolveRefError') {
            console.log('Cloning new repo', repoURL);
            exec('git clone ' + repoURL, callback);
        }
    });
}

function getDataFromSource(source, callback) {

    console.log("Fetching data");

    if(source.type == "surveyCTO") {

       console.log("From surveyCTO");

        var options = {
            uri: source.url,
            auth: {
                user: source.username,
                pass: source.password,
                sendImmediately: false
            }
        };

        function saveOutput(outputFileName, response, body, callback) {
            //get the root of the cleaning path
            fs.writeFile(outputFileName + '.csv', body, function(err) {
               callback(err, outputFileName + '.csv');
            });
        }

        async.waterfall([
            async.apply(request, options),
            async.apply(saveOutput, source.name)
        ], function(err, result) {
            callback(err, result);
        });

    } else if(source.type == "git") {
        console.log("From git repo");
        pullGitRepo(source.url, source.name, callback);
    }
}

function cleanSource(cleaning, inputFileName, callback) {
    //here we need to loop through the cleaning, executing
    //the cleaning scripts on the prior step and running it on the next step

    console.log("Cleaning input data starting from", inputFileName);

    function runCleaningScript(cleaning, inputName, outputName, callback) {

        //we need to contstruct the input path relative to the execution script,
        //the output path relative to the execution script
        //then execute
        //exution path
        fileToExecute = cleaning.name + '/' + cleaning.subdirectory + '/' + cleaning.script;
        console.log("Executing: ", fileToExecute, inputName, outputName);
        exec(fileToExecute, inputName, outputName, callback);
    }

    //one unit of cleaning
    function pullCleanCommit(cleaning, inputName, callback) {


        var outputName = cleaning.name + '/' +
                         cleaning.subdirectory + '/' +
                         'output_' + cleaning.stepname + '.csv';

        console.log("Cleaning file", inputName, "and saving to", outputName);

        if(cleaning.type == "git") {

            async.series([
                async.apply(pullGitRepo, cleaning.url, cleaning.name),
                async.apply(runCleaningScript, cleaning, inputName, outputName),
                async.apply(gitAddCommitPush, outputName, cleaning.name, cleaning.name)
            ], function(err) {
                callback(err, outputName);
            });
        }
    }

    var currentName = inputFileName;

    //create a list of the cleaning steps to be done. Th
    var clean_list = [];
    if(cleaning.length > 0) {
        clean_list.push(async.apply(pullCleanCommit, cleaning[0], currentName));
    }

    for(var i = 1; i < cleaning.length; i++) {
        clean_list.push(async.apply(pullCleanCommit, cleaning[i]));
    }

    async.waterfall(clean_list, function(err, result) {
        callback(err, result);
    });
}

function getOutput(output, outputFileName, callback) {
    //read the csv from the file name
    var data_to_output = [];
    csv().fromFile(outputFileName).then(function(json) {
        //map the fields of the csv to the correct fields in the json
        for(var i = 0; i < json.length; i++) {
            var obj = {};
            for(var item in output.field_mapping) {
                obj[item] = json[i][output.field_mapping[item]];
            }
            data_to_output.push(obj);
        }

        callback(null, output.type, output.name, data_to_output);
    }, function(err) {
       callback(err);
    });
}


function getCleanProcessData() {
    var dataToProcess = {
        "deployment" : [],
        "redeployment" : [],
        "pickup" : [],
        "auxillary" : {}
    };

    console.log("Starting data processing");

    //iterative processing of the data sources list
    async.eachSeries(config.data_sources, function(item) {
        async.waterfall([
                async.apply(getDataFromSource, item.source),
                async.apply(cleanSource, item.cleaning),
                async.apply(getOutput, item.output)
        ], function(err, type, name, data) {
            //append the result to the master data log
            if(type == "device_deployment") {
                dataToProcess.deployment.push(data);
            } else if (type == "device_redeployment") {
                dataToProcess.redeployment.push(data);
            } else if (type == "device_pickup") {
                dataToProcess.pickup.push(data);
            } else if (type == "auxillary") {
                dataToProcess.auxillary[name] = [];
                dataToProcess.auxillary[name].push(data);
            }
        });
    } , function(err) {
        async.waterfall([
                getDeviceTable,
                async.apply(generateTrackingTables, dataToProcess.deployment,
                                                    dataToProcess.redeployment,
                                                    dataToProcess.pickup)
        ], function(err) {
        });
    });
}

getCleanProcessData();

//Periodically query surveyCTO for new surveys - if you get new surveys processing them on by one
setInterval(getCleanProcessData, 1200000);

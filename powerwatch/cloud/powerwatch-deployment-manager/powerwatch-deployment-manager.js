#!/usr/bin/env node

const { Pool }  = require('pg');
var format = require('pg-format');
const request = require('request');
const fs = require('fs');
const jpeg = require('jpeg-js');
const jsQR = require('jsqr');
const { exec } = require('child_process');
const csv = require('csvtojson');
var async = require('async');
var diff = require('deep-diff');
var admin = require('firebase-admin');
const git  = require('isomorphic-git');
   

//get the usernames and passwords necessary for this task
var command = require('commander');
command.option('-d, --database [database]', 'Database configuration file.')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file')
        .option('-s, --survey [survey]', 'Survey configuration file')
        .option('-U, --surveyusername [surveyusername]', 'SurveyCTO username file')
        .option('-P, --surveypassword [surveypassword]', 'SurveyCTO passowrd file').parse(process.argv)
        .option('-o, --oink [oink]', 'OINK configuration file').parse(process.argv);

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

//The only valid way to do this is through a service account ID
//So we will start by
var oink_config = {};
if(typeof command.oink !== 'undefined') {
    oink_config = require(command.oink);
} else {
    oink_config = require('./oink-config.json');
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

//Initialize the firebase project
var serviceAccount = require(oink_config.service_account);
admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    databaseURL: oink_config.database
});

//Get the database object
var db = admin.firestore();

//This is a recurive function that we could sub in if we get a lot of image
//corruption. But's it's untested so let's leave it out for now
function handleRequestResponse(options, error, response, body, depth, callback) {
    //We are doing this recursively so limit the depth
    if(depth > 4) {
        return callback(null);
    }

    if(depth > 1) {
        console.log("Called with depth ", depth);
    }

    if(error) {
        console.log(error);
        request(options, (function() {
           return function(error, response, body) {
              //We should just try to process this image immediately with
              //the QR code processing code
              console.log("Got error response - calling handle Request recursively");
              handleRequestResponse(options, error, response, body, depth + 1, function(data) {
                  return callback(data);
              });
           };
        })());
    } else {
       var buf = new Buffer(body, "binary");
       try {
           var rawImage = jpeg.decode(buf, true);
           const code = jsQR(rawImage.data, rawImage.width, rawImage.height);
           if(code == null) {
              //There was no readable QR code here
              return callback(null);
           } else {
              //We have a QR code
              return callback(code.data);
           }
       } catch (error) {
           //we should just retry this request
           console.log(error);
           fs.writeFile("error.jpg", buf, function(err) {
           });

           request(options, (function() {
              return function(error, response, body) {
                 //We should just try to process this image immediately with
                 //the QR code processing code
                 console.log("Got error response - calling handle Request recursively");
                 handleRequestResponse(options, error, response, body, depth + 1, function(data) {
                     return callback(data);
                 });
              };
           })());
       }
    }
}


function extractQRCodes(survey, url_field, output_field, outer_callback) {

    // Why 2...well emperically surveyCTO doesn't like more??
    // You can easily start getting ECONNRESETS
    async.forEachOfLimit(survey, 2, function(value, key, callback) {
        if(value[url_field] != '') {
            console.log('Fetching QR for', url_field);
            var options = {
              uri: value[url_field],
              auth: {
                 user: survey_config.username,
                 pass: survey_config.password,
                 sendImmediately: false
              },
              headers: {
                 "X-OpenRosa-Version": "1.0"
              },
              encoding: 'binary'
           };

           request(options, (function() {
              return function(error, response, body) {
                 //We should just try to process this image immediately with
                 //the QR code processing code

                 //Here's how you call the recursive function
                 handleRequestResponse(options, error, response, body, 1, function(data) {
                     if(data) {
                          console.log("Processed QR code:", data);
                          survey[key][output_field] = data;
                     } else {
                          survey[key][output_field] = null;
                          console.log("No processable QR code");
                          fs.writeFile(url_field + '.jpg', new Buffer(body,'binary'), function(err) {
                          });
                     }
                     callback();
                 });
              };
           })());

        } else {
            callback();
        }
    }, function(err) {
        if(err) {
            console.log("Some error with async");
        }
        outer_callback(survey);
    });
}

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
    //Remove the temp table if it exists
    pg_pool.query("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = $1)",[table_name+'_temp'], (err, res) => {
        if (err) {
            console.log(err);
            return callback(err);
        } else {
            if(res.rows[0].exists == true) {
                //Just go ahead and move the shadow table to respondnets
                pg_pool.query('DROP TABLE ' + table_name + '_temp', (err, res) => {
                    if(err) {
                        console.log("Error dropping temp table");
                        return callback(err);
                    } else {
                        console.log("Dropped temp table");
                        return callback(null);
                    }
                });
            } else {
                console.log("Temp table doesnt exits. Proceeding");
                callback(null);
            }
        }
    });
}

function writeGenericTablePostgres(objects, table_name, outer_callback) {

    //If we didn't get any array move on
    if(objects.length == 0) {
        console.log("No records to write to table");
        return outer_callback();
    }

    dropTempTableGeneric(table_name, function(err) {
        if(err) {
            return outer_callback(err);
        } else {
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
            pg_pool.query(qstring, (err, res) => {

                if(err) {
                    console.log("Error creating shadow table");
                    console.log(err);
                    return outer_callback(err);
                } else {
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
                        pg_pool.query(qstring, values, (err, res) => {
                            if(err) {
                                console.log("Error inserting into temp table");
                                callback(err);
                            } else {
                                console.log('posted successfully!');
                                callback();
                            }
                        });
                    }, function(err) {
                        if(err) {
                            console.log("Some error with async");
                        }

                        //Okay now that we have successfully created the temp table
                        //Let's move the table that already exists and change
                        //the name of this one to the primary table
                        //is there a table that exists for this device?
                        console.log("Checking for table existence");
                        pg_pool.query("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = $1)",[table_name], (err, res) => {
                            if (err) {
                                console.log(err);
                                return outer_callback(err);
                            } else {
                                if(res.rows[0].exists == false) {
                                    //Just go ahead and move the shadow table to respondnets
                                    console.log("Table does not exist. Altering temp table name");
                                    pg_pool.query('ALTER TABLE ' + table_name + '_temp RENAME to ' + table_name, (err, res) => {
                                        if(err) {
                                            console.log("Error renaming table");
                                            return outer_callback(err);
                                        } else {
                                            console.log("Created new table");
                                            return outer_callback(null);
                                        }
                                    });
                                } else {
                                   //Rename, then move the table
                                   console.log("Table does exist. Renaming old table and moving it's schema");
                                   var new_name = table_name + '_' + Math.round((Date.now()/1000)).toString();
                                   pg_pool.query('ALTER TABLE ' + table_name + ' RENAME to ' + new_name, (err, res) => {
                                       if(err) {
                                          console.log(err);
                                          return outer_callback(err);
                                       } else {
                                           pg_pool.query('ALTER TABLE ' + new_name + ' SET SCHEMA backup', (err, res) => {
                                               if(err) {
                                                   console.log(err);
                                                   return outer_callback(err);
                                               } else {
                                                   pg_pool.query('ALTER TABLE ' + table_name + '_temp RENAME to ' + table_name, (err, res) => {
                                                       if(err) {
                                                           console.log("Error renaming table");
                                                           return outer_callback(err);
                                                       } else {
                                                           console.log("Created new table");
                                                           return outer_callback(null);
                                                       }
                                                   });
                                               }
                                           });
                                        }
                                   });
                                }
                            }
                        });
                    });
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

function writeRespondentTableOINK(respondents, callback) {
    //Okay this needs to write respondents to OINK
    //Some users may already exits, and they should not be removed
    //But they may need to be mark as inactive or such

    //Then oink needs a way of reprocessing updated users based on unique
    //attributes

    //If there is a user in OINK that no longer exists in our script,
    //we should process that too
    
    //Okay first loop through the respondents and add/merge them
    //into OINK
    console.log();
    console.log("Adding respondent list to OINK");
    var batch = db.batch();
    for(var key in respondents) {
        if(respondents.hasOwnProperty(key)) {
            //Okay what fields need to be set for oink
            
            //name of doc-respondent ID
            //active true/false
            //first survey true/false
            //incentivized true/false
            //user_id - respondent_id
            //phone_number
            //timestamp
            //payment_service
            //app id
            //powerwatch true/false
            //powerwatch install time
            var oink_user = {
                user_id: respondents[key].respondent_id,
                incentivized: respondents[key].currently_active,
                app_installed: respondents[key].currently_active,
                powerwatch_installed: respondents[key].powerwatch,
                payment_service: "korba",
                phone_number: respondents[key].phone_number,
                phone_carrier: respondents[key].carrier,
            };

            if(oink_user.powerwatch_installed) {
                oink_user.powerwatch_install_time = respondents[key].pilot_survey_time;
                oink_user.powerwatch_core_id = respondents[key].powerwatch_core_id;
            }

            if(oink_user.app_installed) {
                oink_user.app_install_time = respondents[key].pilot_survey_time;
                oink_user.app_id = respondents[key].app_id;
            }

            //get the doc
            console.log(key);
            var docRef = db.collection('OINK_user_list').doc(key);
            batch.set(docRef, oink_user, {merge: true});
        }
    }
    
    //Okay now, if there are any users not in our current respondent list
    // (like there was an error and the cleaning script removed them)
    // Set them to not active, not incentivized, no powerwatch
    db.collection('OINK_user_list').get().then(users => {
        users.forEach(doc => {
            //If the user ID is not in our respondent list just set it to not
            //incentivized
            if(typeof respondents[doc.id] == 'undefined' && (doc.data().active == true || doc.data().incentivized == true)) {
                console.log('Removing user with ID', doc.id, 'from incentivized list');
                var docRef = db.collection('OINK_user_list').doc(doc.id);
                batch.set(docRef, {
                    active: false,
                    incentivized: false
                }, {
                    merge: true
                });
            }
        });

        console.log("Committing respondent list and deactivation list");
        batch.commit().then(function() {
            console.log("Successfully updated oink user list");
            return callback();
        }).catch(function(error) {
            console.loog("Error updating oink user list:", error);
            return callback(error);
        });

    }).catch(err => {
        console.log(err);
        console.log("error reading oink user list");
        return callback(error);
    });
}

function updateTrackingTables(respondents, devices, entrySurveys, exitSurveys) {
    //Write the respondents and devices table to postgres
    writeRespondentTablePostgres(respondents, function(err) {
    if(err) {
        console.log(err);
    } else {
       writeRespondentTableOINK(respondents, function(err) {
       if(err) {
          console.log(err);
       } else {
            writeDevicesTablePostgres(devices, function(err) {
            if(err) {
                console.log(err);
            } else {
                writeEntryTablePostgres(entrySurveys, function(err) {
                if(err) {
                    console.log(err);
                } else {
                    writeExitTablePostgres(exitSurveys, function(err) {
                    if(err) {
                        console.log(err);
                    } else {
                        //This is where you should write to OINK
                    }
                    });
                }
                });
            }
            });
       }
       });
    }
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

function getGenericID(survey, qrField, qrBarField, manualField, devices) {
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

function getExitGiveDeviceID(survey, devices) {
    //g_deviceID_retrieve
    //deviceRetrieveQR

    return getGenericID(survey, 'deviceGiveQR', 'g_deviceQRbar_give', 'g_deviceID_give', devices);
}

function getExitRetrieveDeviceID(survey, devices) {
    //g_deviceID_retrieve
    //deviceRetrieveQR

    return getGenericID(survey, 'deviceRetrieveQR', 'g_deviceQRbar_retrieve', 'g_deviceID_retrieve', devices);
}

function getEntryDeviceID(survey, devices) {
    //g_deviceID
    //deviceQR

    return getGenericID(survey, 'deviceQR', 'g_deviceQRbar', 'g_deviceID', devices);
}

function getEntryCoordinates(survey) {
    //Which GPS numbers have been recorded
    var gps = ['g_gps_accurate','g_gps','gps'];

    var min_accuracy = null;
    var min_index = null;

    for(var i = 0; i < gps.length; i++){
        if(survey[gps[i]].Accuracy != '') {
            if(min_accuracy == null || parseFloat(survey[gps[i]].Accuracy) < min_accuracy) {
                min_index = i;
            }
        }
    }

    if(min_index == null) {
        return null;
    } else {
        return [parseFloat(survey[gps[min_index]].Latitude),parseFloat(survey[gps[min_index]].Longitude)];
    }
}

var carrier_map = {};
carrier_map['1'] = 'MTN';
carrier_map['2'] = 'Airtel';
carrier_map['3'] = 'Vodaphone';
carrier_map['4'] = 'Tigo';
carrier_map['5'] = 'GLO';

function generateTrackingTables(entrySurveys, exitSurveys, device_table) {
    //Sort the surveys by submission time
    //This assumption makes it easier to process the surveys
    entrySurveys.sort(function(a,b) {
       return Date.parse(a) - Date.parse(b);
    });

    exitSurveys.sort(function(a,b) {
       return Date.parse(a) - Date.parse(b);
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
           console.log("Processing entry survey for respondent", respondent_id);

           //Make sure that the R script didn't report an error for this survey
           if(typeof entrySurveys[i].error != 'undefined' && (entrySurveys[i].error == true || entrySurveys[i].error == 'TRUE')) {
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
          if(respondent_id.length != 8) {
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
           console.log("Processing entry survey for respondent ", respondent_id);

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
              let ids = getExitRetrieveDeviceID(exitSurveys[i], device_table);
              if(ids == null) {
                  //This is an error, post the error
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
              var ids = getExitGiveDeviceID(exitSurveys[i], device_table);
              if(ids == null) {
                  //This is an error, post the error
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
               devices[device_removal_info.index].currently_deployed = false;
               devices[device_removal_info.index].deployment_end_time = device_removal_info.removal_time;

               //Update the respondent
               respondents[respondent_id].powerwatch = false;
               respondents[respondent_id].change_survey_time = device_removal_info.removal_time;
               respondents[respondent_id].change_survey_id = exitSurveys[i].instanceID;
               respondents[respondent_id].powerwatch_removal_time = device_removal_info.removal_time;

               //Was a device deployed
               if(device_add_info != null) {
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
   updateTrackingTables(respondents, devices, entrySurveys, exitSurveys);
}

function processSurveys(entrySurveys, exitSurveys) {
    //This should enter a powerwatch user into the postgres deployment table and the oink table
    //getDevicesTable(function(devices) {
    //   //Okay we should not have completely processed entry and exit surveys
    //   generateTrackingTables(entrySurveys, exitSurveys, devices);
    //});

    //Parse out the QR codes
    extractQRCodes(entrySurveys, "g_deviceQR", "deviceQR", function(entrySurveys) {
        extractQRCodes(entrySurveys, "g_appQR_pic1", "appQR1", function(entrySurveys) {
            extractQRCodes(exitSurveys, "g_deviceQR_retrieve", "deviceRetrieveQR", function(exitSurveys) {
                extractQRCodes(exitSurveys, "g_deviceQR_give", "deviceGiveQR", function(exitSurveys) {
                    getDevicesTable(function(devices) {
                       //Okay we should not have completely processed entry and exit surveys
                       generateTrackingTables(entrySurveys, exitSurveys, devices);
                    });
                });
            });
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
                                    console.log("Committing cleaned file");
                                    gitAddCommitPush(path + formid + '_cleaned.csv', repoPath, function(err) {
                                        return callback(json, changed, err);
                                    });
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
                                        return callback(json, changed, err);
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
        if(err.name == 'ResolveRefError') {
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
            fetchSurveys(survey_config.exitSurveyName, survey_config.exitCleaningPath, survey_config.gitRepoPath, function(exitSurveys, exit_changed, err) {
                if(err) {
                    console.log("Error fetching and processing forms");
                    console.log(err);
                    return;
                } else {
                    processSurveys(entrySurveys, exitSurveys);
                    //if(entry_changed || exit_changed) {
                    //    processSurveys(entrySurveys, exitSurveys);
                    //} else {
                    //    console.log("No new surveys, no new processing scripts. Exiting.")
                    //}
                }
            });
        }
    });
}

//Periodically query surveyCTO for new surveys - if you get new surveys processing them on by one
///setInterval(fetchNewSurveys, 600000)
pullGitRepo(survey_config.gitRepoURL, survey_config.gitRepoPath, function(err) {
    if(err) {
        console.log('Error pulling git repo');
    } else {
        fetchNewSurveys();
    }
});

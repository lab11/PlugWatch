#!/usr/bin/env node

const { Pool }  = require('pg');
var format = require('pg-format');
const request = require('request')
const fs = require('fs');
const jpeg = require('jpeg-js');
const jsQR = require('jsqr');
const { exec } = require('child_process');
const csv = require('csvtojson');
var async = require('async');
var diff = require('deep-diff');


//get the usernames and passwords necessary for this task
var command = require('commander');
command.option('-d, --database [database]', 'Database configuration file.')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file')
        .option('-s, --survey [survey]', 'Survey configuration file')
        .option('-U, --surveyusername [surveyusername]', 'SurveyCTO username file')
        .option('-P, --surveypassword [surveypassword]', 'SurveyCTO passowrd file').parse(process.argv)
        .option('-o, --oink [oink]', 'OINK configuration file')
        .option('-a, --oinkusername [oinkusername]', 'OINK username file')
        .option('-b, --oinkpassword [oinkpassword]', 'OINK password file').parse(process.argv);

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

var oink_config = {};
if(typeof command.oinkusername !== 'undefined') {
    //survey_config = require(command.survey);
    //survey_config.username = fs.readFileSync(command.surveyusername,'utf8').trim()
    //survey_config.password = fs.readFileSync(command.surveypassword,'utf8').trim()
} else {
    //survey_config = require('./survey-config.json'); 
}

const pg_pool = new  Pool( {
    user: timescale_config.username,
    host: timescale_config.host,
    database: timescale_config.database,
    password: timescale_config.password,
    port: timescale_config.port,
    max: 20,
});

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
       console.log(response.statusCode);
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
            console.log(key);
            console.log(value[url_field]);
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
                 console.log(key);
                 handleRequestResponse(options, error, response, body, 1, function(data) {
                     if(data) {
                          console.log(data);
                          survey[key][output_field] = data;
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
        console.log("All asyncs have returned");
        outer_callback(survey);
    });

}

function updateTrackingTables(respondents, devices, entrySurveys, exitSurveys) {

}

function lookupCoreID(core_id, devices) {

   for(var i = 0; i < devices.length; i++) {
       if(devices[i].core_id == core_id) {
           return [devices[i].core_id, devices[i].shield_id];
       }
   }
   
   return null;
}

function lookupShieldID(shield_id, devices) {
   
   for(var i = 0; i < devices.length; i++) {
       if(devices[i].shield_id == shield_id) {
           return [devices[i].core_id, devices[i].shield_id];
       }
   }
   
   return null;
}

function getDevicesTable(callback) {
    //Query devices table
    pg_pool.query('SELECT core_id, shield_id, product_id FROM devices', null, (err, res) => {
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
   //appQR2

   //First check to see if either QR is readable
   if(survey.appQR1 != null && survey.appQR2 != null) {
       if(survey.appQR1 != survey.appQR2) {
           //two valid QRS that don't match?
           console.log('App QRs do not match');
           if(survey.appQR1 == survey.g_appQR_nr) {
               return survey.appQR1;
           } else if (survey.appQR2 == survey.g_appQR_nr) {
               return survey.appQR2;
           }
       } else {
           return survey.appQR1;
       }
   } else if(survey.appQR1 != null) {
       if(survey.appQR1 == survey.g_appQR_nr) {
           return survey.appQR1;
       }
   } else if(survey.appQR2 != null) {
       if(survey.appQR2 == survey.g_appQR_nr) {
           return survey.appQR2;
       }
   } else {
       //We didn't get two source of corraborating info
       return null;
   }
}

function getGenericID(survey, qrField, manualField, devices) {
   //Attempt to parse the QR code
   var parsed_core_id = null;
   var parsed_shield_id = null;
   
   if(typeof survey[qrField] != 'undefined') {
      let ids = survey[qrField].split(':');
      if(ids.length == 3) {
         parsed_core_id = ids[1];
         parsed_shield_id = ids[2];
      } 
   }

   //Okay if we have a parsed core or shield ID, we should try looking that up
   //in the devices table
   if(parsed_core_id != null) {
       let ids = lookupCoreID(parsed_core_id, devices);

       if(ids != null) {
           return ids;
       } else {
           ids = lookupShieldID(parsed_shield_id, devices); 

           if(ids != null) {
               return ids;
           } else {
               ids = lookupShieldID(survey[manualField] + '0000', devices);

               if(ids != null) {
                   return ids;
               } else {
                   //There is nothing we can do here but give up
                   return null;
               }
           }
       }
   }
}

function getExitGiveDeviceID(survey, devices) {
   //g_deviceID_retrieve
   //deviceRetrieveQR

   return getGenericID(survey, 'deviceGiveQR', 'g_deviceID_give', devices);
}

function getExitRetrieveDeviceID(survey, devices) {
   //g_deviceID_retrieve
   //deviceRetrieveQR
   
   return getGenericID(survey, 'deviceRetrieveQR', 'g_deviceID_retrieve', devices);
}

function getEntryDeviceID(survey, devices) {
   //g_deviceID
   //deviceQR
   
   return getGenericID(survey, 'deviceQR', 'g_deviceID', devices);
}

function getEntryCoordinates(survey) {
   if(survey['g_gps_accurate-Accuracy'] != '') {
       return [survey['g_gps_accurate-Latitude'],survey['g_gps_accurate-Longitude']];
   } else if(survey['g_gps-Accuracy'] != '') {
       return [survey['g_gps-Latitude'],survey['g_gps-Longitude']];
   } else if(survey['g_gps-Accuracy'] != '') {
       return [survey['gps-Latitude'],survey['gps-Longitude']];
   } else {
      return null;
   } 
}

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
    var still_making_progress = false; 
    while(still_making_progress) {
       //This will be set to true when you deploy a device or remove a device
       still_making_progress = false; 
       
       var surveys_to_remove = [];
       for(let i = 0; i < entrySurveys.length; i++) {

           var device_info = null;
           var respondent_info = null;
           let surveySuccess = true;
           let respondent_id = entrySurveys[i].a_respid;
           console.log("Processing entry survey for respondent ", respondent_id);

           //Make sure that the R script didn't report an error for this survey
           if(typeof entrySurveys[i].error != 'undefined' && entrySurveys[i].error) {
               surveySuccess = false;
           }
            
           //Make sure that we need to process this survey
           if(entrySurveys[i].g_download == '0' && entrySurveys[i].g_install == '0') {
              //This survey did not result in an app download or a powerwatch install
              //Exiting
              console.log('No app install or powerwatch install. Skipping survey');
              surveys_to_remove.push(i);
              continue;
           } 


           //Okay, first, have we already processed an entry survey for
           //this respondent ID
           if(typeof respondents[respondent_id] != 'undefined') {
               //This is a duplicate
               surveySuccess = false;
               entrySurveys[i].error = true;
               entrySurveys[i].error_field = 'a_respid';
               entrySurveys[i].error_comment = 'Duplicate respondent ID of ' + respondents[respondent_id].entrySurveyID;
           }

           //Do we already have a respondent with this phone number?
           for(var key in respondents) {
               if(respondents[key].phoneNumber == entrySurveys[i].e_phonenumber) {
                   //This is a duplicate
                   surveySuccess = false;
                   entrySurveys[i].error = true;
                   entrySurveys[i].error_field = 'e_phonenumber';
                   entrySurveys[i].error_comment = 'Duplicate phone number of ' + respondents[key].entrySurveyID;
               }
           }

           //Okay this is a unique respondent with a unique phone number
           //Process the survey

           //Get the most accurate latitude and longitude possible
           var coords = getEntryCoordinates(entrySurveys[i]);
           if(coords == null) {
               surveySuccess = false;
               entrySurveys[i].error = true;
               entrySurveys[i].error_field = 'g_gps_accuracy';
               entrySurveys[i].error_comment = 'No valid GPS coordinates found';
           }
           
         
           respondent_info = {
               respondent_id: respondent_id,
               entrySurveyTime: entrySurveys[i].endtime,
               entrySurveyID: entrySurveys[i].instanceID,
               phoneNumber: entrySurveys[i].e_phonenumber,
               carrier: entrySurveys[i].e_carrier,
               entryLatitude: coords[0],
               entryLongitude: coords[1]
           };

           //Did this user download the app?
           if(entrySurveys[i].g_download == '1') {
               //Set the user to active
               respondent_info.currently_active = true;

               //extract the unique key presented by the app
               var appID = getAppID(entrySurveys[i]);
               if(appID == null) {
                   surveySuccess = false;
                   entrySurveys[i].error = true;
                   entrySurveys[i].error_field = 'g_appQR_nr';
                   entrySurveys[i].error_comment = 'Insufficient aggreement between app QR codes';
               } else {
                  respondent_info.appID = appID;
               }
           }
            
           //if there is a powerwatch device collect the same information about powerwatch
           if(entrySurveys.g_install == '1') {
              //Process all the IDs present in a survey and cross reference it  with the device table
              let ids = getEntryDeviceID(entrySurveys[i], device_table);
              if(ids == null) {
                  //This is an error, post the error
                  surveySuccess = false;
                  entrySurveys[i].error = true;
                  entrySurveys[i].error_field = 'g_deviceID';
                  entrySurveys[i].error_comment = 'Unkown or invalid device ID';
              } else {
                  let core_id = ids[0];
                  let shield_id = ids[1];

                  //Make sure this is is not a duplicate device
                  for(let j = 0; j < devices.length; j++) {
                     if(devices[j].core_id == core_id && devices[j].currently_deployed) {
                        //This devices is currently deployed
                        surveySuccess = false;
                        entrySurveys[i].error = true;
                        entrySurveys[i].error_field = 'g_deviceID';
                        entrySurveys[i].error_comment = 'Device already deployed';
                     }
                  }

                  device_info = {
                      respondent_id: respondent_id,
                      deploymentSurveyTime: entrySurveys[i].endtime,
                      deploymentSurveyID: entrySurveys[i].instanceID,
                      phoneNumber: entrySurveys[i].e_phonenumber,
                      carrier: entrySurveys[i].e_carrier,
                      currently_deployed: true,
                      entryLatitude: coords[0],
                      entryLongitude: coords[1],
                      core_id: core_id,
                      shield_id: shield_id,
                      installed_outage: entrySurveys[i].g_installoutage,
                      deployment_start_time: entrySurveys[i].endtime,
                  };
                  
                  //Update the respondent to say that they do have a powerwatch
                  respondent_info.powerwatch = true;
                  respondent_info.powerwatch_core_id = core_id;
                  respondent_info.powerwatch_shield_id = shield_id;
                  respondent_info.last_deployment_time = entrySurveys[i].endtime;
              }//end device IDs are valid
           }//end device was installed

           //If this survey was processed successfully, remove it from
           //the set of surveys to process
           if(surveySuccess) {
               still_making_progress = true;
               
               //Add the respondent to the respondent ID table
               respondents[respondent_id] = respondent_info;

               //Add this deployment to the table
               devices.push(device_info);
               
               //Add this index to the surveys to be removed
               surveys_to_remove.push(i);
           }
       } // end for loop

       //Actually remove the surveys
       for(let i = 0; i < surveys_to_remove.length; i++) {
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
               exitSurveys[i].error_comment = 'Unkown respondent id';
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
                  exitSurveys[i].error_comment = 'Unkown or invalid device ID';
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
                  exitSurveys[i].error_comment = 'Unkown or invalid device ID';
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
                     }
                  }

                  device_add_info = {
                      respondent_id: respondent_id,
                      deploymentSurveyTime: exitSurveys[i].endtime,
                      deploymentSurveyID: exitSurveys[i].instanceID,
                      phoneNumber: respondents[respondent_id].phoneNumber,
                      carrier: respondents[respondent_id].carrier,
                      currently_deployed: true,
                      entryLatitude: removed_device.entryLatitude,
                      entryLongitude: removed_device.entryLongitude,
                      core_id: core_id,
                      shield_id: shield_id,
                      installed_outage: removed_device.installed_outage,
                      deployment_start_time: exitSurveys[i].endtime,
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
               respondents[respondent_id].last_removal_time = device_removal_info.removal_time;

               //Was a device deployed
               if(device_add_info != null) {
                  devices.push(device_add_info);

                  //Update the respondent to say that they do have a powerwatch
                  respondents[respondent_id].powerwatch = true;
                  respondents[respondent_id].powerwatch_core_id = device_add_info.core_id;
                  respondents[respondent_id].powerwatch_shield_id = device_add_info.shield_id;
                  respondents[respondent_id].last_deployment_time = device_add_info.deployment_start_time;
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
   updateTrackingTables(respondents, devices, entrySurveys, exitSurveys);
}

function processSurveys(entrySurveys, exitSurveys) {
    //This should enter a powerwatch user into the postgres deployment table and the oink table
    
    // Parse out the QR codes
    extractQRCodes(entrySurveys, "g_deviceQR", "deviceQR", function(entrySurveys) {
        extractQRCodes(entrySurveys, "g_appQR_pic1", "appQR1", function(entrySurveys) {
            extractQRCodes(entrySurveys, "g_appQR_pic2", "appQR2", function(entrySurveys) {
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
    });
}

//function to fetch surveys from surveyCTO
function fetchSurveys(formid, callback) {

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
        fs.writeFile(formid + '.csv', body, function(err) {
            if(err) {
                console.log("Encountered file writing error, can't clean");
                return callback(null, "File writing error for cleaning");
            } else {
                //load the last file we cleaned
                var last_json = null;
                csv().fromFile(formid + '_cleaned.csv').then(function(json) {
                    console.log("loaded last json");
                    last_json = json;
                    //Clean the file using the rscript
                    exec('Rscript ' + formid + '.R ' + formid + '.csv ' + 
                                                       formid + '_cleaned.csv', 
                                             function(error, stdout, stderr) {

                        if(error) {
                            console.log(error, stderr);
                            return callback(null, false, "Error cleaning file with provided script");
                        } else {
                            csv().fromFile(formid + '_cleaned.csv').then(function(json) {
                                //compare json to last json
                                var differences = diff(last_json, json);
                                var changed = (typeof differences != 'undefined');
                                return callback(json, changed, null);
                            }, function(err) {
                                console.log("Error reading file");
                                return callback(null, false, err);
                            });
                        }
                    });
                }, function(err) {
                    console.log("error loading last json");
                    console.log(err);
                    last_json = null;

                    //Clean the file using the rscript
                    exec('Rscript ' + formid + '.R ' + formid + '.csv ' + 
                                    formid + '_cleaned.csv', 
                                    function(error, stdout, stderr) {

                        if(error) {
                            console.log(error, stderr);
                            return callback(null, false, "Error cleaning file with provided script");
                        } else {
                            csv().fromFile(formid + '_cleaned.csv').then(function(json) {
                                return callback(json, true, null);
                            }, function(err) {
                                console.log("Error reading file");
                                return callback(null, false, err);
                            });
                        }
                    });
                });
            }
        });
    });
}

//function to fetch surveys from surveyCTO
function fetchNewSurveys() {
    //fetch all surveys moving forward
    //send the API requests to surveyCTO - we probable also need attachments to process pictures
    fetchSurveys(survey_config.entrySurveyName, function(entrySurveys, entry_changed, err) {
        if(err) {
            console.log("Error fetching and processing forms");
            console.log(err);
            return;
        } else {
            fetchSurveys(survey_config.exitSurveyName, function(exitSurveys, exit_changed, err) {
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
fetchNewSurveys();

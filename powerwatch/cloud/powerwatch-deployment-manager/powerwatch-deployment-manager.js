#!/usr/bin/env node

const { Pool }  = require('pg');
var format = require('pg-format');
const request = require('request')
const fs = require('fs')
const jpeg = require('jpeg-js')
const jsQR = require('jsqr')
const { exec } = require('child_process')
const csv = require('csvtojson')
var async = require('async')
var diff = require('deep-diff')


//get the usernames and passwords necessary for this task
var command = require('commander');
command.option('-d, --database [database]', 'Database configuration file.')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file')
        .option('-U, --surveyusername [surveyusername]', 'SurveyCTO username file')
        .option('-P, --surveypassword [surveyusername]', 'SurveyCTO passowrd file').parse(process.argv)

var timescale_config = null; 
if(typeof command.database !== 'undefined') {
    timescale_config = require(command.database);
    timescale_config.username = fs.readFileSync(command.username,'utf8').trim()
    timescale_config.password = fs.readFileSync(command.password,'utf8').trim()
} else {
    //timescale_config = require('./postgres-config.json'); 
}

var survey_config = {};
if(typeof command.surveyusername !== 'undefined') {
    survey_config.username = fs.readFileSync(command.surveyusername,'utf8').trim()
    survey_config.password = fs.readFileSync(command.surveypassword,'utf8').trim()
} else {
    survey_config = require('./survey-config.json'); 
}

/*const pg_pool = new  Pool( {
    user: timescale_config.username,
    host: timescale_config.host,
    database: timescale_config.database,
    password: timescale_config.password,
    port: timescale_config.port,
    max: 20,
})*/

//given a list of particle or shield IDs, lookup in the database and see
//how many match and if they all match the same thing
function findValidID(idlist) {
    //TODO: query the devices table and see that some quorum of the IDs in the
    //list match a valid device. return device statistics if so, else return null
}

function powerwatchEntryHelper(survey) {
    console.log("Processing survey for respondent ",survey.a_respid)
    //Was a powerwatch device installed?
    powerwatch_installed = false
    if(survey.g_install == '1') {
        powerwatch_installed = true
        //yes -> put it in the deployment table
        //things we care about
        //respondent ID
        //location
        //installed during outage
        //time of install
        //device ID
        //phone number
        //the survey that entered this record
       
        //First try to determine the correct device ID and shield ID
        //g_deviceID, g_deviceID2, processedID
        id_list = []
        if('processedID' in survey) {
            var ids = suvery.processedID.split(':')
            if(ids.length != 3) {
                console.log("Not a valid QR code read - discarding")
            } else {
                id_list.push(ids[1])
                id_list.push(ids[2])
            }
        } else {
            console.log("No attached image or no valid QR found in image")
        }
        id_list.push(g_deviceID)
        id_list.push(g_deviceID2)

        var id = findValidID(idlist);
        var core_id = ""
        var shield_id = ""
        if(id == null) {
            //this is clearly an error and we should
            //TODO: write the logic to the error table
            return;
        } else {
            core_id = id[0]
            shield_id = id[1]
            //we have a valid ID and should be able to put this into
            //the deployment table

            //First query the deployment table and see if this device
            //is already in the deployed state
            pg_pool.query("SELECT deployment_time, end_time, FROM deployment where core_id = $1", [core_id]), (err, res) => {
                if(err) {
                    console.log("This is a weird error, the most that should happen is it returns no data")
                } else {
                    console.log(res.rows.length)
                    if(res.rows.length == 0) {
                        //TODO: This has never been deployed - insert it into table
                    } else {
                        // we need to loop through and see 1) if this has been deployed before has it ended
                        // 2) if it's currently deployed is that this survey
                        for(var i = 0; i < res.rows.length; i++) {
                            if(res.rows[i].end_time == null) {
                                //this has been deployed and that deployment isn't over
                                if(res.rows[i].deployment_time == survey.end_time) {
                                    //that is this survey - we don't need to insert this survey again
                                    return;
                                } else {
                                    //that's not this survey - this is probably an error. We should write it all
                                    //to the error table and maybe an endline survey will come through later
                                    
                                    //TODO: write the logic to the error table
                                    return;
                                }
                            } else {
                                //This deployment has ended and it's fine
                            }
                        }

                        //TODO: We didn't run into a conflic - insert it into the table
                    }

                }
            }
        }
    }

    //It doesn't matter if the app was downloaded
    //If the user consented then we should still incentivize them in OINK
    
    //Into the deployment table we want to p

    //form = {}
    //form.a_PW                      = json[i].a_PW,
    //form.g_install                 = json[i].g_install,
    //form.g_plugwatch               = json[i].g_plugwatch,
    //form.g_installoutage           = json[i].g_installoutage,
    //form.g_noinstall               = json[i].g_noinstall,
    //form.g_deviceID                = json[i].g_deviceID,
    //form.g_deviceID2               = json[i].g_deviceID2,
    //form.g_deviceQR                = json[i].g_deviceQR,
    //form.a_respid                  = json[i].a_respid,
    //form.g_download                = json[i].g_download,
    //form.g_nodownload              = json[i].g_nodownload,
    //form.g_nodownload_pic          = json[i].g_nodownload_pic,
    //form.g_dwnumber                = json[i].g_dwnumber,
    //form.g_nonumber                = json[i].g_nonumber,
    //form.g_imei1                   = json[i].g_imei1,
    //form.g_imei2                   = json[i].g_imei2,
    //form.e_phonenumber             = json[i].e_phonenumber,
    //form.g_imei3                   = json[i].g_imei3,
    //form.e_carrier                 = json[i].e_carrier,
    //form.formdef_version           = json[i].formdef_version, 
    //form.SubmissionDate            = json[i].SubmissionDate,
    //form.starttime                 = json[i].starttime,
    //form.endtime                   = json[i].endtime,
    //form['g_gps-Latitude']         = json[i]['g_gps-Latitude'],
    //form['g_gps-Longitude']           = json[i]['g_gps-Longitude'],
    //form['g_gps-Altitude']            = json[i]['g_gps-Altitude'],
    //form['g_gps-Accuracy']            = json[i]['g_gps-Accuracy'],
    //form['g_gps_accurate-Latitude']   = json[i]['g_gps_accurate-Latitude'],
    //form['g_gps_accurate-Longitude']  = json[i]['g_gps_accurate-Longitude'],
    //form['g_gps_accurate-Altitude']   = json[i]['g_gps_accurate-Altitude'],
    //form['g_gps_accurate-Accuracy']   = json[i]['g_gps_accurate-Accuracy'],
    //form['gps-Latitude']              = json[i]['gps-Latitude'],
    //form['gps-Longitude']             = json[i]['gps-Longitude'],
    //form['gps-Altitude']              = json[i]['gps-Altitude'],
    //form['gps-Accuracy']              = json[i]['gps-Accuracy'],
    //data.push(form)

}

//This is a recurive function that we could sub in if we get a lot of image 
//corruption. But's it's untested so let's leave it out for now
function handleRequestResponse(options, error, response, body, depth, callback) {
    //We are doing this recursively so limit the depth
    if(depth > 4) {
        return callback(null)
    }

    if(depth > 1) {
        console.log("Called with depth ", depth)
    }

    if(error) {
        console.log(error)
        request(options, (function() {
           return function(error, response, body) {
              //We should just try to process this image immediately with
              //the QR code processing code
              console.log("Got error response - calling handle Request recursively")
              handleRequestResponse(options, error, response, body, depth + 1, function(data) {
                  return callback(data)
              });
           }
        })())
    } else { 
       console.log(response.statusCode)
       var buf = new Buffer(body, "binary")
       try {
           var rawImage = jpeg.decode(buf, true);
           const code = jsQR(rawImage.data, rawImage.width, rawImage.height)
           if(code == null) {
              //There was no readable QR code here
              return callback(null)
           } else {
              //We have a QR code
              return callback(code.data)
           }
       } catch (error) {
           //we should just retry this request
           console.log(error)
           fs.writeFile("error.jpg", buf, function(err) {
           });
           
           request(options, (function() {
              return function(error, response, body) {
                 //We should just try to process this image immediately with
                 //the QR code processing code
                 console.log("Got error response - calling handle Request recursively")
                 handleRequestResponse(options, error, response, body, depth + 1, function(data) {
                     return callback(data)
                 });
              }
           })())
       }
    }
}


function extractQRCodes(survey, url_field, output_field, outer_callback) {
   
    // Why 2...well emperically surveyCTO doesn't like more??
    // You can easily start getting ECONNRESETS 
    async.forEachOfLimit(survey, 2, function(value, key, callback) {
        if(value[url_field] != '') {
            console.log(key)
            console.log(value[url_field])
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
           }

           request(options, (function() {
              return function(error, response, body) {
                 //We should just try to process this image immediately with
                 //the QR code processing code
                 
                 //Here's how you call the recursive function
                 console.log(key)
                 handleRequestResponse(options, error, response, body, 1, function(data) {
                     if(data) {
                          console.log(data)
                          survey[key][output_field] = data
                     }
                     callback()
                 });
              }
           })())

        } else {
            callback()
        }
    }, function(err) {
        if(err) {
            console.log("Some error with async");
        }
        console.log("All asyncs have returned");
        outer_callback(survey)
    });

}

function getEntryDeviceID(survey) {
}

function getEntryCoordinates(survey) {
   if(survey['g_gps_accurate-Accuracy'] != '') {
       return [survey['g_gps_accurate-Latitude'],survey['g_gps_accurate-Longitude']]
   } else if(survey['g_gps-Accuracy'] != '') {
       return [survey['g_gps-Latitude'],survey['g_gps-Longitude']]
   } else {
       return [survey['gps-Latitude'],survey['gps-Longitude']]
   }
}


function generateTrackingTables(entrySurveys, exitSurveys) {
    //Okay the high level idea here is to generate a json blob describing
    //the entire deployment from the surveys. We actually want two - a respondent-centric
    //set and device-centric set
    //
    //To do that the method will be loop through entry Surveys
    //When you find a valid device deployment create entries for the respondent
    //and entries for the device
    //Then loop through the exit surveys trying to find a pickup for that
    //device and that respondent ID
    //Surveys that look erroneous get flagged
    //Devices that look erroneous get flagged
    //and It all can be cleaned up by modifying the R script
    devices = []
    respondents = []
    for(var i = 0; i < entrySurveys.length; i++) {
        id = entrySurvey[i].a_respid
        console.log("Processing survey for respondent ", id)
        

        //Get the most accurate latitude and longitude possible
        coords = getEntryCoordinates(entrySurveys[i])

        //First collect information about the respondent indexed by respondent ID
        respondents[id] =  {
            id: id,
            entrySurveyTime: entrySurvey[i].starttime,
            entrySurveyID: entrySurvey[i].instanceID,
            phoneNumber: entrySurvey[i].e_phonenumber,
            carrier: entrySurvey[i].e_carrier,
            currently_active: true,
            entryLatitude: entrySurvey[i]['g_gps-Latitude'],
            entryLongitude: entrySurvey[i]['g_gps-Longitude'],
        }
    //form.a_PW                      = json[i].a_PW,
    //form.g_install                 = json[i].g_install,
    //form.g_plugwatch               = json[i].g_plugwatch,
    //form.g_installoutage           = json[i].g_installoutage,
    //form.g_noinstall               = json[i].g_noinstall,
    //form.g_deviceID                = json[i].g_deviceID,
    //form.g_deviceID2               = json[i].g_deviceID2,
    //form.g_deviceQR                = json[i].g_deviceQR,
    //form.a_respid                  = json[i].a_respid,
    //form.g_download                = json[i].g_download,
    //form.g_nodownload              = json[i].g_nodownload,
    //form.g_nodownload_pic          = json[i].g_nodownload_pic,
    //form.g_dwnumber                = json[i].g_dwnumber,
    //form.g_nonumber                = json[i].g_nonumber,
    //form.g_imei1                   = json[i].g_imei1,
    //form.g_imei2                   = json[i].g_imei2,
    //form.e_phonenumber             = json[i].e_phonenumber,
    //form.g_imei3                   = json[i].g_imei3,
    //form.e_carrier                 = json[i].e_carrier,
    //form.formdef_version           = json[i].formdef_version, 
    //form.starttime                 = json[i].starttime,

        //Was a powerwatch device installed?
        powerwatch_installed = false
        if(survey.g_install == '1') {
            powerwatch_installed = true
            //yes -> put it in the deployment table
            //things we care about
            //respondent ID
            //location
            //installed during outage
            //time of install
            //device ID
            //phone number
            //the survey that entered this record
           
            //First try to determine the correct device ID and shield ID
            //g_deviceID, g_deviceID2, processedID
            id_list = []
            if('processedID' in survey) {
                var ids = suvery.processedID.split(':')
                if(ids.length != 3) {
                    console.log("Not a valid QR code read - discarding")
                } else {
                    id_list.push(ids[1])
                    id_list.push(ids[2])
                }
            } else {
                console.log("No attached image or no valid QR found in image")
            }
            id_list.push(g_deviceID)
            id_list.push(g_deviceID2)

            var id = findValidID(idlist);
            var core_id = ""
            var shield_id = ""
            if(id == null) {
                //this is clearly an error and we should
                //TODO: write the logic to the error table
                entrySurveys[i].result = "Error"
                entrySurveys[i].resultReason = "Could not get valid powerwatch ID"
                return;
            } else {
                core_id = id[0]
                shield_id = id[1]


         

    }
}

function processSurveys(entrySurveys, exitSurveys) {
    //This should enter a powerwatch user into the postgres deployment table and the oink table
    
    // Parse out the QR codes
    extractQRCodes(entrySurveys, "g_deviceQR", "processedID", function(entrySurveys) {
        extractQRCodes(exitSurveys, "pluggedin_image", "followupID", function(exitSurveys) {
            //Okay we should not have completely processed entry and exit surveys
            generateTrackingTables(entrySurveys, exitSurveys)
        });
    });
}

//function to fetch surveys from surveyCTO
function fetchSurveys(formid, callback) {

    //fetch all surveys from the start of time - we prevent double writing anyways
    uri = 'https://gridwatch.surveycto.com/api/v1/forms/data/wide/csv/'
                                       + formid
                                       +'?r=approved|rejected|pending'

    var options = {
        uri: uri,
        auth: {
            user: survey_config.username,
            pass: survey_config.password,
            sendImmediately: false
        }
    }
    data = []
    num_images = 0
    request(options, function(error, response, body) {
        //Okay now we should write this out to a file and call the cleaning script 
        //on it
        fs.writeFile(formid + '.csv', body, function(err) {
            if(err) {
                console.log("Encountered file writing error, can't clean")
                return callback(null, "File writing error for cleaning")
            } else {
                //load the last file we cleaned
                last_json = null
                csv().fromFile(formid + '_cleaned.csv').then(function(json) {
                    console.log("loaded last json")
                    last_json = json
                    //Clean the file using the rscript
                    exec('Rscript ' + formid + '.R ' 
                                    + formid + '.csv ' 
                                    + formid + '_cleaned.csv', 
                                    function(error, stdout, stderr) {

                        if(error) {
                            console.log(error, stderr)
                            return callback(null, false, "Error cleaning file with provided script")
                        } else {
                            csv().fromFile(formid + '_cleaned.csv').then(function(json) {
                                //compare json to last json
                                var differences = diff(last_json, json);
                                var changed = (typeof differences != 'undefined')
                                return callback(json, changed, null)
                            }, function(err) {
                                console.log("Error reading file")
                                return callback(null, false, err);
                            });
                        }
                    });
                }, function(err) {
                    console.log("error loading last json")
                    console.log(err)
                    last_json = null

                    //Clean the file using the rscript
                    exec('Rscript ' + formid + '.R ' 
                                    + formid + '.csv ' 
                                    + formid + '_cleaned.csv', 
                                    function(error, stdout, stderr) {

                        if(error) {
                            console.log(error, stderr)
                            return callback(null, false, "Error cleaning file with provided script")
                        } else {
                            csv().fromFile(formid + '_cleaned.csv').then(function(json) {
                                return callback(json, true, null)
                            }, function(err) {
                                console.log("Error reading file")
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
    fetchSurveys('Combined_Form', function(entrySurveys, entry_changed, err) {
        if(err) {
            console.log("Error fetching and processing forms")
            console.log(err);
            return
        } else {
            fetchSurveys('PW_Device_followup', function(exitSurveys, exit_changed, err) {
                if(err) {
                    console.log("Error fetching and processing forms")
                    console.log(err);
                    return
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

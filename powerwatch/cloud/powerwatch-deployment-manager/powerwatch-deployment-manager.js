#!/usr/bin/env node

const { Pool }  = require('pg');
var format = require('pg-format');
const request = require('request')
const fs = require('fs')
const jpeg = require('jpeg-js')
const jsQR = require('jsqr')


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

const pg_pool = new  Pool( {
    user: timescale_config.username,
    host: timescale_config.host,
    database: timescale_config.database,
    password: timescale_config.password,
    port: timescale_config.port,
    max: 20,
})

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

function processPowerwatchEntrySurvey(survey) {
    //This should enter a powerwatch user into the postgres deployment table and the oink table
    //parse out the picture if it exists
    if(survey.g_deviceQR != '') {
       var options = {
          uri: survey.g_deviceQR,
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

       console.log(survey.g_deviceQR)

       request(options, (function() {
          return function(error, response, body) {
             //We should just try to process this image immediately with
             //the QR code processing code
             var buf = new Buffer(body, "binary")
                var rawImage = jpeg.decode(buf, true);
             const code = jsQR(rawImage.data, rawImage.width, rawImage.height)
             if(code == null) {
                //There was no readable QR code here
                powerwatchEntryHelper(survey)
             } else {
                //We have a QR code
                survey.processedID = code.data
                powerwatchEntryHelper(survey)
             }
          }
       })())
    } else {
      powerwatchEntryHelper(survey)
    }
}

function processPowerwatchExitSurvey(survey) {
    //This should enter a line into the powerwatch deployment stating removed
}

function processAppEntrySurvey(survey) {
    //This should enter a line into the oink table
}

function processAppExitSurvey(survey) {
    //I don't think this really exists yet
}

//function to fetch surveys from surveyCTO
function fetchSurveys(formid, date, callback) {
    //fetch all surveys from the start of time - we prevent double writing anyways
    var uri = ""
    if(date == 0) {
        uri = 'https://gridwatch.surveycto.com/api/v1/forms/data/wide/json/'
                                           + formid
                                           +'?r=approved|rejected|pending'
    } else {
        uri = 'https://gridwatch.surveycto.com/api/v1/forms/data/wide/json/'
                                           + formid
                                           + '?r=approved|rejected|pending'
                                           + '?date=' + String(date)
    }

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
        json = JSON.parse(body)
        return callback(json)
    })
}

//function to fetch surveys from surveyCTO
var lastSurveyFetch = 0
function fetchNewSurveys() {
    //fetch all surveys moving forward
    //send the API requests to surveyCTO - we probable also need attachments to process pictures
    fetchSurveys('Combined_Form', lastSurveyFetch, function(data) {
       for(var i = 0; i < data.length; i++) {
          processPowerwatchEntrySurvey(data[i])
       }
    })

    //update last survey fetch
    lastSurveyFetch = Date.now()
}

//Periodically query surveyCTO for new surveys - if you get new surveys processing them on by one
///setInterval(fetchNewSurveys, 600000)
fetchNewSurveys();

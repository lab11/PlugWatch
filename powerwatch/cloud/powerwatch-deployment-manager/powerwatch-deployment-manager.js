#!/usr/bin/env node

const { Pool }  = require('pg');
var format      = require('pg-format');
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

/*const pg_pool = new  Pool( {
    user: timescale_config.username,
    host: timescale_config.host,
    database: timescale_config.database,
    password: timescale_config.password,
    port: timescale_config.port,
    max: 20,
})*/

function powerwatchEntryHelper(survey) {
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

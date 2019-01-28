#!/usr/bin/env node

const { Pool }  = require('pg');
var format      = require('pg-format');


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
    timescale_config = require('./postgres-config.json'); 
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

function processPowerwatchEntrySurvey(survey) {
    //This should enter a powerwatch user into the postgres deployment table and the oink table
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

//function to fetch surveys from surveyCTO
var lastSurveyFetch = 0
function fetchNewSurveys() {
   if(lastSurveyFetch = 0) {
       //fetch all surveys from the start of time - we prevent double writing anyways
   } else {
       //update last survey fetch
       lastSurveyFetch = Date.now()

       //fetch all surveys moving forward
       //send the API requests to surveyCTO - we probable also need attachments to process pictures
   }
}

//Periodically query surveyCTO for new surveys - if you get new surveys processing them on by one
setInterval(fetchNewSurveys, 600000)

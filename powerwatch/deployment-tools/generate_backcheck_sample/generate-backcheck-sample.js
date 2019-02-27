#!/usr/bin/env node

const request = require('request');
const fs = require('fs');
const { exec } = require('child_process');
const csv = require('csvtojson');
var async = require('async');
const git  = require('isomorphic-git');
   

//get the usernames and passwords necessary for this task
var command = require('commander');
command.option('-d, --database [database]', 'Database configuration file.')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file')
        .option('-s, --survey [survey]', 'Survey configuration file')
        .option('-U, --surveyusername [surveyusername]', 'SurveyCTO username file')
        .option('-P, --surveypassword [surveypassword]', 'SurveyCTO passowrd file')
        .option('-o, --oink [oink]', 'OINK configuration file')
        .option('-a, --service_account [oink_service_account]', 'OINK service account file').parse(process.argv);

var survey_config = {};
if(typeof command.surveyusername !== 'undefined') {
    survey_config = require(command.survey);
    survey_config.username = fs.readFileSync(command.surveyusername,'utf8').trim();
    survey_config.password = fs.readFileSync(command.surveypassword,'utf8').trim();
} else {
    survey_config = require('./survey-config.json');
}

//function to fetch surveys from surveyCTO
function fetchSurveys(formid, cleaning_path, callback) {

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
                        //Clean the file using the rscript
                        exec('Rscript ' + cleaning_path + ' ' + path + formid + '.csv ' +
                                        path + formid + '_cleaned.csv',
                                        function(error, stdout, stderr) {

                            if(error) {
                                console.log(error, stderr);
                                return callback(null, false, "Error cleaning file with provided script");
                            } else {
                                csv().fromFile(path + formid + '_cleaned.csv').then(function(json) {
                                    return callback(json, false, err);
                                    console.log("Proceeding assuming changes.");
                                }, function(err) {
                                    console.log("Error reading file");
                                    return callback(null, false, err);
                                });
                            }
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

var seed = 1;
function random() {
    var x = Math.sin(seed++) * 10000;
    return x - Math.floor(x);
}

//function to fetch surveys from surveyCTO
function fetchNewSurveys() {
    //fetch all surveys moving forward
    //send the API requests to surveyCTO - we probable also need attachments to process pictures
    fetchSurveys(survey_config.entrySurveyName, survey_config.entryCleaningPath, function(entrySurveys, entry_changed, err) {
        if(err) {
            console.log("Error fetching and processing forms");
            console.log(err);
            return;
        } else {
            //Okay we should be able to use these cleaned surveys to generate a unique set
            console.log("Ready to process");
            
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
            for(let i = 0; i < entrySurveys.length; i++) {
                if(array.includes(i)) {
                    console.log(i,entrySurveys[i].a_respid, list);
                    list ^= 1;
                } 
            }
        }
    });
}

//Call it once to start
pullGitRepo(survey_config.gitRepoURL, survey_config.gitRepoPath, function(err) {
    if(err) {
        console.log('Error pulling git repo');
    } else {
        fetchNewSurveys();
    }
});

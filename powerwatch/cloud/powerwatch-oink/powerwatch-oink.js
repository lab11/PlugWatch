#!/usr/bin/env node

const { Pool }  = require('pg');
var format = require('pg-format');
const request = require('request');
const sortObj = require('sort-object');
const fs = require('fs');
var async = require('async');
const crypto = require('crypto');
var bodyParser = require("body-parser");
var http = require('http');


//get the usernames and passwords necessary for this task
var command = require('commander');
command.option('-d, --database [database]', 'Database configuration file.')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file')
        .option('-s, --secret [secret]', 'Korba secret key')
        .option('-c, --client [client]', 'Korba client key').parse(process.argv);

var timescale_config = null;
if(typeof command.database !== 'undefined') {
    timescale_config = require(command.database);
    timescale_config.username = fs.readFileSync(command.username,'utf8').trim();
    timescale_config.password = fs.readFileSync(command.password,'utf8').trim();
} else {
    timescale_config = require('./postgres-config.json');
}

var korba_config = null;
if(typeof command.secret !== 'undefined') {
    korba_config.secret_key = fs.readFileSync(command.secret,'utf8').trim();
    korba_config.client_key = fs.readFileSync(command.client,'utf8').trim();
} else {
    korba_config = require('./korba-config.json');
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


//an incentive function is a lambda function that returns a list of amount, reason pairs
//these get inserted into the specified payment table if they don't already
//exit
//incentive functions are stateless, duplicate transactions are prevented by
//this function for the same user and reason string. Reason strings should therefore NOT contain
//randomness
function incentivize_users(incentive_function, column_arguments, user_table, payment_table, callback) {
    cols = ""
    for(let i = 0; i < column_arguments.length; i++) {
        if(i == 0) {
            cols += "%I "
        } else {
            cols += ", %I"
        }
    }

    column_arguments.push(user_table);

    var qstring = format.withArray('SELECT respondent_id, ' + cols + ' FROM %I', column_arguments);
    console.log(qstring)
    pg_pool.query(qstring, (err, res) => {
        if(err) {
            //the column arguments were probably wrong
            console.log("Error getting information for incentive function");
        } else {
            var payments = []
            for(let i = 0; i < res.rows.length; i++) {
                pay = incentive_function(res.rows[i])
                for(let j = 0; j < pay.length; j++) {
                    pay[j].respondent_id = res.rows[i].respondent_id;
                }
                payments = payments.concat(pay);
            }

            //okay now add those payments to the payments table if
            //they don't exist yet
            console.log(payments)
            async.forEachLimit(payments, 1, function(item, callback) {
                //query for the respondent id
                res_id = (item.respondent_id).toString()
                var qstring = format.withArray("SELECT phone_number, carrier FROM %I WHERE respondent_id = %L",[user_table, res_id]);
                console.log(qstring);
                pg_pool.query(qstring, (err, res) => {
                    if(err) {
                        //Error finding user
                        console.log("Error retrieving phone number and carrier of user");
                        console.log(err)
                    } else {
                        // great, we have the information we need to insert into the payments table
                        // if it doesn't exist
                        var qstring = format.withArray('INSERT INTO %I (phone_number, carrier, respondent_id, time_created, amount, incentive_id, incentive_type, payment_attempt, status, external_transaction_id) VALUES (%L, %L, %L, NOW(), %L, %L, %L, %L, %L, %L) ON CONFLICT (external_transaction_id) DO NOTHING', [payment_table, res.rows[0].phone_number, res.rows[0].carrier, item.respondent_id, item.amount, item.incentive_id, item.incentive_type, 1, 'waiting', [res.rows[0].phone_number, res.rows[0].carrier, item.incentive_type, item.incentive_id, 1].join('-')]);
                        console.log(qstring);
                        pg_pool.query(qstring, (err, res) => {
                            if(err) {
                                //catch this
                                console.log('Error inserting into payments table');
                                console.log(err)
                            } else{
                                //great insert done
                                console.log("successful insert");
                                callback();
                            }
                        });
                    }
                });
            }, function(err) {
                callback(err); 
            });
        }
    });
}

function check_korba_status(external_transaction_id, callback) {
    console.log("Checking korba status")

    const jsonInfo = {
        "transaction_id": external_transaction_id,
        "client_id": 14
    }

    bodySorted = sortObj(jsonInfo)
    emptyArray = []
    for (var key in bodySorted) {
      if (bodySorted.hasOwnProperty(key)) {
        emptyArray.push(key + "=" + bodySorted[key])
      }
    }
    newArray = emptyArray.join('&');

    // Getting the HMAC SHA-256 digest
    const secret_key = korba_config.secret_key;
    const client_key = korba_config.client_key;

    const hash = crypto.createHmac('sha256', secret_key)
      .update(newArray) //This is the msg
      .digest('hex');
    request({
      uri: 'https://xchange.korbaweb.com/api/v1.0/transaction_status/',
      method: 'POST',
      headers:{
        'Content-Type':'application/json',
        'Authorization':`HMAC ${client_key}:${hash}`
      },
      json: true,
      body: bodySorted,
      resolveWithFullResponse: true,
    },function(error, response, bodyKorba){
      console.log('error: ', error);
      console.log('statusCode: ', response.statusCode);
      console.log('response: ', response);
      console.log(bodyKorba);
      callback(error, response, bodyKorba);
    });
}

//do the korba thing
function send_to_korba(phone_number, carrier, amount, external_transaction_id, callback) {
    console.log("sending to korba");

    //Add phone zero to phone number
    if (phone_number.length == 9) {
        phone_number = '0' + phone_number;
    }

    // Korba API has specific "codes" it expects
    var network_code = carrier;
    if (network_code.toLowerCase() == 'airtel') {
        network_code = 'AIR';
    } else if (network_code.toLowerCase() == 'mtn') {
        network_code = 'MTN';
    } else if (network_code.toLowerCase() == 'tigo') {
        network_code = 'TIG';
    } else if (network_code.toLowerCase() == 'glo') {
        network_code = 'GLO';
    } else if (network_code.toLowerCase() == 'vodafone' || network_code.toLowerCase() == 'vodaphone') {
        network_code = 'VOD';
    } else {
        console.error(`'Unknown network_code "${network_code}" will almost certainly fail at Korba'`);
    }

    const jsonInfo = {
        "customer_number": phone_number,
        "amount": amount,
        "transaction_id": external_transaction_id,
        "network_code": network_code,
        "callback_url": "https://pgweb.powerwatch.io",
        "description": "",
        "client_id": 14
    }

    bodySorted = sortObj(jsonInfo)
    emptyArray = []
    for (var key in bodySorted) {
      if (bodySorted.hasOwnProperty(key)) {
        emptyArray.push(key + "=" + bodySorted[key])
      }
    }
    newArray = emptyArray.join('&');

    // Getting the HMAC SHA-256 digest
    const secret_key = korba_config.secret_key;
    const client_key = korba_config.client_key;

    const hash = crypto.createHmac('sha256', secret_key)
      .update(newArray) //This is the msg
      .digest('hex');
    request({
      uri: 'https://xchange.korbaweb.com/api/v1.0/topup/',
      method: 'POST',
      headers:{
        'Content-Type':'application/json',
        'Authorization':`HMAC ${client_key}:${hash}`
      },
      json: true,
      body: bodySorted,
      resolveWithFullResponse: true,
    },function(error, response, bodyKorba){
      console.log('error: ', error);
      console.log('statusCode: ', response.statusCode);
      console.log(bodyKorba);
      if(error) {
          callback(error);
      } else {
          callback(response.statusCode);
      }
    });
}

function issue_payments(payments_table, callback) {
    //take payments in the waiting state added above and send them to Korba
    //change them to pending
    var qstring = format.withArray("SELECT phone_number, carrier, amount, external_transaction_id from %I WHERE status = 'waiting'", [payments_table]);
    console.log(qstring);
    pg_pool.query(qstring, (err, res) => {
        if(err) {
            console.log("Error getting waiting payments:",err);
        } else {
            async.forEachLimit(res.rows, 1, function(row, callback) {
                //for each row
                //send it to korba
                send_to_korba(row.phone_number, row.carrier, row.amount, row.external_transaction_id, function(result) {
                    //update it's state
                    var qstring = format.withArray("UPDATE %I SET status = 'pending', time_submitted = NOW() " + 
                                  "WHERE external_transaction_id = %L",[payments_table, row.external_transaction_id]);
                    console.log(qstring);
                    pg_pool.query(qstring, (err, res) => {
                        if(err) {
                            console.log("error updating table to pending", err);
                            callback(err)
                        } else {
                            console.log("successfully updated table");
                            callback();
                        }
                    });
                });
            }, function(err) {
                console.log("done with async calls");
                callback(err)
            });
        }
    });
}

function retry_payment(payments_table, external_transaction_id, callback) {
    //get the current payment number
    pg_pool.query('SELECT * from %I WHERE external_transaction_id = %s', [payments_table, external_transaction_id], (err, res) => {
        if(err) {
            callback(err);
        } else {
            attempt = res.row[0].payment_attempt;
            if(attempt > 3) {
                //we shouldn't try again
                console.log("Payment retried too many times, not retrying");
                callback();
            } else {
                attempt += 1;
                new_transaction_id = external_transaction_id.split('-')
                new_transaction_id[new_transaction_id.length-1] = attempt
                new_transaction_id = new_transaction_id.join('-');
                
                var qstring = format.withArray('INSERT INTO %I (phone_number, carrier, respondent_id, time_created, amount, incentive_id, incentive_type, payment_attempt, status, external_transaction_id) VALUES (%L, %L, %L, NOW(), %L, %L, %L, %L, %L, %L) ON CONFLICT (external_transaction_id) DO NOTHING', [payment_table, res.rows[0].phone_number, res.rows[0].carrier, item[0].respondent_id, item[0].amount, item[0].incentive_id, item[0].incentive_type, attempnt, 'waiting', new_transaction_id]);
                console.log(qstring);
                pg_pool.query(qstring, (err, res) => {
                    if(err) {
                        console.log("error inserting retry");
                        callback(err);
                    } else {
                        console.log("Successfully inserted retry");
                        callback();
                    }
                });
            }
        }
    });
}

function update_payment_status(payments_table, transaction_id, new_status, callback) {
    pg_pool.query("UPDATE %I SET status = %s WHERE external_transaction_id = %s",[payments_table, new_status, transaction_id], (err, res) => {
        if(err) {
            console.log("error updating table to retried", err);
            callback(err);
        } else {
            callback();
        }
    });
}

function check_status(payments_table) {
    //take payments in the pending state and query korba for their status
    //update it if necessary
    //IF successful send a text message to the user
    //If failed generate a new payment with the attempt number incremented
    pg_pool.query("SELECT external_transaction_id from %I WHERE status = 'pending'", [payments_table], (err, res) => {
        if(err) {
            console.log("Error getting pending payments", err);
        } else {
            async.forEachLimit(res.rows, 1, function(row, callback) {
                //for each row
                //send it to korba
                check_korba_status
                check_korba_status(row[0].external_transaction_id, function(error, result, body) {
                    //update it's state
                    if(error) {
                        callback(error);
                    } else if ('success' in body && body['success'] == false) {
                        if(body['error_code'] == 421) {
                            //transaction does not exist - our original request failed
                            //generate a new korba payment for this payment ID and try again
                            update_payment_status(payments_table, 'error', row[0].external_transaction_id, function(err) {
                                if(err) {
                                    console.log("Error updating status table", err)
                                    callback(err);
                                } else {
                                    console.log("retrying payment");
                                    retry_payment(payments_table, row[0].external_trasaction_id, function(err) {
                                        if(err) {
                                            callback(err)
                                        } else {
                                            callback();
                                        }
                                    });
                                }
                            });
                        } else {
                            //we don't know about this error
                            console.log("Unkown error with korba status check",error, result, body);
                            callback(error);
                        }
                    } else if ('success' in body && body['success'] == true) {
                        if(body['status'] == 'success') {
                            //transaction is complete
                            //update the database
                            update_payment_status(payments_table, 'complete', row[0].external_transaction_id, function(err) {
                                if(err) {
                                    console.log("error updating table to complete");
                                    callback(err);
                                } else {
                                    console.log("successfully updated table");
                                    //send a text message
                                }
                            });
                        } else if(body['status'] == 'pending') {
                            //for now just wait on this
                            //TODO add a pending timeout where we retry
                        } else if(body['status'] == 'failed') {
                            //update it to failed and try again
                            update_payment_status(payments_table, 'failed', row[0].external_transaction_id, function(err) {
                                if(err) {
                                    console.log("error updating table to retried");
                                    callback(err);
                                } else {
                                    console.log("retrying the payment");
                                    retry_payment(payments_table, row[0].external_trasaction_id, function(err) {
                                        if(err) {
                                            callback(err)
                                        } else {
                                            callback();
                                        }
                                    });
                                }
                            });
                        } else {
                            console.log("Error parsing korba status code");
                            callback(body);
                        }
                    } else {
                        console.log("Error parsing korba status code")
                        callback(body);
                    }
            }, function(err) {
                //some error with async
                if(err) {
                    console.log("Error updating transaction status");
                } else {
                    console.log("Done updating transaction status")
                }
            });
        });
        }
    });
}

function send_sms() {
    //send sms to the user
}

//will incentivize every user for complianceApp-30 for 4 Cedis
function complianceApp(args) {
    compliance_list = [];

    if(args.currently_active == true) {
        //calculate the number of days between deployment and now
        days = ((((Date.now() - args.pilot_survey_time)/1000)/3600)/24)
        compliances_to_issue = Math.floor(days/30);
        for(let i = 0; i < compliances_to_issue; i++) {
            var obj = {};
            obj.amount = 4;
            obj.incentive_type = 'complianceApp';
            obj.incentive_id = (i+1)*30;
            compliance_list.push(obj);
        }
    }

    return compliance_list;
}

//will incentivize every user for complianceApp-30 for 4 Cedis
function compliancePowerwatch(args) {
    compliance_list = [];

    if(args.powerwatch == true) {
        //calculate the number of days between deployment and now
        days = ((((Date.now() - args.pilot_survey_time)/1000)/3600)/24)
        compliances_to_issue = Math.floor(days/30);
        for(let i = 0; i < compliances_to_issue; i++) {
            var obj = {};
            obj.amount = 5;
            obj.incentive_type = 'compliancePowerwatch';
            obj.incentive_id = (i+1)*30;
            compliance_list.push(obj);
        }
    }

    return compliance_list;
}

//flow
// 1) check pending paymetns
// 2) create new incentives
// 3) issue new payments (and retry as necessary)

incentivize_users(complianceApp, ['pilot_survey_time', 'currently_active'], 'respondents', 'payments', function(err) {
    if(err) {
        console.log("Error incentivizing App users:", err);
    } else {
        incentivize_users(compliancePowerwatch, ['pilot_survey_time', 'powerwatch'], 'respondents', 'payments', function(err) {
            if(err) {
                console.log("Error incentivizing powerwatch users:", err);
            } else {
                /*issue_payments('payments', function(err) {
                    console.log("Error issuing payments", err); 
                });*/
            }
        });
    }
});

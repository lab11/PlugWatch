#!/usr/bin/env node

const { Pool }  = require('pg');
var format = require('pg-format');
const request = require('request');
const fs = require('fs');
var async = require('async');


//get the usernames and passwords necessary for this task
var command = require('commander');
command.option('-d, --database [database]', 'Database configuration file.')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file').parse(process.argv);

var timescale_config = null;
if(typeof command.database !== 'undefined') {
    timescale_config = require(command.database);
    timescale_config.username = fs.readFileSync(command.username,'utf8').trim();
    timescale_config.password = fs.readFileSync(command.password,'utf8').trim();
} else {
    timescale_config = require('./postgres-config.json');
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
function incentivize_users(incentive_function, column_arguments, user_table, payment_table) {
    cols = ""
    for(let i = 0; i < column_arguments.length; i++) {
        if(i == 0) {
            cols += "%I "
        } else {
            cols += ", %I"
        }
    }

    column_arguments.push(user_table);

    var qstring = format.withArray('SELECT respondent_id ' + cols + ' FROM %I', column_arguments);
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
                payments.push(pay);
            }

            //okay now add those payments to the payments table if
            //they don't exist yet
            async.forEachLimit(payments, 1, function(item, callback) {
                //query for the respondent id
                res_id = (item[0].respondent_id).toString()
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
                        var qstring = format.withArray('INSERT INTO %I (phone_number, carrier, respondent_id, time_created, amount, incentive_id, incentive_type, payment_attempt, status, external_transaction_id) VALUES (%L, %L, %L, NOW(), %L, %L, %L, %L, %L, %L) ON CONFLICT (external_transaction_id) DO NOTHING', [payment_table, res.rows[0].phone_number, res.rows[0].carrier, item[0].respondent_id, item[0].amount, item[0].incentive_id, item[0].incentive_type, 1, 'waiting', [res.rows[0].phone_number, res.rows[0].carrier, item[0].incentive_type, item[0].incentive_id, 1].join('-')]);
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
                console.log('Error with async');
            });
        }
    });
}

//do the korba thing
function send_to_korba(phone_number, carrier, external_transaction_id, callback) {
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
    } else if (network_code.toLowerCase() == 'vodafone') {
        network_code = 'VOD';
    } else {
        console.error(`'Unknown network_code "${network_code}" will almost certainly fail at Korba'`);
    }

    const jsonInfo = {
        "customer_number": phone_number,
        "amount": amount,
        "transaction_id": external_transaction_id,
        "network_code": network_code,
        "callback_url": null,
        "description": null,
        "client_id": 14
    }

    bodySorted = sortObj(jsonInfo)
    emptyArray = []
    for (var key in bodySorted) {
      if (bodySorted.hasOwnProperty(key)) {
        //console.log(key + "=" + newLocalSorted[key]);
        emptyArray.push(key + "=" + bodySorted[key])
      }
    }
    newArray = emptyArray.join('&');

    // Getting the HMAC SHA-256 digest
    const secret_key = config.korba.secret_key;
    const client_key = config.korba.client_key;

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
      console.log('response: ', response);
      console.log(bodyKorba);
      if(error) {
          callback(error);
      } else {
          callback(statusCode);
      }
    });
}

function issue_payments(payments_table) {
    //take payments in the waiting state added above and send them to Korba
    //change them to pending
    pg_pool.query('SELECT phone_number, carrier, external_transaction_id from %I WHERE status = waiting', [payments_table], (err, res) => {
        if(err) {
            console.log("Error getting waiting payments");
        } else {
            async.forEachLimit(res.rows, 1, function(row) {
                //for each row
                //send it to korba
                send_to_korba(row.phone_number, row.carrier, row.external_transaction_id, function(result) {
                    //update it's state
                    pg_pool.query('UPDATE %I SET status = pending WHERE external_transaction_id = %s',[payments_table, row.external_transaction_id], (err, res) => {
                        if(err) {
                            console.log("error updating table to pending");
                        } else {
                            console.log("successfully updated table");
                        }
                    });
                });
            }, function(err) {
                //some error with async
                console.log("Error sending transactions to korba async");
            });
        }
    });
}

function check_status() {
    //take payments in the pending state and query korba for their status
    //update it if necessary
    //IF successful send a text message to the user
    //If failed generate a new payment with the attempt number incremented
}

function send_sms() {
    //send sms to the user
}

//will incentivize every user for complianceApp-30 for 4 Cedis
function compliance_incentive_30_days() {
    var obj = {};
    obj.incentive_type = 'complianceApp';
    obj.incentive_id = '30';
    obj.amount = 4;
    return [obj];
}

incentivize_users(compliance_incentive_30_days, [], 'achimota_respondents', 'achimota_payments');

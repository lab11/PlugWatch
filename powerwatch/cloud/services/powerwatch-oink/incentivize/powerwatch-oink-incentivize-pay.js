#!/usr/bin/env node

const { Pool }  = require('pg');
var format = require('pg-format');
const request = require('request');
const sortObj = require('sort-object');
const fs = require('fs');
const engagespark = require('engagespark-topup');
var async = require('async');
const crypto = require('crypto');
var bodyParser = require("body-parser");

//get the usernames and passwords necessary for this task
var command = require('commander');
command.option('-d, --database [database]', 'Database configuration file.')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file')
        .option('-o, --oink [oink]', 'Oink configuration file')
        .option('-s, --secret [secret]', 'Korba secret key')
        .option('-c, --client [client]', 'Korba client key')
        .option('-a, --apiKey [apiKey]', 'engageSpark api key')
        .option('-i, --orgID [orgID]', 'engageSpark org ID').parse(process.argv);

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

var korba_config = null;
if(typeof command.secret !== 'undefined') {
    korba_config = {};
    korba_config.secret_key = fs.readFileSync(command.secret,'utf8').trim();
    korba_config.client_key = fs.readFileSync(command.client,'utf8').trim();
} else {
    korba_config = require('./korba-config.json');
}

var engagespark_config = null;
if(typeof command.apiKey !== 'undefined') {
    engagespark_config = {};
    engagespark_config.apiKey = fs.readFileSync(command.apiKey,'utf8').trim();
    engagespark_config.orgID = fs.readFileSync(command.orgID,'utf8').trim();
} else {
    engagespark_config = require('./engagespark-config.json');
}

//initialize engagespark
var topup = new engagespark(engagespark_config.orgID, engagespark_config.apiKey);

var oink_config = null;
if(typeof command.oink !== 'undefined') {
    oink_config = require(command.oink);
} else {
    oink_config = require('./oink-config.json');
}

const PAYMENTS_TABLE = oink_config.payments_table
const RESPONDENTS_TABLE = oink_config.respondents_table

//an incentive function is a lambda function that returns a list of amount, reason pairs
//these get inserted into the specified payment table if they don't already
//exit
//incentive functions are stateless, duplicate transactions are prevented by
//this function for the same user and reason string. Reason strings should therefore NOT contain
//randomness
function incentivize_users(incentive_function, callback) {

    function get_respondents(callback) {
        var qstring = format.withArray('SELECT * FROM %I', [RESPONDENTS_TABLE]);
        pg_pool.query(qstring, (err, res) => {
            callback(err, res);
        });
    }

    function generate_incentives(incentive_function, respondents_list, callback) {
        var payments = []
        for(let i = 0; i < respondents_list.rows.length; i++) {
            pay = incentive_function(respondents_list.rows[i])
            for(let j = 0; j < pay.length; j++) {
                pay[j].respondent_info = respondents_list.rows[i];
            }
            payments = payments.concat(pay);
        }

        callback(null, payments);
    }

    function write_incentives(payments_to_write, callback) {
        async.forEachLimit(payments_to_write, 1, function(item, inner_callback) {
            var r = item.respondent_info;
            var transaction_id = [r.phone_number, r.carrier, item.incentive_type, item.incentive_id, 1].join('-');

            //generate incentives that target the APIs at the correct proportion
            var r = Math.random() 
            var payment_api = "";
            var sum = 0;
            for(var i in oink_config.payment_apis) {
                sum += i['proportion'];
                if(r  < sum) {
                    payment_api = i['payment_api'];
                }
            }

            if(payment_api == "") {
                payment_api = oink_config.payment_apis[0]['name'];
            }

            var qstring = format.withArray('INSERT INTO %I (phone_number, carrier, respondent_id, ' +
                'time_created, amount, incentive_id, incentive_type, payment_attempt, ' +
                'status, external_transaction_id, payment_api) VALUES (%L, %L, %L, NOW(), %L, %L, ' +
                '%L, %L, %L, %L, %L) ON CONFLICT DO NOTHING',
                [PAYMENTS_TABLE, r.phone_number, r.carrier, r.respondent_id,
                 item.amount, item.incentive_id, item.incentive_type, 1, 'waiting', transaction_id, payment_api]);

            console.log(qstring);
            pg_pool.query(qstring, (err, res) => {
                inner_callback(err, res);
            });
        }, function(err) {
            callback(err);
        });
    }

    async.waterfall([
        get_respondents,
        async.apply(generate_incentives, incentive_function),
        write_incentives
    ], function(err, result) {
        console.log("Returning from incentvize users with errors:", err, "and Result:", result);
        callback(err);
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
      callback(error, response.statusCode);
    });
}

function issue_payments(callback) {
    //take payments in the waiting state added above and send them to Korba
    //change them to pending
    function update_status(qstring, callback) {
        pg_pool.query(qstring, (err, res) => {
            callback(err, res);
        });
    }

    var qstring = format.withArray("SELECT phone_number, carrier, amount, external_transaction_id, payment_api from %I WHERE status = 'waiting'", [PAYMENTS_TABLE]);
    console.log(qstring);
    pg_pool.query(qstring, (err, res) => {
        if(err) {
            console.log("Error getting waiting payments:",err);
        } else {
            async.forEachLimit(res.rows, 10, function(row, callback) {
                if(row.payment_api == "korba") {
                    //for each row
                    //issue the payment
                    var pending_qstring = format.withArray("UPDATE %I SET status = 'pending', time_submitted = NOW() " +
                                  "WHERE external_transaction_id = %L",[PAYMENTS_TABLE, row.external_transaction_id]);
                    async.series([
                        async.apply(send_to_korba, row.phone_number, row.carrier, row.amount, row.external_transaction_id),
                        async.apply(update_status, pending_qstring)
                    ], function(err, result) {
                        //we actually dont' want to error here because it will stop
                        //everyone from being paid
                        console.log(err);
                        callback();
                    });
                } else if (row.payment_api == "engagespark") {
                    var success_qstring = format.withArray("UPDATE %I SET status = 'success', time_submitted = NOW() " +
                                  "WHERE external_transaction_id = %L",[PAYMENTS_TABLE, row.external_transaction_id]);

                    var pending_qstring = format.withArray("UPDATE %I SET status = 'pending', time_submitted = NOW() " +
                                  "WHERE external_transaction_id = %L",[PAYMENTS_TABLE, row.external_transaction_id]);

                    async.series([
                        async.apply(topup.send_topup, '233' + row.phone_number, row.amount, row.external_transaction_id),
                        async.apply(update_status, success_qstring)
                    ], function(err, result) {
                        console.log(err);
                        update_status(pending_qstring, function(err, res) {
                            callback(err);
                        });
                    });
                }
            }, function(err) {
                callback(err)
            });
        }
    });
}

// HERE are our current incentive functions //////////////////////////////
// We could imagine these in a separate file where each exported function
// is automatically run ///////////////////////////////////////////////

//Start the program
// 1) check status of pending payments with korba
//    - send SMS if success
//    - retry if failure
// 2) incentivize new users
// 3) issue payments that need to be issued

var incentive_funcs = require(oink_config.incentives_file);

var incentive_list = []
for (var func in incentive_funcs) {
    incentive_list.push(async.apply(incentivize_users,incentive_funcs[func]));
}

var to_call_list = [];
to_call_list = to_call_list.concat(incentive_list,[issue_payments]);

//we need to run our functions in series
async.series(to_call_list, function(err, results) {
    if(err) {
        console.log("Error running OINK payment functions:", err)
    } else {
        console.log("Oink periodic processing completed successfully");
    }
});

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
const twilio = require('twilio')


//get the usernames and passwords necessary for this task
var command = require('commander');
command.option('-d, --database [database]', 'Database configuration file.')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file')
        .option('-t, --twilio [twilio]', 'Twilio accound sid')
        .option('-a, --api_key [api_key]', 'Twilio api key')
        .option('-o, --oink [oink]', 'Oink configuration file')
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
    korba_config.secret_key = fs.readFileSync(command.secret,'utf8').trim();
    korba_config.client_key = fs.readFileSync(command.client,'utf8').trim();
} else {
    korba_config = require('./korba-config.json');
}

var twilio_config = null;
if(typeof command.twilio !== 'undefined') {
    twilio_config.account_sid = fs.readFileSync(command.twilio,'utf8').trim();
    twilio_config.api_key = fs.readFileSync(command.api_key,'utf8').trim();
} else {
    twilio_config = require('./twilio-config.json');
}

var client = new twilio(twilio_config.account_sid, twilio_config.api_key);

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
    
            var qstring = format.withArray('INSERT INTO %I (phone_number, carrier, respondent_id, ' + 
                'time_created, amount, incentive_id, incentive_type, payment_attempt, ' + 
                'status, external_transaction_id) VALUES (%L, %L, %L, NOW(), %L, %L, ' + 
                '%L, %L, %L, %L) ON CONFLICT (external_transaction_id) DO NOTHING', 
                [PAYMENTS_TABLE, r.phone_number, r.carrier, item.respondent_id, 
                 item.amount, item.incentive_id, item.incentive_type, 1, 'waiting', transaction_id]);
    
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
      callback(error, response.statusCode);
    });
}

function issue_payments(callback) {
    //take payments in the waiting state added above and send them to Korba
    //change them to pending
    function set_to_pending(qstring, callback) {
        pg_pool.query(qstring, (err, res) => {
            callback(err, res);
        });
    }

    var qstring = format.withArray("SELECT phone_number, carrier, amount, external_transaction_id from %I WHERE status = 'waiting'", [PAYMENTS_TABLE]);
    console.log(qstring);
    pg_pool.query(qstring, (err, res) => {
        if(err) {
            console.log("Error getting waiting payments:",err);
        } else {
            async.forEachLimit(res.rows, 1, function(row, callback) {
                //for each row
                //send it to korba
                var qstring = format.withArray("UPDATE %I SET status = 'pending', time_submitted = NOW() " + 
                              "WHERE external_transaction_id = %L",[PAYMENTS_TABLE, row.external_transaction_id]);
                async.series([
                    async.apply(send_to_korba, row.phone_number, row.carrier, row.amount, row.external_transaction_id),
                    async.apply(set_to_pending, qstring)
                ], function(err, result) {
                    //we actually dont' want to error here because it will stop
                    //everyone from being paid
                    console.log(err);
                    callback();
                }); 
            }, function(err) {
                callback(err)
            });
        }
    });
}

function retry_payment(external_transaction_id, callback) {
    
    function get_payment_number(transaction_id, callback) {
        qstring = format.withArray('SELECT * from %I WHERE external_transaction_id = %L', [PAYMENTS_TABLE, transaction_id]);
        pg_pool.query(qstring, (err, res) => {
            callback(err, res.rows[0]);
        });
    }

    function insert_new_transaction(prev, callback) {
        if(prev.payment_attempt > 3) {
            console.log("Payment retried too many times, not retrying");
            callback();
        } else {
            attempt = prev.payment_attempt + 1;

            new_transaction_id = prev.external_transaction_id.split('-')
            new_transaction_id[new_transaction_id.length-1] = attempt
            new_transaction_id = new_transaction_id.join('-');

            var qstring = format.withArray('INSERT INTO %I (phone_number, carrier, respondent_id, ' + 
                'time_created, amount, incentive_id, incentive_type, payment_attempt, ' +
                'status, external_transaction_id) VALUES (%L, %L, %L, NOW(), %L, %L, ' +
                '%L, %L, %L, %L) ON CONFLICT (external_transaction_id) DO NOTHING', 
                [PAYMENTS_TABLE, prev.phone_number, prev.carrier, prev.respondent_id, 
                    prev.amount, prev.incentive_id, prev.incentive_type, attempt, 'waiting', new_transaction_id]);

            console.log(qstring);
            pg_pool.query(qstring, (err, res) => {
                callback(err, res);
            });
        }
    }

    async.waterfall([
        async.apply(get_payment_number,external_transaction_id),
        insert_new_transaction
    ], function(err, res) {
        callback(err);
    });
}

function update_payment_status(new_status, transaction_id, callback) {
    qstring = format.withArray("UPDATE %I SET status = %L WHERE external_transaction_id = %L",[PAYMENTS_TABLE, new_status, transaction_id]);
    console.log(qstring);
    pg_pool.query(qstring, (err, res) => {
        callback(err, res);
    });
}

function update_payment_state_from_status(error, result, body, id, callback) {
    //update it's state

    var new_payment_state = "";
    if(error) {
        return callback(error);
    } else if ('success' in body && body['success'] == false) {
        if(body['error_code'] == 421) {
            new_payment_state = 'error';
        } else {
            return callback(error);
        }
    } else if ('success' in body && body['success'] == true) {
        if(body['status'] == 'success') {
            new_payment_state = 'complete';
        } else if(body['status'] == 'pending') {
            //for now just wait on this
            //TODO add a pending timeout where we retry
            return callback();
        } else if(body['status'] == 'failed') {
            new_payment_state = 'failed';
        } else {
            return callback(body);
        }
    } else {
        return callback(body);
    }

    if(new_payment_state == 'complete') {
        async.series([
            async.apply(update_payment_status, new_payment_state, id),
            //async.apply(send_sms, id)
        ], function(err, results) {
            callback(err);
        });
    } else {
        async.series([
            async.apply(update_payment_status, new_payment_state, id),
            async.apply(retry_payment, id)
        ], function(err, results) {
            callback(err);
        });
    }
}

function check_status(callback) {
    //take payments in the pending state and query korba for their status
    //update it if necessary
    //IF successful send a text message to the user
    //If failed generate a new payment with the attempt number incremented
    
    function get_pending_transactions(callback) {
        qstring = format.withArray("SELECT external_transaction_id from %I WHERE status = 'pending'", [PAYMENTS_TABLE]);
        pg_pool.query(qstring, (err, res) => {
            callback(err, res);
        });            
    }

    function check_and_update_transactions(transactions, callback) {
        console.log(transactions.rows.length);
        async.forEachLimit(transactions.rows, 1, function(row, callback) {
            //for each row
            //send it to korba
            check_korba_status(row.external_transaction_id, function(error, result, body) {
                update_payment_state_from_status(error, result, body, row.external_transaction_id, function(err) {
                    callback(err);
                });
            });
        }, function(err) {
            callback(err)
        });
    }

    async.waterfall([
        get_pending_transactions,
        check_and_update_transactions
    ], function(err, result) {
        callback(err);
    });
}

function send_sms(phone_number, message) {
    //send sms to the user
    console.log("Sending message to", phone_number, "with message", message);

    client.messages.create({
            to: '+233' + phone_number,
            from: 'GridWatch',
            body: message,
            statusCallback: "",
     }).then(message => {
        //Write the result of that request to a final table about user notification
        console.log(message.status)
        console.log(message.error_code)
    });
}

// HERE are our current incentive functions //////////////////////////////
// We could imagine these in a separate file where each exported function
// is automatically run ///////////////////////////////////////////////

//will incentivize every user for complianceApp-30 for 4 Cedis
function complianceApp(args) {
    compliance_list = [];

    if(args.currently_active == true) {
        //calculate the number of days between deployment and now
        days = ((((Date.now() - args.pilot_survey_time)/1000)/3600)/24)
        compliances_to_issue = Math.floor(days/30);
        for(let i = 0; i < compliances_to_issue; i++) {
            var obj = {};
            obj.amount = oink_config.complianceAppAmount;
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
            obj.amount = oink_config.compliancePowerwatchAmount;
            obj.incentive_type = 'compliancePowerwatch';
            obj.incentive_id = (i+1)*30;
            compliance_list.push(obj);
        }
    }

    return compliance_list;
}

//Start the program
// 1) check status of pending payments with korba
//    - send SMS if success
//    - retry if failure
// 2) incentivize new users
// 3) issue payments that need to be issued

//we need to run our functions in series
async.series([
    check_status,
    //To ADD more incentives, add new incentivize user functions with new lambda functions to this list
    async.apply(incentivize_users, complianceApp),
    async.apply(incentivize_users, compliancePowerwatch),
    issue_payments,
], function(err, results) {
    if(err) {
        console.log("Error running OINK payment functions:", err)
    } else {
        console.log("Oink periodic processing completed successfully");
    }
});

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
    korba_config = {};
    korba_config.secret_key = fs.readFileSync(command.secret,'utf8').trim();
    korba_config.client_key = fs.readFileSync(command.client,'utf8').trim();
} else {
    korba_config = require('./korba-config.json');
}

var twilio_config = null;
if(typeof command.twilio !== 'undefined') {
    twilio_config = {};
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
      timeout: 60000,
      headers:{
        'Content-Type':'application/json',
        'Authorization':`HMAC ${client_key}:${hash}`
      },
      json: true,
      body: bodySorted,
      resolveWithFullResponse: true,
    },function(error, response, bodyKorba){
      console.log('error: ', error);
      if(typeof response != 'undefined') {
          console.log('statusCode: ', response.statusCode);
      }
      console.log(bodyKorba);
      callback(null, response, bodyKorba);
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
                'status, external_transaction_id, payment_api) VALUES (%L, %L, %L, NOW(), %L, %L, ' +
                '%L, %L, %L, %L, %L) ON CONFLICT (external_transaction_id) DO NOTHING',
                [PAYMENTS_TABLE, prev.phone_number, prev.carrier, prev.respondent_id,
                    prev.amount, prev.incentive_id, prev.incentive_type, attempt, 'waiting', new_transaction_id, prev.payment_api]);

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

function get_korba_new_payment_state(body) {
    if ('success' in body && body['success'] == false) {
        if(body['error_code'] == 421) {
            return 'error';
        } else {
            return null;
        }
    } else if ('success' in body && body['success'] == true) {
        if(body['status'] == 'success') {
            return 'complete';
        } else if(body['status'] == 'pending') {
            //for now just wait on this
            //TODO add a pending timeout where we retry
            return 'pending';
        } else if(body['status'] == 'failed') {
            return 'failed';
        } else {
            return null;
        }
    } else {
        return null;
    }
}

function update_payment_state_from_status(new_payment_state, transaction, callback) {

    if(new_payment_state == 'complete') {
        async.series([
            async.apply(update_payment_status, new_payment_state, transaction.external_transaction_id),
            async.apply(send_sms, transaction.phone_number, transaction.amount, transaction.incentive_type)
        ], function(err, results) {
            callback(err);
        });
    } else {
        async.series([
            async.apply(update_payment_status, new_payment_state, transaction.external_transaction_id),
            async.apply(retry_payment, transaction.external_transaction_id)
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
        qstring = format.withArray("SELECT * from %I WHERE status = 'pending' OR status = 'success'", [PAYMENTS_TABLE]);
        pg_pool.query(qstring, (err, res) => {
            callback(err, res);
        });
    }

    function check_and_update_transactions(transactions, callback) {
        console.log(transactions.rows.length);
        async.forEachLimit(transactions.rows, 10, function(row, callback) {
            //for each row
            //send it to korba
            if(row.payment_api == "korba") {
                check_korba_status(row.external_transaction_id, function(error, result, body) {
                    if(result) {
                        var stat = get_korba_new_payment_state(body);
                        //if it's pending just leave it alone
                        if(stat != 'pending') {
                            update_payment_state_from_status(stat, row, function(err) {
                                callback(err);
                            });
                        } else {
                            callback();
                        }
                    }
                });
            } else if (row.payment_api == "engagespark") {
                if(row.status == "success") {
                    update_payment_state_from_status('complete', row, function(err) {
                        callback(err);
                    });
                } else {
                    update_payment_state_from_status('failed', row, function(err) {
                        callback(err);
                    });
                }
            }
        }, function(err) {
            //iff there is an error just ignore it for now
            console.log("Got status check error:", err)
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

function send_sms(phone_number, amount, stimulus_type, callback) {

    message = "";
    if(stimulus_type == 'complianceApp') {
        message = "Thank you for participating in GridWatch. We have sent you airtime for your participation. If you have questions please contact 024 6536896";
    } else if (stimulus_type == 'compliancePowerwatch') {
        message = "Thank you for participating in GridWatch. We have sent you airtime for your participation. If you have questions please contact 024 6536896";
    } else {
        callback("Can't send message for that stimulus");
    }

    //send sms to the user
    console.log("Sending message to", phone_number, "with message", message);

    client.messages.create({
            to: '+233' + phone_number,
            from: 'GridWatch',
            body: message,
            statusCallback: "",
     }).then(message => {
        console.log(message.status)
        console.log(message.error_code)
        callback(null, message.error_code);
    });
}

//Start the program
// 1) check status of pending payments with korba
//    - send SMS if success
//    - retry if failure

var to_call_list = [];
to_call_list = to_call_list.concat([check_status]);

//we need to run our functions in series
async.series(to_call_list, function(err, results) {
    if(err) {
        console.log("Error running OINK payment functions:", err)
    } else {
        console.log("Oink periodic processing completed successfully");
    }
});

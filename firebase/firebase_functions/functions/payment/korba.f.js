const functions = require('firebase-functions');
const curl = require('curlrequest');
const admin = require('firebase-admin');
const util = require('util');
const request = require('request-promise');
const crypto = require('crypto');
const sortObj = require('sort-object');
try {admin.initializeApp(functions.config().firebase);} catch(e) {}
 // You do that because the admin SDK can only be initialized once.

//Creating a firebase object to navigate it:
var db = admin.firestore();
var FieldValue = admin.firestore.FieldValue;

//korba funtion:
// - Module for for korba-specific users. It's triggered by core1 function using an HTTPS request.
// - Structures the data to be sent to the Korba API proxy.
exports = module.exports = functions.https.onRequest((req, res) => {
    const reqBody = req.body
    const jsonInfo = {
        "customer_number": reqBody.customer_number,
        "amount": reqBody.amount,    
        "transaction_id": reqBody.transaction_id,
        "network_code": reqBody.network_code,
        "callback_url": "https://us-central1-paymenttoy.cloudfunctions.net/paymentApicallback",
        "description": reqBody.description,
        "client_id": 14
        }
    console.log(`The Disbursement API request is: ${util.inspect(jsonInfo)}`);
    console.log(util.inspect(reqBody));
    return request({
            uri: 'https://korba.grid.watch/api',
            method: 'POST',
            headers:{
            'Content-Type':'application/json',
            },
            json: true,
            body: jsonInfo,
            resolveWithFullResponse: true,
         }).then((response) => {
                if (response.statusCode >= 400) {
                throw new Error(`HTTP Error: ${response.statusCode}`);
                res.status(response.statusCode).send(response.body)
                }
                
                console.log('Posted with response from API: ', response.body);
                console.log('Status from korba: ', response.statusCode);
                res.status(response.statusCode).send(response.body)
                
        }).catch((error) => { 
            res.send(error)
        });

});


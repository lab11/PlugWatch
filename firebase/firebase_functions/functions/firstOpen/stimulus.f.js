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

exports = module.exports = functions.https
    .onRequest((req, res) => {
    const reqBody = req.body
    reqBody['time'] = FieldValue.serverTimestamp()
    var dummyCron = db.collection('invite_transaction').add(reqBody);
    res.status(200).send(reqBody);

});
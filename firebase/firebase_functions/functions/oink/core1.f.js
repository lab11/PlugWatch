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

//core1 funtion:
// - Triggers on creation of tx_core_payment events. Checks the user information for payment in the user_list collection. 
//   If so, start to structuring the body for the payment request and update invite_transaction,tx_core_payment status and 
//   set the payment service of the user based on user_list information.
// - Send payment request to the scific payment service module. In this case core1 triggers a https function (korba).
//   id transaction is successful, log information into rx_core_payment.

// - Parameters:
//    * There are not specific parameters for this function.

exports = module.exports = functions.firestore
    .document('tx_core_payment/{docId}').onWrite((event) =>{
        //Getting the data that was modified and initializing all the parameters for payment.
        const data = event.data.data();
        const previousData = event.data.previous.data();
        const docId = event.params.docId;
        var userPaymentInfo = {}
        var localMsgs = []
        
        //Check if the document was deleted, if so return null (for avoiding infinite loop)
        if (!event.data.exists){
            return null;
        }

        //Check if the document is not new, if so check the status, num_attempts and reattempt flag (for avoiding infinite loops)
        if (event.data.previous.exists){
            if (data.status != 'failed' || data.num_attempts >= 5 || data.reattempt){
                return null;

            } else {
                //Starting a new reattempt
                localMsgs.push('attempt '+ (data.num_attempts + 1))
                db.collection('tx_core_payment').doc(docId).update({reattempt: true, num_attempts: data.num_attempts + 1, msgs: localMsgs });
                return db.collection('user_list').doc(data.user_id).get()
                    .then(doc => {
                        if (!doc.exists){
                            //TODO: Maybe trigger an alarm here.
                            db.collection('alarms_db').add({timestamp: FieldValue.serverTimestamp(),user_id:data.user_id, reason:"User ID does not exist.",tx_core_doc_id:docId });
                            throw new Error('Invalid or unexisting User ID.');
                            // No reattempt since this should be trigger an alarm for possible fraud.
                        } else {
                            var userPaymentData = doc.data()
                            //send all the common data among all APIs and trigger an HTTP function based on the user payment service.
                            userPaymentInfo['customer_number'] = userPaymentData.customer_number;
                            userPaymentInfo['network_code'] = userPaymentData.network_code;
                            userPaymentInfo['payment_service'] = userPaymentData.payment_service;
                            
                        }
                    })
                    //Creating the format of the body for the HTTP request
                    .then(() => {
                        userPaymentInfo['amount'] = data.amount;
                        userPaymentInfo['type'] = data.type;
                        userPaymentInfo['user_id'] = data.user_id;
                        userPaymentInfo['transaction_id'] = new Date().getUTCMilliseconds();
                        userPaymentInfo['description'] = 'payment of '+ userPaymentInfo.type +' to user : '+ userPaymentInfo.user_id;
                        console.log(`user payment info is: ${util.inspect(userPaymentInfo)}`);
                        var namePaymentService = userPaymentInfo.payment_service;
                        namePaymentService = namePaymentService.charAt(0).toUpperCase() + namePaymentService.slice(1)
        
                        return request({
                            uri: 'https://us-central1-paymenttoy.cloudfunctions.net/payment'+namePaymentService,
                            method: 'POST',
                            headers:{
                                'Content-Type':'application/json',
                            },
                            json: true,
                            body: userPaymentInfo,
                            resolveWithFullResponse: true,
                        }).then((response) => {
                                    if (response.statusCode >= 400) {
                                        localMsgs.push('HTTP Error')
                                        console.log(`HTTP Error: ${response.statusCode}`);
                                        return db.collection('tx_core_payment').doc(docId).update({reattempt: false, status:'failed', msgs: localMsgs});
                                    }
                                    
                                    console.log('Posted with payment service response: ', response.body);
                                    console.log('Payment service status: ', response.statusCode);
                                    var checkErrorFromBody = response.body;
        
                                    if (checkErrorFromBody.success === 'false' || checkErrorFromBody.error_code != null || checkErrorFromBody.detail == "Invalid Signature."){
                                        console.log('Error in transaction:', checkErrorFromBody);
                                        localMsgs.push('Transaction Error')
                                        return db.collection('tx_core_payment').doc(docId).update({reattempt: false, status:'failed', msgs: localMsgs});
                                    }
                                    else {
                                        var logDb = {}
                                        return db.collection('rx_core_payment').add({
                                            amount:data.amount,
                                            type: data.type,
                                            user_id: data.user_id,
                                            transaction: userPaymentInfo.transaction_id,
                                            tx_core_doc_id: docId
                                            
                                        }).then(() =>{
                                            localMsgs.push('Payment completed')
                                            return db.collection('tx_core_payment').doc(docId).update({reattempt: false, status:'completed', msgs: localMsgs});
                                        });
                                    }
                        });
        
                    });
                

            }

        }

        console.log(`The onCreate event document is: ${util.inspect(data)}`);
        console.log(`The docId of the creation was: ${util.inspect(docId)}`);

        return db.collection('tx_core_payment')
        .doc(docId).update({

            status:'pending'

        }).then(() => {
            //Updating the status of the document that generated the transaction:
            var doc_path_string = data.type + '_transaction'
            return db.collection(doc_path_string).doc(data.stimulus_doc_id).update({status:'pending',tx_core_doc_id: docId});

            //Getting the info from user_list to complete the API request
            }).then(() => {
                return db.collection('user_list').doc(data.user_id).get()
                    .then(doc => {
                        if (!doc.exists){
                            //TODO: Maybe trigger an alarm here.
                            console.log('The user does not exist in the user_list collection!')
                            db.collection('alarms_db').add({timestamp: FieldValue.serverTimestamp(),user_id:data.user_id, reason:"User ID does not exist.",tx_core_doc_id:docId });
                            throw new Error('Invalid or unexisting User ID.');
                        } else {
                            var userPaymentData = doc.data()
                            //send all the common data among all APIs and trigger an HTTP function based on the user payment service.
                            userPaymentInfo['customer_number'] = userPaymentData.customer_number;
                            userPaymentInfo['network_code'] = userPaymentData.network_code;
                            userPaymentInfo['payment_service'] = userPaymentData.payment_service;
                            
                        }
                    }).then(() => {
                        return db.collection('tx_core_payment').doc(docId).update({payment_service: userPaymentInfo.payment_service, num_attempts: data.num_attempts + 1});
                    });

            //Creating the format of the body for the HTTP request
            }).then(() => {
                userPaymentInfo['amount'] = data.amount;
                userPaymentInfo['type'] = data.type;
                userPaymentInfo['user_id'] = data.user_id;
                userPaymentInfo['transaction_id'] = new Date().getUTCMilliseconds();
                userPaymentInfo['description'] = 'payment of '+ userPaymentInfo.type +' to user : '+ userPaymentInfo.user_id;
                console.log(`user payment info is: ${util.inspect(userPaymentInfo)}`);
                var namePaymentService = userPaymentInfo.payment_service;
                namePaymentService = namePaymentService.charAt(0).toUpperCase() + namePaymentService.slice(1)


                return request({
                    uri: 'https://us-central1-paymenttoy.cloudfunctions.net/payment'+namePaymentService,
                    method: 'POST',
                    headers:{
                        'Content-Type':'application/json',
                    },
                    json: true,
                    body: userPaymentInfo,
                    resolveWithFullResponse: true,
                }).then((response) => {
                            //Checking the API response:
                            if (response.statusCode >= 400) {
                                localMsgs.push("HTTP Error")
                                return db.collection('tx_core_payment').doc(docId).update({reattempt: false, status:'failed', msgs: localMsgs});
                            }
                            
                            console.log('Posted with payment service response: ', response.body);
                            console.log('Payment service status: ', response.statusCode);
                            var checkErrorFromBody = response.body;
                            

                            if (checkErrorFromBody.success === 'false' || checkErrorFromBody.error_code != null || checkErrorFromBody.detail == "Invalid Signature."){
                                console.log('Error in transaction:', checkErrorFromBody);
                                localMsgs.push('Transaction Error')
                                return db.collection('tx_core_payment').doc(docId).update({reattempt: false, status:'failed', msgs: localMsgs});
                            }
                            else {
                                var logDb = {}
                                return db.collection('rx_core_payment').add({
                                    amount:data.amount,
                                    type: data.type,
                                    user_id: data.user_id,
                                    transaction: userPaymentInfo.transaction_id,
                                    tx_core_doc_id: docId
                            

                                }).then(() =>{
                                    localMsgs.push('Payment completed')
                                    return db.collection('tx_core_payment').doc(docId).update({reattempt: false, status:'completed', msgs: localMsgs});
                                });
                            }
                });

            });

        

        

         
    });


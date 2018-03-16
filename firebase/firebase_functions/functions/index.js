'use strict';
/** EXPORT ALL FUNCTIONS
 *
 *   Loads all `.f.js` files
 *   Exports a cloud function matching the file name
 *   Author: David King
 *   Edited: Tarik Huber
 *   Based on this thread:
 *     https://github.com/firebase/functions-samples/issues/170
 */
const glob = require("glob");
const camelCase = require("camelcase");
const files = glob.sync('./**/*.f.js', { cwd: __dirname, ignore: './node_modules/**'});
for(let f=0,fl=files.length; f<fl; f++){
  const file = files[f];
  const functionName = camelCase(file.slice(0, -5).split('/').join('_')); // Strip off '.f.js'
  if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === functionName) {
    exports[functionName] = require(file);
  }
}


// const functions = require('firebase-functions');
// const curl = require('curlrequest');
// const admin = require('firebase-admin');
// const util = require('util');
// const request = require('request-promise');
// const crypto = require('crypto');
// const sortObj = require('sort-object');



// //Initializing Admin to write on firebase:
// admin.initializeApp(functions.config().firebase);

// //Creating a firebase object to navigate it:
// var db = admin.firestore();
// var FieldValue = admin.firestore.FieldValue;


// exports.ghana_manual_payment = functions.https
// .onRequest((req, res) => {
//     const reqBody = req.body
//     console.log(util.inspect(reqBody));
//     console.log(req.statusCode);
//     res.status(200).send(reqBody);
    
// });


// //stimulus_invite funtion:
// // - Triggers on creation of invite_transaction events. Checks if totalNumInvites are below the threshold. If so, calculate the amount to be paid and enqueue the transaction
// //   to tx_core_payment collection. Otherwise, sets status of the invite_transaction doc to "restricted". If this function fails to perform any of these tasks, throw an error and
// //   update the invite_transaction status to "failed".

// // - Parameters:
// //    * threshold: Max num_invites that an user can get paid for. 
// //    * costInvite: The value oer invite to be paid.
// //    * event: Event that triggered the function. In this case this is the new document created by the App. It has many parameter including the doc_id and the fields of each document.

// exports.stimulusInvite = functions.firestore
//     .document('invite_transaction/{docId}').onCreate((event) =>{
//         //Getting the data that was modified and initializing all the parameters for payment.
//         const data = event.data.data();
//         const docId = event.params.docId;
//         const threshold = 100;
//         var totalNumInv = 0;
//         var totalNumFailedInv = 0;
//         const costInvite = 0.1;

//         console.log(`The onCreate event document is: ${util.inspect(data)}`);
//         console.log(`The docId of the creation was: ${util.inspect(docId)}`);

//         return db.collection('invite_transaction').where('user_id','==', data.user_id).get() //We need to sum over non-failed transaction.
//                 .then(snapshot => {
//                         //Calculating the total num of invites that the specific user has sent.
//                         return snapshot.forEach(doc => {
//                                     totalNumInv += doc.data().num_invites;
//                                 });
                    
//                 }).then(() => {
//                     //Calculating the total number of invites in status "failed"
//                     return db.collection('invite_transaction').where('user_id','==', data.user_id).where('status','==','failed').get() // Calculating the num. of failed transactions.
//                             .then(snapshot => {
//                                 return snapshot.forEach(doc => {
//                                 totalNumFailedInv += doc.data().num_invites;
//                                 });
//                             });

//                 }).then(() => {
//                     //Calculating the number of invites available to redeem:
//                     console.log(`Total Num. Invites before failed state: ${totalNumInv}`);
//                     console.log(`Total Num. Invites in fail state: ${totalNumFailedInv}`);
//                     totalNumInv = totalNumInv - totalNumFailedInv;
//                     console.log(`Total Num. Invites: ${totalNumInv}`)

//                     //Verifying if the number of invites is less than threshold:
//                     if (totalNumInv <= threshold) {
//                         return db.collection('invite_transaction')
//                             .doc(docId).update({valid_num_invites: data.num_invites, status:'enqueued'})
//                             .then(() => {
//                                 //Calculating the amount to pay and write on tx_core_payment collection
//                                 var toPay = data.num_invites * costInvite;
//                                 return db.collection('tx_core_payment').add({
//                                     user_id: data.user_id,
//                                     amount: toPay,
//                                     msgs: [],
//                                     num_attempts: 0,
//                                     time: FieldValue.serverTimestamp(),
//                                     type: 'invite',
//                                     stimulus_doc_id: docId,
//                                     status: 'pending',
//                                     reattempt: false
                                    
//                                 });

//                             }).then(ref => {
//                                 return console.log('Added document with ID: ', ref.id);
//                             }).catch(err => {
//                                 console.log('Error getting docs in invites under threshold', err);
//                                 return db.collection('invite_transaction')
//                                     .doc(docId).update({status:'failed'});
//                             });
                                
//                     //if total num. invites is bigger than threshold, calculate how many of them can be redeemed:
//                     } else{
//                         var validNumEvents = threshold - (totalNumInv - data.num_invites)
//                         console.log(`valid num events: ${validNumEvents}`);
//                         if (validNumEvents <= 0){
//                             return db.collection('invite_transaction')
//                             .doc(docId).update({valid_num_invites: 0, status:'restricted'})
//                             .then(() => {
//                                 return console.log(`User ${data.user_id} exceeded the quota of Invites.`);
//                                 //we can also think of triggering an alarm here.

//                             }).catch(err => {
//                                 console.log('Error getting docs in invites for exceeded quota', err);
//                                 return db.collection('invite_transaction')
//                                     .doc(docId).update({status:'failed'});
//                             });

//                         } else {
//                             return db.collection('invite_transaction')
//                             .doc(docId).update({valid_num_invites: validNumEvents, status: 'enqueued'})
//                             .then(() => {
//                                 var toPay = validNumEvents * costInvite;
//                                 return db.collection('tx_core_payment').add({
//                                     user_id: data.user_id,
//                                     amount: toPay,
//                                     msgs: [],
//                                     num_attempts: 0,
//                                     time: FieldValue.serverTimestamp(),
//                                     type: 'invite',
//                                     stimulus_doc_id: docId,
//                                     status: 'pending',
//                                     reattempt: false
                                    
//                                 });

//                             }).then(ref => {
//                                 return console.log('Added document with ID: ', ref.id);
//                             }).catch(err => {
//                                 console.log('Error getting docs in invites for exceeded quota > 0', err);
//                                 return db.collection('invite_transaction')
//                                     .doc(docId).update({status:'failed'});

//                             });

//                         }
                        

//                     }
//                 }).catch(err => {
//                     console.log('Error getting documents', err);
//                     return db.collection('invite_transaction')
//                                     .doc(docId).update({status:'failed'});
                    
//                 });
                  
// });


// //StimulusCron Function:
// // - Similar to stimulusInvite function but handles the incentives for surveys.
// //TODO: Implement this function.
// exports.stimulusCron = functions.firestore
//     .document('cron_transaction/{docId}').onCreate((event) =>{
//         //Getting the data that was modified:
//         const data = event.data.data();
//         const docId = event.params.docId;
//         var threshold;
//         console.log(`The onWrite event document is: ${util.inspect(data)}`);
//         console.log(`The docId of the creation was: ${util.inspect(docId)}`);
        
//         return db.collection('tx_core_payment').add(
//             {
//                 user_id: data.user_id,
//                 msgs: [],
//                 num_attempts: 0,
//                 time: FieldValue.serverTimestamp(),
//                 type: 'cron',
//                 stimulus_doc_id: docId,
//                 status:'pending',
//                 reattempt: false
//             }
            
//             ).then(ref => {
//             console.log('Added document with ID: ', ref.id);
//         });
// });


// //core1 funtion:
// // - Triggers on creation of tx_core_payment events. Checks the user information for payment in the user_list collection. 
// //   If so, start to structuring the body for the payment request and update invite_transaction,tx_core_payment status and 
// //   set the payment service of the user based on user_list information.
// // - Send payment request to the scific payment service module. In this case core1 triggers a https function (korba).
// //   id transaction is successful, log information into rx_core_payment.

// // - Parameters:
// //    * There are not specific parameters for this function.

// exports.core1 = functions.firestore
//     .document('tx_core_payment/{docId}').onWrite((event) =>{
//         //Getting the data that was modified and initializing all the parameters for payment.
//         const data = event.data.data();
//         const previousData = event.data.previous.data();
//         const docId = event.params.docId;
//         var userPaymentInfo = {}

//         if (event.data.previous.exists()){
//             if (data.status != 'failed' || data.num_attempts >= 5 || data.reattempt){
//                 return null;

//             // } else if (data.status == 'failed' && data.msgs[data.msgs.length - 1].startsWith('attempt')){
//             //     return null;

//             } else {
//                 //TODO: Implement re-attempt of payment n times.
//                 db.collection('tx_core_payment').doc(docId).update({reattempt: true, num_attempts: data.num_attempts + 1, msgs: data.msgs.push('attempt '+ (data.num_attempts + 1))});
//                 return db.collection('user_list').doc(data.user_id).get()
//                     .then(doc => {
//                         if (!doc.exists){
//                             //TODO: Maybe trigger an alarm here.
//                             console.log('The user does not exist in the user_list collection!')
//                         } else {
//                             var userPaymentData = doc.data()
//                             //send all the common data among all APIs and trigger an HTTP function based on the user payment service.
//                             userPaymentInfo['customer_number'] = userPaymentData.customer_number;
//                             userPaymentInfo['network_code'] = userPaymentData.network_code;
//                             userPaymentInfo['payment_service'] = userPaymentData.payment_service;
                            
//                         }
//                     })//.then(() => { // this might be redundant
//                         //return db.collection('tx_core_payment').doc(docId).update({payment_service: userPaymentInfo.payment_service});
//                     //})
//                     .then(() => {
//                         userPaymentInfo['amount'] = data.amount;
//                         userPaymentInfo['type'] = data.type;
//                         userPaymentInfo['user_id'] = data.user_id;
//                         userPaymentInfo['transaction_id'] = new Date().getUTCMilliseconds();
//                         userPaymentInfo['description'] = 'payment of '+ userPaymentInfo.type +' to user : '+ userPaymentInfo.user_id;
//                         console.log(`user payment info is: ${util.inspect(userPaymentInfo)}`);
        
//                         return request({
//                             uri: 'https://us-central1-paymenttoy.cloudfunctions.net/'+userPaymentInfo.payment_service,
//                             method: 'POST',
//                             headers:{
//                                 'Content-Type':'application/json',
//                             },
//                             json: true,
//                             body: userPaymentInfo,
//                             resolveWithFullResponse: true,
//                         }).then((response) => {
//                                     if (response.statusCode >= 400) {
//                                         console.log(`HTTP Error: ${response.statusCode}`);
//                                         return db.collection('tx_core_payment').doc(docId).update({reattempt: false, status:'failed', msgs: data.msgs.push('HTTP Error')});
//                                     }
                                    
//                                     console.log('Posted with payment service response: ', response.body);
//                                     console.log('Payment service status: ', response.statusCode);
//                                     var checkErrorFromBody = response.body;
        
//                                     if (checkErrorFromBody.success === 'false' || checkErrorFromBody.error_code != null){
//                                         log.console('Error in transaction:', checkErrorFromBody);
//                                         return db.collection('tx_core_payment').doc(docId).update({reattempt: false, status:'failed', msgs: data.msgs.push('Transaction Error')});
//                                     }
//                                     else {
//                                         var logDb = {}
//                                         return db.collection('rx_core_payment').add({
//                                             amount:data.amount,
//                                             type: data.type,
//                                             user_id: data.user_id,
//                                             transaction: userPaymentInfo.transaction_id
//                                             //TODO: Add the docId of the stimuli or the tx_core_payment.
//                                         }).then(() =>{
//                                             return db.collection('tx_core_payment').doc(docId).update({reattempt: false, status:'completed', msgs: data.msgs.push('Payment completed')});
//                                         });
//                                     }
//                         });
        
//                     });
                

//             }

//         }

//         console.log(`The onCreate event document is: ${util.inspect(data)}`);
//         console.log(`The docId of the creation was: ${util.inspect(docId)}`);

//         return db.collection('tx_core_payment')
//         .doc(docId).update({

//             status:'pending'

//         }).then(() => {
//             //TODO: Find a way to update status to the proper document without the if statements (incentive agnostic).
//             //When core is triggered update the status of the invites_transaction or cron_transaction to pending:
//             if (data.type == 'invite'){
//                 return db.collection('invite_transaction').doc(data.stimulus_doc_id).update({status:'pending',tx_core_doc_id: docId});

//             } else {
//                 return db.collection('cron_transaction').doc(data.stimulus_doc_id).update({status:'pending', tx_core_doc_id: docId});
//             }
        
//             }).then(() => {
//                 return db.collection('user_list').doc(data.user_id).get()
//                     .then(doc => {
//                         if (!doc.exists){
//                             //TODO: Maybe trigger an alarm here.
//                             console.log('The user does not exist in the user_list collection!')
//                         } else {
//                             var userPaymentData = doc.data()
//                             //send all the common data among all APIs and trigger an HTTP function based on the user payment service.
//                             userPaymentInfo['customer_number'] = userPaymentData.customer_number;
//                             userPaymentInfo['network_code'] = userPaymentData.network_code;
//                             userPaymentInfo['payment_service'] = userPaymentData.payment_service;
                            
//                         }
//                     }).then(() => {
//                         return db.collection('tx_core_payment').doc(docId).update({payment_service: userPaymentInfo.payment_service});
//                     });

//             }).then(() => {
//                 userPaymentInfo['amount'] = data.amount;
//                 userPaymentInfo['type'] = data.type;
//                 userPaymentInfo['user_id'] = data.user_id;
//                 userPaymentInfo['transaction_id'] = new Date().getUTCMilliseconds();
//                 userPaymentInfo['description'] = 'payment of '+ userPaymentInfo.type +' to user : '+ userPaymentInfo.user_id;
//                 console.log(`user payment info is: ${util.inspect(userPaymentInfo)}`);

//                 return request({
//                     uri: 'https://us-central1-paymenttoy.cloudfunctions.net/'+userPaymentInfo.payment_service,
//                     method: 'POST',
//                     headers:{
//                         'Content-Type':'application/json',
//                     },
//                     json: true,
//                     body: userPaymentInfo,
//                     resolveWithFullResponse: true,
//                 }).then((response) => {
//                             if (response.statusCode >= 400) {
//                             throw new Error(`HTTP Error: ${response.statusCode}`);
//                             }
                            
//                             console.log('Posted with payment service response: ', response.body);
//                             console.log('Payment service status: ', response.statusCode);
//                             var checkErrorFromBody = response.body;

//                             if (checkErrorFromBody.success === 'false' || checkErrorFromBody.error_code != null){
//                                 log.console('Error in transaction. Try again.');
//                             }
//                             else {
//                                 var logDb = {}
//                                 return db.collection('rx_core_payment').add({
//                                     amount:data.amount,
//                                     type: data.type,
//                                     user_id: data.user_id,
//                                     transaction: userPaymentInfo.transaction_id
                            

//                                 });
//                             }
//                 });

//             });

        

        

         
//     });


// //korba funtion:
// // - Module for for korba-specific users. It's triggered by core1 function using an HTTPS request.
// // - Structures the data to be sent to the Korba API proxy.
//  exports.korba = functions.https.onRequest((req, res) => {
//     const reqBody = req.body
//     const jsonInfo = {
//         "customer_number": reqBody.customer_number,
//         "amount": reqBody.amount,    
//         "transaction_id": reqBody.transaction_id,
//         "network_code": reqBody.network_code,
//         "callback_url": "https://us-central1-paymenttoy.cloudfunctions.net/invSysFunct3",
//         "description": reqBody.description,
//         "client_id": 14
//         }
//     console.log(`The Disbursement API request is: ${util.inspect(jsonInfo)}`);
//     console.log(util.inspect(reqBody));
//     return request({
//             uri: 'https://korba.grid.watch/api',
//             method: 'POST',
//             headers:{
//             'Content-Type':'application/json',
//             },
//             json: true,
//             body: jsonInfo,
//             resolveWithFullResponse: true,
//          }).then((response) => {
//                 if (response.statusCode >= 400) {
//                 throw new Error(`HTTP Error: ${response.statusCode}`);
//                 res.status(response.statusCode).send(response.body)
//                 }
                
//                 console.log('Posted with response from API: ', response.body);
//                 console.log('Status from korba: ', response.statusCode);
//                 res.status(response.statusCode).send(response.body)
                
//         }).catch((error) => { 
//             res.send(error)
//         });

// });
    

// exports.invSysFunct3 = functions.https
//     .onRequest((req, res) => {
//         const reqBody = req.body
//         console.log(util.inspect(reqBody));
//         console.log(req.statusCode);
//         res.status(200).send(reqBody);
    
// });


// exports.generateDocumentInvite = functions.https
//     .onRequest((req, res) => {
//     const reqBody = req.body
//     reqBody['time'] = FieldValue.serverTimestamp()
//     var dummyCron = db.collection('invite_transaction').add(reqBody);
//     res.status(200).send(reqBody);

// });


// exports.generateDocumentCron = functions.https
//     .onRequest((req, res) => {
//     const reqBody = req.body
//     reqBody['time'] = FieldValue.serverTimestamp()
//     reqBody['status'] = 'waiting' //waiting is submitted for revision by the core function.

//     var dummyCron = db.collection('cron_transaction').add(reqBody);
//     res.status(200).send(reqBody);

// });


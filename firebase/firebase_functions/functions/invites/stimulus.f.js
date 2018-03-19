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

//stimulus_invite funtion:
// - Triggers on creation of invite_transaction events. Checks if totalNumInvites are below the threshold. If so, calculate the amount to be paid and enqueue the transaction
//   to tx_core_payment collection. Otherwise, sets status of the invite_transaction doc to "restricted". If this function fails to perform any of these tasks, throw an error and
//   update the invite_transaction status to "failed".

// - Parameters:
//    * threshold: Max num_invites that an user can get paid for. 
//    * costInvite: The value oer invite to be paid.
//    * event: Event that triggered the function. In this case this is the new document created by the App. It has many parameter including the doc_id and the fields of each document.

exports = module.exports = functions.firestore
    .document('invite_transaction/{docId}').onCreate((event) =>{
        //Getting the data that was modified and initializing all the parameters for payment.
        const data = event.data.data();
        const docId = event.params.docId;
        const threshold = 100;
        var totalNumInv = 0;
        var totalNumFailedInv = 0;
        const costInvite = 0.1;

        console.log(`The onCreate event document is: ${util.inspect(data)}`);
        console.log(`The docId of the creation was: ${util.inspect(docId)}`);

        return db.collection('invite_transaction').where('user_id','==', data.user_id).get() //We need to sum over non-failed transaction.
                .then(snapshot => {
                        //Calculating the total num of invites that the specific user has sent.
                        return snapshot.forEach(doc => {
                                    totalNumInv += doc.data().num_invites;
                                });
                    
                }).then(() => {
                    //Calculating the total number of invites in status "failed"
                    return db.collection('invite_transaction').where('user_id','==', data.user_id).where('status','==','failed').get() // Calculating the num. of failed transactions.
                            .then(snapshot => {
                                return snapshot.forEach(doc => {
                                totalNumFailedInv += doc.data().num_invites;
                                });
                            });

                }).then(() => {
                    //Calculating the number of invites available to redeem:
                    console.log(`Total Num. Invites before failed state: ${totalNumInv}`);
                    console.log(`Total Num. Invites in fail state: ${totalNumFailedInv}`);
                    totalNumInv = totalNumInv - totalNumFailedInv;
                    console.log(`Total Num. Invites: ${totalNumInv}`)

                    //Verifying if the number of invites is less than threshold:
                    if (totalNumInv <= threshold) {
                        return db.collection('invite_transaction')
                            .doc(docId).update({valid_num_invites: data.num_invites, status:'enqueued'})
                            .then(() => {
                                //Calculating the amount to pay and write on tx_core_payment collection
                                var toPay = data.num_invites * costInvite;
                                toPay = Math.round(toPay * 100) / 100
                                return db.collection('tx_core_payment').add({
                                    user_id: data.user_id,
                                    amount: toPay,
                                    msgs: [],
                                    num_attempts: 0,
                                    time: FieldValue.serverTimestamp(),
                                    type: 'invite',
                                    stimulus_doc_id: docId,
                                    status: 'pending',
                                    reattempt: false
                                    
                                });

                            }).then(ref => {
                                return console.log('Added document with ID: ', ref.id);
                            }).catch(err => {
                                console.log('Error getting docs in invites under threshold', err);
                                return db.collection('invite_transaction')
                                    .doc(docId).update({status:'failed'});
                            });
                                
                    //if total num. invites is bigger than threshold, calculate how many of them can be redeemed:
                    } else{
                        var validNumEvents = threshold - (totalNumInv - data.num_invites)
                        console.log(`valid num events: ${validNumEvents}`);
                        if (validNumEvents <= 0){
                            return db.collection('invite_transaction')
                            .doc(docId).update({valid_num_invites: 0, status:'restricted'})
                            .then(() => {
                                return console.log(`User ${data.user_id} exceeded the quota of Invites.`);
                                //we can also think of triggering an alarm here.

                            }).catch(err => {
                                console.log('Error getting docs in invites for exceeded quota', err);
                                return db.collection('invite_transaction')
                                    .doc(docId).update({status:'failed'});
                            });

                        } else {
                            return db.collection('invite_transaction')
                            .doc(docId).update({valid_num_invites: validNumEvents, status: 'enqueued'})
                            .then(() => {
                                var toPay = validNumEvents * costInvite;
                                toPay = Math.round(toPay * 100) / 100
                                return db.collection('tx_core_payment').add({
                                    user_id: data.user_id,
                                    amount: toPay,
                                    msgs: [],
                                    num_attempts: 0,
                                    time: FieldValue.serverTimestamp(),
                                    type: 'invite',
                                    stimulus_doc_id: docId,
                                    status: 'pending',
                                    reattempt: false
                                    
                                });

                            }).then(ref => {
                                return console.log('Added document with ID: ', ref.id);
                            }).catch(err => {
                                console.log('Error getting docs in invites for exceeded quota > 0', err);
                                return db.collection('invite_transaction')
                                    .doc(docId).update({status:'failed'});

                            });

                        }
                        

                    }
                }).catch(err => {
                    console.log('Error getting documents', err);
                    return db.collection('invite_transaction')
                                    .doc(docId).update({status:'failed'});
                    
                });
                  
});


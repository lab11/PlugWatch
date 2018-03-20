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


//stimulus_firstOpen funtion:

// Triggers upon app first open via first timestamp of account sign in. 
// Validates first log in by checking that account is never been opened before. 
// Calculates the amount to be paid to the user and enqueues transaction to tx_core_payment collection. 
// Otherwise, sets status of the firstOpen_transcation doc to "restricted".
// If fails, throws error and upadtes the status to "failed".

// - Parameters:

// * Validation bit: bit set to high if account has ever been longed in on device
// * costFirstOpen: The value paid 
// * event: Event that triggered the function. In this case this is the new document created by the App.
/*
exports = module.exports = functions.https.onRequest((request, response) => {
    response.send("Hello from Joe");
})
*/

exports = module.exports = functions.firestore
    .document('firstOpen_transaction/{docId}').onCreate((event) =>{
        //Getting the data that was modified and initializing all the parameters for payment.
        const data = event.data.data();
        const docId = event.params.docId;
        const costFirstOpen = 5;
        var previouslyOpened = false;
        
        //console.log(`The onCreate event document is: ${util.inspect(data)}`);
        console.log(`The docId of the creation was: ${util.inspect(docId)}`);

        return db.collection('firstOpen_transaction').where('user_id','==', data.user_id).get() //We need to sum over non-failed transaction.
                .then(snapshot => {
                        //Calculating the total num of invites that the specific user has sent.
                        return snapshot.forEach(doc => {
                                    totalNumInv += doc.data().num_invites;
                                });
                    
                }).then(() => {
                    //Calculating the total number of invites in status "failed"
                    return db.collection('firstOpen_transaction').where('user_id','==', data.user_id).where('status','==','failed').get() // Calculating the num. of failed transactions.
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
                        return db.collection('firstOpen_transaction')
                            .doc(docId).update({valid_num_invites: data.num_invites, status:'enqueued'})
                            .then(() => {
                                //Calculating the amount to pay and write on tx_core_payment collection
                                var toPay = data.num_invites * costInvite;
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
                                return db.collection('firstOpen_transaction')
                                    .doc(docId).update({status:'failed'});
                            });
                                
                    //if total num. invites is bigger than threshold, calculate how many of them can be redeemed:
                    } else{
                        var validNumEvents = threshold - (totalNumInv - data.num_invites)
                        console.log(`valid num events: ${validNumEvents}`);
                        if (validNumEvents <= 0){
                            return db.collection('firstOpen_transaction')
                            .doc(docId).update({valid_num_invites: 0, status:'restricted'})
                            .then(() => {
                                return console.log(`User ${data.user_id} exceeded the quota of Invites.`);
                                //we can also think of triggering an alarm here.

                            }).catch(err => {
                                console.log('Error getting docs in invites for exceeded quota', err);
                                return db.collection('firstOpen_transaction')
                                    .doc(docId).update({status:'failed'});
                            });

                        } else {
                            return db.collection('firstOpen_transaction')
                            .doc(docId).update({valid_num_invites: validNumEvents, status: 'enqueued'})
                            .then(() => {
                                var toPay = validNumEvents * costInvite;
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
                                return db.collection('firstOpen_transaction')
                                    .doc(docId).update({status:'failed'});

                            });

                        }
                        

                    }
                }).catch(err => {
                    console.log('Error getting documents', err);
                    return db.collection('firstOpen_transaction')
                                    .doc(docId).update({status:'failed'});
                    
                });
                  
});







exports = module.exports = functions.https
    .onRequest((req, res) => {
    const reqBody = req.body
    reqBody['time'] = FieldValue.serverTimestamp()
    var dummyCron = db.collection('firstOpen_transaction').add(reqBody);
    res.status(200).send(reqBody);
});



//https://us-central1-paymenttoy.cloudfunctions.net/generatorsFirstOpen

//validation and build snapshot and amount copy and paste from invite function
//write to core 1 by checking fields core one uses and filling them in copy and paste invite function
// console.logs goes to function within console drop down

#!/usr/bin/env node

var dgram = require('dgram');
var listener = dgram.createSocket({type: 'udp4', reuseAddr: true}).bind(5000);
var sender = dgram.createSocket({type: 'udp4'});


//This is the udp section of the listener
listener.on('error', function(error) {
    console.log('UDP error:');
    console.log(error);
});

listener.on('listening', function() {
    console.log('UDP listening on ' + listener.address().address + ':' + listener.address().port);
});

listener.on('message', function(msg, rinfo) {
    console.log(msg);
    console.log(rinfo);

    //Turn msg into json
    try {
        msgString = msg.toString('utf8');
        console.log(msgString);
        evt = JSON.parse(msgString);
        console.log(evt);
    } catch(e) {
        console.log('Error converting to JSON');
        console.log(e)
    }

    try {
        sender.send(JSON.stringify(evt),5001,'localhost', function(err) {
            console.log("Got error sending udp 1");
            console.log(err);
        });
        sender.send(JSON.stringify(evt),5002,'localhost', function(err) {
            console.log("Got error sending udp 2");
            console.log(err);
        });
    } catch(e) {
        console.log('UDP handling error: ' + e)
    }
});



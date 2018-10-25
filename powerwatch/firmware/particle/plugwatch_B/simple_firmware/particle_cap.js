#!/usr/bin/env node

var EventSource = require('eventsource');
var fs = require('fs');

var URL = 'https://api.particle.io/v1/devices/events?access_token=3bfb10b3da0273200525159db35be81ba7a3d3aa';

var es = new EventSource(URL);
es.addEventListener('currentTime', function (e) {
  console.log(e.data)
  fs.appendFileSync('log.json', e.data);
})



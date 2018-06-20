var timescale_config = require('./postgres-config.json'); 

const { Pool }  = require('pg');
var format      = require('pg-format');
const express   = require('express');
const app       = express();
var moment      = require('moment');


const pg_pool = new  Pool( {
    user: timescale_config.username,
    host: timescale_config.host,
    database: timescale_config.database,
    password: timescale_config.password,
    port: timescale_config.port,
    max: 20,
})

console.log("Using timescale at " + timescale_config.host +
        ":" + timescale_config.port + "  db=" + timescale_config.database);

// Do a query of the deployments table so that we can load a map
app.get('/init', (req,resp) => {
    console.log('Received init request')
    pg_pool.query("SELECT latitude, longitude, deployment_time from deployment", (err, res) => {
        if(err) {
            console.log('Initial query error:' + err)
            resp.status(500).send('Database query error');
        } else {

            var latitudes = [];
            var longitudes = [];
            var times = [];
            for(var i = 0; i < res.rows.length; i++) {
                latitudes.push(res.rows[i].latitude) 
                longitudes.push(res.rows[i].longitude)
                times.push(res.rows[i].deployment_time)
            }

            var lat_min = Math.min(...latitudes);
            var lat_max = Math.max(...latitudes);
            var long_min = Math.min(...longitudes);
            var long_max = Math.max(...longitudes);
            var time_min = Math.min(...times);

            var lat_dist = lat_max - lat_min;
            var long_dist = long_max - long_min;
            var lat_av = (lat_max + lat_min)/2;
            var long_av = (long_max + long_min)/2;
            blob = {};
            blob.lat_min = lat_min-lat_dist*1.0;
            blob.lat_max = lat_max+lat_dist*1.0;
            blob.long_min = long_min-long_dist*1.0;
            blob.long_max = long_max+long_dist*1.0;
            blob.lat_av = lat_av;
            blob.long_av = long_av;
            blob.latitudes = latitudes;
            blob.longitudes = longitudes;
            blob.time_min = time_min;
            console.log(blob.time_min);
            resp.send(blob);
        }
    });
});

app.get('/getData', (req, resp) => {
    //First query the deployment table to get the list of core_id's and coordinates to base the power state on
    
    var geoJSON = {}
    geoJSON.type = "FeatureCollection";
    geoJSON.features = [];
    var last_features = [];
    var last_feature_dict = {};
    pg_pool.query('SELECT core_id, latitude, longitude from deployment', (err, res) => {
        if(err) {
            console.log('Postgress error');
            console.log(err);
            resp.status(500).send('Database query error');
        } else {
            //okay we should iterate through the response
            for(var i = 0; i < res.rows.length; i++) {
                var feature = {};
                feature.type = "Feature";
                feature.geometry = {};
                feature.geometry.type = "Point";
                feature.geometry.coordinates = [res.rows[i].longitude,res.rows[i].latitude];
                feature.properties = {};
                feature.properties.time = new Date(req.query.start_time).getTime()/1000;
                feature.properties.first_minute = 0;
                feature.properties.last_minute = 0;
                feature.properties.core_id = res.rows[i].core_id;
                feature.properties.state = 0;
                feature.properties.last_battery = 85;
                feature.properties.last_update = new Date(req.query.start_time).getTime()/1000;
                last_feature_dict[res.rows[i].core_id] = i;
                geoJSON.features.push(feature);
            }

            last_features = JSON.parse(JSON.stringify(geoJSON.features));
            console.log(last_features.length);

            pg_pool.query('SELECT time, powerwatch.core_id, is_powered, state_of_charge from powerwatch inner join deployment on deployment.core_id=powerwatch.core_id where time >deployment.deployment_time AND time > $1 AND time < $2 ORDER BY time asc', [req.query.start_time + 'UTC', req.query.end_time + 'UTC'], (err, res) => {
                if(err) {
                    console.log('Postgress error');
                    console.log(err);
                    resp.status(500).send('Database query error');
                } else {
                    //Iterate through each minute, updating each features state in the geojson blob based on the data from the query
                    iTime = moment(new Date(req.query.start_time));
                    end_datetime = moment(new Date(req.query.end_time));
                    console.log(iTime);
                    console.log(end_datetime);
                    var j = 0;
                    var minutes = 0;
                    for(; iTime < end_datetime; iTime = iTime.add(1,'m')) {
                        //console.log(iTime)
                        //Iterate through all the points in this minute and 
                        //update the state table
                        //console.log('Updating data')
                        while(res.rows[j].time < iTime) {
                            var ind = last_feature_dict[res.rows[j].core_id];
                            if(res.rows[j].is_powered == true && last_features[ind].properties.state != 3) {
                                last_features[ind].properties.last_minute = minutes;
                                geoJSON.features.push(JSON.parse(JSON.stringify(last_features[ind])));
                                last_features[ind].properties.state = 3;
                                last_features[ind].properties.last_battery = res.rows[j].state_of_charge;
                                last_features[ind].properties.last_update = res.rows[j].time;
                                last_features[ind].properties.first_minute = minutes;
                            } else if(res.rows[j].is_powered == true && last_features[ind].properties.state == 3) {
                                last_features[ind].properties.last_update = res.rows[j].time;
                                last_features[ind].properties.last_battery = res.rows[j].state_of_charge;
                            } else if (res.rows[j].is_powered == false && last_features[ind].properties.state != 2) {
                                last_features[ind].properties.last_minute = minutes;
                                geoJSON.features.push(JSON.parse(JSON.stringify(last_features[ind])));
                                last_features[ind].properties.state = 2;
                                last_features[ind].properties.last_battery = res.rows[j].state_of_charge;
                                last_features[ind].properties.last_update = res.rows[j].time;
                                last_features[ind].properties.first_minute = minutes;
                            } else if(res.rows[j].is_powered == false && last_features[ind].properties.state == 2) {
                                last_features[ind].properties.last_update = res.rows[j].time;
                                last_features[ind].properties.last_battery = res.rows[j].state_of_charge;
                            }
                            j++; 
                        }

                        // Update the time and check if any sensors have
                        // passed the offline threshold
                        // then push
                        //console.log('Updating feature set')
                        for(var k = 0; k < last_features.length; k++) {
                            if(moment.duration(iTime.diff(last_features[k].properties.last_update)).asMinutes() >  20 && (last_features[k].properties.state != 0 || last_features[k].properties.state != 1)) {
                                last_features[k].properties.last_minute = minutes;
                                geoJSON.features.push(JSON.parse(JSON.stringify(last_features[k])));
                                if(last_features[k].properties.last_battery < 15) {
                                    last_features[k].properties.state = 1;
                                } else {
                                    last_features[k].properties.state = 0;
                                }
                                last_features[k].properties.last_update = iTime;
                                last_features[k].properties.first_minute = minutes;
                            } else if (moment.duration(iTime.diff(last_features[k].properties.last_update)).asMinutes() >  20) {
                                last_features[k].properties.last_update = iTime;
                            }
                        }
                        minutes++;
                    }

                    for(var k = 0; k < last_features.length; k++) {
                        last_features[k].properties.last_minute = minutes;
                        geoJSON.features.push(JSON.parse(JSON.stringify(last_features[k])));
                    }
                    
                    console.log('sending response');
                    console.log(geoJSON.features.slice(500,600));
                    resp.send(geoJSON);
                }
            });

        }
    });
});

app.use('/',express.static('public'));
app.listen(3000, () => console.log('Example app listening on port 3000!'))  

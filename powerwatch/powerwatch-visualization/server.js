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
            console.log(blob)
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
                feature.properties.core_id = res.rows[i].core_id;
                feature.properties.state = 'offline';
                geoJSON.features.push(feature);
            }

            last_features = geoJSON.features;
            console.log(last_features);

            pg_pool.query('SELECT time, powerwatch.core_id, is_powered, state_of_charge from powerwatch inner join deployment on deployment.core_id=powerwatch.core_id where time >deployment.deployment_time AND time > $1 AND time < $2 ORDER BY time asc', [req.query.start_time, req.query.end_time], (err, res) => {
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
                    for(; iTime < end_datetime; iTime = iTime.add(1,'m')) {
                        var i = 0;
                        while(iTime < res.rows[i].time) {
                             
                            i++; 
                        }
                    }
                }
            });

        }
    });
});

app.use('/',express.static('public'));
app.listen(3000, () => console.log('Example app listening on port 3000!'))  

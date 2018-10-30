const { Pool }  = require('pg');
var format      = require('pg-format');
const express   = require('express');
const app       = express();
var moment      = require('moment');
var rand        = require('random-seed').create(0);
var passport = require('passport');
var Strategy = require('passport-google-oauth20').Strategy;
var path = require('path');
ensureLoggedIn = require('connect-ensure-login').ensureLoggedIn;

var command = require('commander');

command.option('-d, --database [database]', 'Database configuration file.')
        .option('-u, --username [username]', 'Database username file')
        .option('-p, --password [password]', 'Database password file').parse(process.argv);

var timescale_config = null;
if(typeof command.database !== 'undefined') {
    timescale_config = require(command.database);
    timescale_config.username = fs.readFileSync(command.username,'utf8').trim()
    timescale_config.password = fs.readFileSync(command.password,'utf8').trim()
} else {
    timescale_config = require('./postgres-config.json');
}

// Configure the google strategy for use by Passport.
//
// OAuth 2.0-based strategies require a `verify` function which receives the
// credential (`accessToken`) for accessing the google API on the user's
// behalf, along with the user's profile.  The function must invoke `cb`
// with a user object, which will be set at `req.user` in route handlers after
// authentication.
passport.use(new Strategy({
    clientID: '256972206462-14lceghjprd7jpvqgfj2vkos25ieqrou.apps.googleusercontent.com',
    clientSecret: 'PcZxvTn20SnO7Ud97x-Ha3Uv',
    callbackURL: 'https://portal.grid.watch/login/google/return'
  },
  function(accessToken, refreshToken, user, cb) {
    var fs = require('fs');
    var user_list = fs.readFileSync("./acl").toString('utf-8');
    users = user_list.split("\n");

    if(!user.emails) {
        cb(null, false);
    } else {
        if(users.indexOf(user.emails[0].value) == -1) {
            cb(null, false);
        } else {
            cb(null, user);
        }
    }
  }
));

// Configure Passport authenticated session persistence.
//
// In order to restore authentication state across HTTP requests, Passport needs
// to serialize users into and deserialize users out of the session.  In a
// production-quality application, this would typically be as simple as
// supplying the user ID when serializing, and querying the user record by ID
// from the database when deserializing.  However, due to the fact that this
// example does not have a database, the complete google profile is serialized
// and deserialized.
passport.serializeUser(function(user, cb) {
  cb(null, user);
});

passport.deserializeUser(function(user, cb) {
  cb(null, user);
});

// Use application-level middleware for common functionality, including
// logging, parsing, and session handling.
app.use(require('morgan')('combined'));
app.use(require('cookie-parser')());
app.use(require('body-parser').urlencoded({ extended: true }));
app.use(require('express-session')({ secret: 'keyboard cat', resave: true, saveUninitialized: true }));

// Initialize Passport and restore authentication state, if any, from the
// session.
app.use(passport.initialize());
app.use(passport.session());

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
app.get('/init', (req, resp, next) => {
    if(!req.isAuthenticated()) {
        resp.redirect('/login');
        return;
    }

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
            
            //load the low_voltage data
            var fs = require('fs');
            var low_voltage_data = fs.readFileSync("./achimota_lines.geojson").toString('utf-8');
            var low_voltage_json = JSON.parse(low_voltage_data);
            blob.low_voltage = low_voltage_json;

            console.log(blob.time_min);
            resp.send(blob);
        }
    });
});

app.get('/getData', (req, resp) => {
    if(!req.isAuthenticated()) {
        resp.redirect('/login');
        return;
    }
    
    var geoJSON = {}
    geoJSON.type = "FeatureCollection";
    geoJSON.features = [];
    var last_features = [];
    var last_feature_dict = {};
    pg_pool.query('SELECT core_id, latitude, longitude, cellular_carrier from deployment', (err, res) => {
        if(err) {
            console.log('Postgress error');
            console.log(err);
            resp.status(500).send('Database query error');
        } else {
            //okay we should iterate through the response
            long_dig = rand(20)-10;
            lat_dig = rand(20)-10;
            for(var i = 0; i < res.rows.length; i++) {
                var feature = {};
                feature.type = "Feature";
                feature.geometry = {};
                feature.geometry.type = "Point";
                // Randomize fourth digit of latitude/longitude
                randomized_longitude = (res.rows[i].longitude/0.001)*0.001 + 0.0002*long_dig;
                randomized_latitude = (res.rows[i].latitude/0.001)*0.001 + 0.0002*lat_dig;
                feature.geometry.coordinates = [randomized_longitude,randomized_latitude];
                feature.properties = {};
                feature.properties.time = new Date(req.query.start_time).getTime()/1000;
                feature.properties.first_minute = 0;
                feature.properties.last_minute = 0;
                feature.properties.core_id = res.rows[i].core_id;
                feature.properties.state = 0;
                feature.properties.last_battery = 85;
                feature.properties.last_update = new Date(req.query.start_time).getTime()/1000;
                feature.properties.cellular_carrier = res.rows[i].cellular_carrier;
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
                    iTime = moment.utc(req.query.start_time);
                    end_datetime = moment.utc(req.query.end_time);
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

app.get('/login', passport.authenticate('google', {scope: ['email']}));

app.get('/login/google/return', 
  passport.authenticate('google', { failureRedirect: '/login' }),

  function(req, res) {
    console.log('got google return');
    res.redirect('/');
  }
);



app.get('/',  function(req, res) {
    if(!req.isAuthenticated()) {
        res.redirect('/login');
        return;
    }

    res.sendFile(path.join(__dirname+'/public/index.html'));
});
        
app.listen(3765, 'localhost', () => console.log('Example app listening on port 3765!'))  

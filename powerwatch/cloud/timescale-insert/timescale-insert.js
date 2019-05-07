#!/usr/bin/env node

const { Pool }  = require('pg');
var format      = require('pg-format');
var async      = require('async');

var timescale_insert = function(options) {
    if(typeof options.workers == 'undefined') {
        options.workers = 20;
    }

    if(typeof options.port == 'undefined') {
        options.port = 5432;
    }

    this.pg_pool = new  Pool( {
        user: options.user,
        host: options.host,
        database: options.database,
        password: options.password,
        port: options.port,
        max: options.workers
    });
}

//can interpret ISO TIME Strings and JS native date objects
function get_type(meas) {
    switch(typeof meas) {
        case "string":
            //is it a time string
            var test = Date.parse(meas);
            if(Number.isNaN(test)) {
                return 'TEXT';
            } else {
                return 'TIMESTAMPTZ';
            }
        break;
        case "boolean":
            return 'BOOLEAN';
        break;
        case "number":
            return 'DOUBLE PRECISION';
        break;
        default:
        if(Array.isArray(meas)) {
            if(meas.length > 0) {
                switch(typeof meas[0]) {
                    case "string":
                        var test = Date.parse(meas[0]);
                        if(Number.isNaN(test)) {
                            return 'TEXT[]';
                        } else {
                            return 'TIMESTAMPTZ[]';
                        }
                    break;
                    case "boolean":
                        return 'BOOLEAN[]';
                    break;
                    case "number":
                        return 'DOUBLE PRECISION[]';
                    break;
                    default:
                        return 'err';
                    break;
                }
            } else if (typeof meas[0] == 'object' && meas[0] instanceof Date) {
                return 'TIMESTAMPTZ[]';
            } else {
                return 'err';
            }
        } else if (typeof meas == 'object' && meas instanceof Date) {
            return 'TIMESTAMPTZ';
        } else {
            return 'err';
        }
    }
}

function create_table(table_name, object, callback) {
    //I think this can be done better with timescale internal data converter!!
    var cols = "";
    var names = [];
    names.push(table_name);
    for (var key in object) {
        var meas = object[key]
        var type = get_type(meas);
        if(type != 'err') {
            names.push(key);
            names.push(type);
            cols = cols + ", %I %s";
        } else {
            return callback('Error with field ' + key);
        }
    }

    console.log("Trying to create table!");
    //These are dynamic queries!!!
    //Which means the are prone to sql injection attacks
    //timescale supports 'format' execution statements to prevent against this
    //but I can't get that to work, so I'm going to run it client-side
    //Therefore we are as safe as the node-pg-format library
    var qstring = format.withArray('CREATE TABLE %I (TIME TIMESTAMPTZ NOT NULL' + cols + ')',names);
    this.pg_pool.query(qstring, [], (err, res) => {
        if(err) {
            return callback(err);
        } else {
            //make it a hyptertable!
            console.log("Making it a hypertable");
            this.pg_pool.query("SELECT create_hypertable($1,'time')",[table_name], (err, res) => {
                return callback(err);
            });
        }
    });
}


function alter_table(table_name, column_name, object, callback) {
    var type = get_type(object[column_name]);
    var params = [];

    if(type != 'err') {
        params.push(table_name);
        params.push(column_name);
        params.push(type);
    } else {
        console.log('Error with field ' + key);
        console.log('Table alteration failed');
        return;
    }

    //then add the column to the table
    console.log(params);
    var astring = format.withArray("ALTER TABLE %I ADD COLUMN %I %s",params);
    this.pg_pool.query(astring, (err, res) => {
        callback(err);
    });
}


function insert_data(table_name, timestamp, object, callback) {
    //console.log("Insterting the data now!");

    var cols = "";
    var vals = "";
    var i = 2;
    for (var key in object) {
        cols = cols + ", %I";
        vals = vals + ", $" + i.toString();
        i = i + 1
    }

    var names = [];
    var values = [];
    names.push(table_name);
    values.push(timestamp);
    for (var key in object) {
        names.push(key);
        var meas = object[key];
        values.push(meas);
    }

    var qstring = format.withArray("INSERT INTO %I (TIME" + cols + ") VALUES ($1" + vals + ")",names);
    console.log(qstring);
    this.pg_pool.query(qstring, values, (err, res) => {
        if(err) {
            console.log(err)
            //was this error due to adding a field?
            if(err.code == 42703) {
                console.log('Attempting to alter the table!');
                //we can pull the erroneous column out of the err code
                var column_name = err.toString().split("\"")[1];

                alter_table.call(this, table_name, column_name, object, function(err) {
                    if(err) {
                        callback(err);
                    } else {
                        insert_data.call(this, table_name, timestamp, object, callback);
                    }
                });

            } else if (err.code == '42P01') {
                console.log('Attempting to create table');
                create_table.call(this, table_name, timestamp, object, function(err) {
                    if(err) {
                        callback(err);
                    } else {
                        insert_data.call(this, table_name, timestamp, object, callback);
                    }
                });
            }
        } else {
            console.log('posted successfully!');
            return callback(err);
        }
    });
}


timescale_insert.prototype.insertOne = function(table_name, timestamp, object, callback) {
    insert_data.call(this, table_name, timestamp, object, callback);
}

timescale_insert.prototype.insertMany = function(table_name, objects, callback) {
    async.forEach(objects, function(object, callback) {
        timestamp = object['timestamp'];
        delete object['timestamp'];
        insert_data.call(this, table_name, timestamp, object, callback);
    }, function(err) {
        callback(err);
    });
}

module.exports = timescale_insert;

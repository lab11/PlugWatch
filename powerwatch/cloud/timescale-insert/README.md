Timescale Insert
================

Finally made into a module.

Takes javascript objects and table names and forces them into a timescale database.

Creates tables if they don't exist, alters tables if columns are missing.

Tries to guess the type of fields in the javascript object - currently supports text,bool,double and arrays of each. 

Doesn't support changing the type of an existing column

```
var timescale_insert = require('timescale-insert');

var options = {
        host: 'host',
        user: 'user',
        password: 'password',
        database: 'database
};

var inserter = new timescale_insert(options);

inserter.insertOne('my_table', time, my_object);
```

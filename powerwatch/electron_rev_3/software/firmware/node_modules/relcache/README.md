# relcache

A cache for relationships so you don't have to round trip to the DB in order to look them up.

Relationships are stored bi-directionally, and can be searched with a 'find' function.

The information is stored in a nested hash, so lookups are fast.  The data points are either single values, or Arrays which follow 'set' data structure rules, courtesy of lodash.  (i.e. they don't contain duplicate values, and we can remove items from the lists and perform unions reliably)

# Storage Operators

## Set

Set stores many-1 relationships.  The relationships provided in the third arg are assumed to be unique to the key.  If you set the same name again, the value will be overwritten and the reverse lookup will be updated.

The first two arguments are a key and value which represent the left side of the relationship.  The third argument is a hash.  In practice this lets you set multiple relationships in one call.

Use 'set' to store the fields within a record:

```coffee-script
relcache.set "user._id", 5, {name: 'Fred', email: 'fred@foo.com'}

# direct lookup
relcache.get "user._id", 5                  # {name: 'Fred', email: 'fred@foo.com'}

# reverse lookup
relcache.get "user.name", 'Fred'            # {user._id: [5]}
relcache.get "user.email", 'fred@foo.com'   # {user._id: [5]}
```

The many-1 relationship in this case means many IDs could point to the same name:

```coffee-script
relcache.set "user._id", 5, {name: 'Fred'}
relcache.set "user._id", 7, {name: 'Fred'}

relcache.get "user.name", 'Fred' # {user._id: [5, 7]}
```

## Add

Add stores many-many relationships.  If you add two relationships of the same name, the new values will be appended, not overwritten as in the case of 'set'.

Use 'add' to store relationships between records:

```coffee-script
# many to many between chats and users
relcache.add "user._id", 5, {chat._id: 1}
relcache.add "user._id", 5, {chat._id: 4}
relcache.add "user._id", 6, {chat._id: 4}

# direct lookup
relcache.get "user._id", 5                  # {chat._id: [1, 4]}
relcache.get "user._id", 6                  # {chat._id: [4]}

# reverse lookup
relcache.get "chat._id", 1                  # {user._id: [5]}
relcache.get "chat._id", 4                  # {user._id: [5, 6]}
```

# Query Operators

## Get

Use the get method to perform a lookup on key equality.

```coffee-script
relcache.get "user._id", 5
```

You can also specify fields by which to filter:

```coffee-script
relcache.get "user._id", 5, 'name'
relcache.get "user._id", 5, ['name', 'email']
```

## Find

The find function is what you'll want to use if an equality comparitor doesn't cut it for your use case.  It supports [MongoDB's comparison operators](http://docs.mongodb.org/manual/reference/operator/#comparison).

```coffee-script
# many to many between chats and users
relcache.set "user._id", 5, {loginCount: 11}
relcache.set "user._id", 6, {loginCount: 8}
relcache.set "user._id", 7, {loginCount: 16}
relcache.set "user._id", 8, {loginCount: 11}
relcache.set "user._id", 9, {loginCount: 4}

relcache.find "loginCount", "gte", 10 # {user._id: [5, 7, 8]}
```

If you want to store relationships between records, you'll probably want to use a many to many

## LICENSE

(MIT License)

Copyright (c) 2013 Torchlight Software <info@torchlightsoftware.com>

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

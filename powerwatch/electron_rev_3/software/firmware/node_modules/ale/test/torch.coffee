{EventEmitter} = require 'events'
should = require 'should'
{ObjectID} = require('mongodb')

{tandoor, hasKeys, getType, removeAt, box, kvp, sample} = require '..'

example = tandoor (a, b, next) ->
  next a, b

longExample = tandoor (args..., next) ->
  next null, args

describe 'Util', ->
  describe 'Tandoor', ->

    it 'with callback, should execute immediately', (done) ->
      example 1, 2, (a, b) ->
        a.should.eql 1
        b.should.eql 2
        done()

    it 'with partial args, should delay execution', (done) ->
      partial = example 1, 2
      partial (a, b) ->
        a.should.eql 1
        b.should.eql 2
        done()

    it 'with long example, should be really fast', (done) ->
      partial = longExample
      for x in [1..100]
        partial = partial x
      partial done

  describe 'hasKeys', ->

    it 'empty keys should give true', ->
      result = hasKeys {a: 1, b: 2}, []
      result.should.eql true

    it 'present keys should give true', ->
      result = hasKeys {a: 1, b: 2}, ['a', 'b']
      result.should.eql true

    it 'non-present keys should give false', ->
      result = hasKeys {a: 1}, ['a', 'b']
      result.should.eql false

    it 'undefined obj should give false', ->
      result = hasKeys undefined, []
      result.should.eql false

  describe 'getType', ->

    tests = [
        description: 'empty object'
        input: {}
        expected: 'Object'
      ,
        description: 'empty array'
        input: []
        expected: 'Array'
      ,
        description: 'error'
        input: new Error
        expected: 'Error'
      ,
        description: 'string'
        input: 'hi'
        expected: 'String'
      ,
        description: 'undefined'
        input: undefined
        expected: 'Undefined'
      ,
        description: 'null'
        input: null
        expected: 'Null'
      ,
        description: 'ObjectID'
        input: new ObjectID()
        expected: 'ObjectID'
    ]

    for test in tests
      do (test) ->
        {description, input, expected} = test
        it description, ->
          result = getType input
          result.should.eql expected

  describe 'removeAt', ->

    tests = [
        description: 'empty object'
        arr: []
        index: 2
        expected: []
      ,
        description: 'beginning of list'
        arr: [1, 2, 3]
        index: 0
        expected: [2, 3]
      ,
        description: 'middle of list'
        arr: [1, 2, 3]
        index: 1
        expected: [1, 3]
      ,
        description: 'end of list'
        arr: [1, 2, 3]
        index: 2
        expected: [1, 2]
      ,
        description: 'outside of list'
        arr: [1, 2, 3]
        index: 4
        expected: [1, 2, 3]
    ]

    for test in tests
      do (test) ->
        {description, arr, index, expected} = test
        it description, ->
          result = removeAt arr, index
          result.should.eql expected

  describe 'box', ->

    tests = [
        description: 'empty array'
        input: []
        expected: []
      ,
        description: 'undefined'
        input: undefined
        expected: []
      ,
        description: 'null'
        input: null
        expected: []
      ,
        description: 'empty object'
        input: {}
        expected: [{}]
      ,
        description: 'number'
        input: 1
        expected: [1]
      ,
        description: 'string'
        input: 'foo'
        expected: ['foo']
    ]

    for test in tests
      do (test) ->
        {description, input, expected} = test
        it description, ->
          result = box input
          result.should.eql expected

  describe 'kvp', ->

    tests = [
        description: 'no args'
        input: []
        expected: {}
      ,
        description: 'only key'
        input: ['a']
        expected: {a: undefined}
      ,
        description: 'key and value'
        input: ['a', 1]
        expected: {a: 1}
      ,
        description: 'multiple kvps'
        input: ['a', 1, 'b', 2]
        expected: {a: 1, b: 2}
      ,
        description: 'odd number of args'
        input: ['a', 1, 'b', 2, 'c']
        expected: {a: 1, b: 2, c: undefined}
    ]

    for test in tests
      do (test) ->
        {description, input, expected} = test
        it description, ->
          result = kvp input...
          result.should.eql expected

  describe 'sample', ->
    it 'should only call twice', (done) ->
      ee = new EventEmitter

      sample ee, 'test', 2, (err, results) ->
        should.not.exist err
        should.exist results
        results.length.should.eql 2
        done()

      ee.emit 'test', 1
      ee.emit 'test', 2
      ee.emit 'test', 3

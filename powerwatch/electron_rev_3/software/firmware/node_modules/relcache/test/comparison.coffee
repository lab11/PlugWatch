should = require 'should'

Relcache = require '..'
relcache = new Relcache

describe 'Find Comparison', ->
  beforeEach ->
    relcache.clear()

  tests = [
      description: 'eq should work'
      cache: [
        ['count', 5, {_id: 3}]
        ['count', 0, {_id: 4}]
        ['count', 1, {_id: 5}]
      ]
      query: ['count', 'eq', 5]
      expected: {_id: [3]}
    ,
      description: 'ne should work'
      cache: [
        ['count', 5, {_id: 3}]
        ['count', 0, {_id: 4}]
        ['count', 1, {_id: 5}]
      ]
      query: ['count', 'ne', 5]
      expected: {_id: [4, 5]}
    ,
      description: 'gte should work'
      cache: [
        ['count', 5, {_id: 3}]
        ['count', 0, {_id: 4}]
        ['count', 1, {_id: 5}]
      ]
      query: ['count', 'gte', 5]
      expected: {_id: [3]}
    ,
      description: 'gt should work'
      cache: [
        ['count', 5, {_id: 3}]
        ['count', 0, {_id: 4}]
        ['count', 1, {_id: 5}]
      ]
      query: ['count', 'gt', 0]
      expected: {_id: [5, 3]}
    ,
      description: 'lte should work'
      cache: [
        ['count', 5, {_id: 3}]
        ['count', 0, {_id: 4}]
        ['count', 1, {_id: 5}]
      ]
      query: ['count', 'lte', 1]
      expected: {_id: [4, 5]}
    ,
      description: 'lt should work'
      cache: [
        ['count', 5, {_id: 3}]
        ['count', 0, {_id: 4}]
        ['count', 1, {_id: 5}]
      ]
      query: ['count', 'lt', 1]
      expected: {_id: [4]}
    ,
      # anything other than strings in the array will not work!
      description: 'all should work'
      cache: [
        ['arr', ['1', '2', '3'], {_id: 3}]
        ['arr', ['3', '4', '5'], {_id: 5}]
        ['arr', ['7', '8'], {_id: 6}]
      ]
      query: ['arr', 'all', ['1', '2']]
      expected: {_id: [3]}
    ,
      description: 'in should work'
      cache: [
        ['count', 5, {_id: 3}]
        ['count', 0, {_id: 4}]
        ['count', 1, {_id: 5}]
      ]
      query: ['count', 'in', [1, 2, 5]]
      expected: {_id: [5, 3]}
    ,
      description: 'nin should work'
      cache: [
        ['count', 5, {_id: 3}]
        ['count', 0, {_id: 4}]
        ['count', 1, {_id: 5}]
      ]
      query: ['count', 'nin', [1, 2, 5]]
      expected: {_id: [4]}
  ]

  for test in tests
    do (test) ->
      {description, cache, query, expected} = test
      it description, ->
        for record in cache
          relcache.set record...

        result = relcache.find query...
        result.should.eql expected

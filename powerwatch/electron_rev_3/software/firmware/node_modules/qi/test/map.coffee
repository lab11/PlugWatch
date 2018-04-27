should = require 'should'
{map} = require '..'

squareAsync = (n, next) ->
  next null, n * n

describe 'map', ->

  it 'should process an array', (done) ->
    map [1, 2, 3], squareAsync, (err, results) ->
      should.not.exist err
      should.exist results
      results.should.be.an.instanceOf(Array)
      results.should.eql [1, 4, 9]
      done()

  it 'should process an object', (done) ->
    map {a: 1, b: 2, c: 3}, squareAsync, (err, results) ->
      should.not.exist err
      should.exist results
      results.should.be.an.instanceOf(Object)
      results.should.eql {a: 1, b: 4, c: 9}
      done()

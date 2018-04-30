should = require 'should'
{focus} = require '..'

describe 'focus', ->
  it 'should wait for all cbs to return', (done) ->
    cb = focus done

    setTimeout cb(), 1
    setTimeout cb(), 2
    setTimeout cb(), 3

  it 'should not trigger if any cbs are not called', (done) ->

    # we should not modify 'trap' if it works correctly
    trap = 'ready'
    trigger = -> trap = 'sprung!'

    cb = focus trigger

    cb()

    setTimeout cb(), 1
    setTimeout cb(), 2

    finished = ->
      trap.should.eql 'ready'
      done()

    setTimeout finished 10

  it 'should return all results', (done) ->
    cb = focus (err, results) ->
      should.not.exist err
      results.should.be.an.instanceOf(Array)
      results.should.eql [0, 1]
      done()

    i = 0

    doStuff = (cb) ->
      -> cb null, i++

    setTimeout doStuff(cb()), 1
    setTimeout doStuff(cb()), 4

  it 'should name results', (done) ->
    cb = focus (err, results) ->
      should.not.exist err
      results.should.be.an.instanceOf(Object)
      results.first.should.eql 0
      results.second.should.eql 1
      done()

    i = 0

    doStuff = (cb) ->
      -> cb null, i++

    setTimeout doStuff(cb 'first'), 1
    setTimeout doStuff(cb 'second'), 4

  it 'should exit on error', (done) ->
    cb = focus (err, results) ->
      should.exist err
      should.exist results
      results.should.eql []
      done()

    cb()

    setTimeout cb()('some error', 'some result'), 1

  it 'should ignore duplicate calls', (done) ->
    cb = focus (err, results) ->
      should.not.exist err
      results.should.eql ['a', 'c']
      done()

    cb1 = cb()
    cb2 = cb()

    fn1 = -> cb1 null, 'a'
    fn2 = -> cb1 null, 'b'
    fn3 = -> cb2 null, 'c'

    setTimeout fn1, 1
    setTimeout fn2, 1
    setTimeout fn3, 3

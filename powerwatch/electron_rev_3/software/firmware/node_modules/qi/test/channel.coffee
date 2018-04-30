should = require 'should'
{channel} = require '..'

describe 'channel', ->
  it 'should call multiple functions', (done) ->

    task1 = (arg, next) ->
      next null, arg * 4

    task2 = (arg, next) ->
      next null, arg + 2

    final = (err, arg) ->
      should.not.exist err
      should.exist arg
      arg.should.eql 42
      done()

    sequence = channel(task1, task2)
    sequence 10, final

  it 'should process multiple args', (done) ->

    task1 = (arg, next) ->
      next null, arg, 'a', 'b', 'c'

    task2 = (args..., next) ->
      next null, args.concat([1, 2, 3])...

    final = (err, args...) ->
      should.not.exist err
      should.exist args
      args.should.eql [10, 'a', 'b', 'c', 1, 2, 3]
      done()

    sequence = channel(task1, task2)
    sequence 10, final

  it 'should process no args', (done) ->

    task1 = (next) ->
      next()

    task2 = (args..., next) ->
      next()

    final = (err, args...) ->
      should.not.exist err
      should.exist args
      args.should.eql []
      done()

    sequence = channel(task1, task2)
    sequence final

  it 'should process an error', (done) ->

    task1 = (arg, next) ->
      next new Error "something's wrong"

    task2 = (args..., next) ->
      throw new Error 'should not get here'
      next()

    final = (err, args...) ->
      should.exist err
      should.exist args
      args.should.eql []
      done()

    sequence = channel(task1, task2)
    sequence 10, final

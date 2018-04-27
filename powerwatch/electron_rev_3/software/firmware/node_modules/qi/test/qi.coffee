should = require 'should'
{disperse, focus, channel} = require '..'

describe 'qi', ->
  it 'should work together', (done) ->

    # some computations to be chained
    comp1 = (next) -> next null, 5
    comp2 = (num, next) -> next null, num
    comp3 = (next) -> next null, 8
    comp4 = (num, next) -> next null, num * 2

    # analyze the final results
    finish = (err, results) ->
      should.not.exist err
      should.exist results
      results.should.eql {
        local: { comp1: 5, comp2: 3 },
        remote: { comp1: 5, comp4: 16 }
      }
      done()

    final = focus finish
    local = focus final('local')
    remote = focus final('remote')

    l1 = local('comp2')
    r1 = remote('comp4')

    # wire up all computations
    comp1 disperse local('comp1'), remote('comp1')
    comp2 3, l1
    channel(comp3, comp4)(r1)

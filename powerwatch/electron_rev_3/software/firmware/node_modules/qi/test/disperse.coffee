should = require 'should'
{disperse} = require '..'

describe 'disperse', ->
  it 'should call all callbacks', ->

    yin = (input) ->
      input.should.eql 1

    yang = (input) ->
      input.should.eql 1

    taiji = disperse yin, yang
    taiji 1

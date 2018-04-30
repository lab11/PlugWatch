focus = require './focus'

module.exports = (coll, iterator, done) ->
  next = focus(done)
  for key, val of coll
    iterator val, next(key)

  return undefined

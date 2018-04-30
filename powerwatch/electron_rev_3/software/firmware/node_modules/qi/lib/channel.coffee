module.exports = (fns...) ->
  index = 0

  (args..., done) ->
    callNext = (args...) ->

      unless index < fns.length
        return done null, args...

      fns[index] args..., (err, results...) ->
        return done err, results... if err
        index++
        callNext results...
    callNext args...

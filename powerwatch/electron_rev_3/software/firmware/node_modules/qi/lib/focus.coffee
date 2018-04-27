isInteger = (n) ->
  typeof n is 'number' and n % 1 is 0

module.exports = (done) ->
  counter = 0
  error = null
  results = []

  (ref) ->

    # switch to object results if we get a non-integer ref
    if not isInteger(ref) and results is []
      results = {}

    ref or= counter
    counter++
    called = false

    (err, result) ->
      process.nextTick ->
        return if error
        return if called

        called = true
        if err
          error = err
          return done err, results

        results[ref] = result
        if --counter is 0
          done null, results

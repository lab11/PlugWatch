module.exports = util =

  # return random 16 digits
  random: ->
    digit = -> ((Math.random() * 16) | 0).toString 16
    buffer = []
    for n in [1..16]
      buffer.push digit()

    return buffer.join ''

  curry: (fn, args...) -> fn.bind fn.prototype, args...

  # a thorough type check
  getType: (obj) ->
    ptype = Object.prototype.toString.call(obj).slice 8, -1
    if ptype is 'Object'
      return obj.constructor.name.toString()
    else
      return ptype

  # return a hash containing only the keys provided
  select: (hash, keys...) ->
    return {} unless util.getType(hash) is 'Object'
    newObj = {}
    for k, v of hash
      newObj[k] = v if k in keys
    return newObj

  reject: (hash, keys...) ->
    return {} unless util.getType(hash) is 'Object'
    newObj = {}
    for k, v of hash
      newObj[k] = v if k not in keys
    return newObj

  # given a function, wrap it in naan and curry, then cook it
  # In English: Enables autocurrying, so if you haven't provided the callback
  # yet you'll get a curried function instead of premature execution.
  tandoor: (fn) ->
    naan = (args...) ->
      [_..., last] = args
      unless (fn.length > 0 and args.length >= fn.length) or (fn.length == 0 and util.getType(last) is 'Function')
        return util.curry naan, args...
      fn args...

    return naan

  getString: (thing) ->
    if thing then thing.toString() else null

  hasKeys: (obj, keys) ->
    return false unless obj?
    for k in keys
      return false unless obj[k]?
    return true

  removeAt: (arr, index) ->
    arr.splice index, 1
    return arr

  box: (val) ->
    return [] unless val?
    if util.getType(val) is 'Array'
      val
    else
      [val]

  kvp: (key, value, args...) ->
    return {} unless key?
    kvps = util.kvp args...
    kvps[key] = value
    return kvps

  sample: (emitter, event, n, done) ->
    done ?= ->

    results = []
    placeCb = ->
      if n-- > 0
        emitter.once event, (args...) ->
          results.push args
          placeCb()
      else
        done null, results

    placeCb()

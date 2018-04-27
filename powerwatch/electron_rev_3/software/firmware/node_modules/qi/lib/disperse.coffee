module.exports = (cbs...) ->
  (args...) ->
    cb args... for cb in cbs

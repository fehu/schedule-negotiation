var listByName = function(coll, name, ord){ return coll.find({sender: name}).sort({"time": ord}).map(function(x){ return x.report } ) }


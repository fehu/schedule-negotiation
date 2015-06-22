var listByName = function(coll, name, ord){ return coll.find({sender: name}).sort({"time": ord}).map(function(x){ return x.report } ) }

var getTimetable = function(coll, name){ return coll.find({sender: name, type: "Timetable Report"}).map(function(x){ return x.report } ) }

var timetables = function(coll){ return coll.find( {type: "Timetable Report"} )
                                            .map(function(x){ return x.report } )
                                            .filter(function(x) {return x.length != 117 })
                               }
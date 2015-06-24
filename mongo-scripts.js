var listByName = function(coll, name, ord){ return coll.find({sender: name}).sort({"time": ord}).map(function(x){ return x.report } ) }

var getTimetable = function(coll, name){ return coll.find({sender: name, type: "Timetable Report"}).map(function(x){ return x.report } ) }



colls = [db.groups, db.professors, db.students, db.controller]

var foreach = function(f){ return colls.map(function(x){ return f(x) }) }

foreach(function(x){ return x.distinct("type") })
# schedule-negotiation
Agents negotiation for schedule generation, based on [agent-negotiation](https://github.com/fehu/agent-negotiation) project.

This is **dev** branch.

---

This project implements agents negotiation with goal of forming university schedule. 


The participating agents are representing three different real-world parties: 

1. students or groups,
2. professors,
3. classrooms.

Each participant has it's own timetable and the set of all participants' timetables is the schedule.


The negotiation process is based on asynchronous immutable messages with no guarantee for order preservation.

---

The negotiation goals are:

  1. Forming _groups_ of _students_ for each _discipline_ (except special case of pre-formed groups).
  2. Finding the best suiting _professor_ to teach each _group_, thus forming an _unplaced class_.
  3. Finding a suitable _classroom_ for each _unplaced class_, thus forming _class_.

Negotiation features:
 - [x] Asynchronous immutable message communication.
   - [x] awaiting responses for certain messages;
   - [x] delay for not expected messages (fallen out of order);
   - [x] agents _roles_ system;
   - [x] updatable _scopes_ (known counterparts);
   - [ ] network communication.
 - [x] Multiple simultaneous negotiation support.
   - [x] isolated negotiation variables;
   - [x] shared negotiation variables.
 - [x] Distributed logging support.
   - [x] write into [MongoDB](https://www.mongodb.org/).
 - [ ] [Agents personalization](docs/PersonalizableAgents.md).


---

[API](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html)

[Message Flow Diagram](http://fehu.github.io/schedule-negotiation/docs/MessageFlow.pdf)

[Detailed Message Flow Description](docs/MessageFlowDetailed.md)

[Personalizable Agents](docs/PersonalizableAgents.md)


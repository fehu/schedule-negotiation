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

Uses [MongoDB](https://www.mongodb.org/). 

---

[API](http://fehu.github.io/schedule-negotiation/docs/dev-api/index.html)

[Message Flow Diagram](http://fehu.github.io/schedule-negotiation/docs/MessageFlow.pdf)

[Detailed Message Flow Description](docs/MessageFlowDetailed.md)

[Personalizable Agents](docs/PersonalizableAgents.md)


Personalizable Agents
=====================

The agents are [driven by utility](/src/main/scala/feh/tec/agents/schedule/UtilityDriven.scala), that is defined as 

![utility-formula](https://latex.codecogs.com/png.latex?{
\\mathrm{utility} &= \\begin{cases}
    \\Delta \\mathrm{goal}\\, \\big( w_p \\, \\mathrm{priority} + \\mathrm{pref} \\big)
        & \\forall \\xi \\in \\Xi \\text{ is satisfied}\\\\
    0 & \\text{otherwise}
\\end{cases}\\numberthis
})

where 

- ![Xi-formula](https://latex.codecogs.com/png.latex?{\\Xi\\ = \\{\\xi_i\\} \\mathrm{-\\, restrictions,}})
- ![Wp-formula](https://latex.codecogs.com/png.latex?{w_p \\mathrm{-\\, priority~ weight.}})

It's also mentioned in [MessageFlowDetailed](MessageFlowDetailed.md#utility).

Preference
----------

While _preference_ mechanism hasn't been implemented yet, the utility design included it from the begining. 
It is purpose is to define some extra weak restrictions, reflecting __personal preferences__ of real people.
In order to avoid over-restrictions, the _preference_ function must be decreasing:
![pref-decr-formula](https://latex.codecogs.com/png.latex?{\\forall t_1 > t_0 \\implies pref(t_1) < pref(t_0) }).

Possible preferences:

- Common for _students_ (_groups_) and _professors_
  - min/max pause between classes;
  - time of the classes;
  - classes per day;
  - free days;
  - specific classes on the same day;
  - specific classes not on the same day;
  - specific classes in a row;
  - specific classes not in a row;
  - ...

- Students / Groups:
  - ...

- Professors:
  - ...

- Classrooms
  - occupancy;
  - ...

Preference Description
----------------------

The preference mechanism is intended to be used by the people, represented in the negotiation by corresponding agents.

This requires development of an expressively powerful _domain specific language_ (DSL) for the users to describe their preferences. 

The preference function must be generatd internally from thr description in order to avoid invalid functions
(or there must be an analysis mechanizm for user-provided functions).






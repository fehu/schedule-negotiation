Personalizable Agents
=====================

An the agents are [driven by utility](/src/main/scala/feh/tec/agents/schedule/UtilityDriven.scala), that is defined as 

![utility-formula](https://latex.codecogs.com/png.latex?{
\\mathrm{utility} &= \\begin{cases}
    \\Delta \\mathrm{goal}\\, \\big( w_p \\, \\mathrm{priority} + \\mathrm{pref} \\big)
        & \\forall \\xi \\in \\Xi \\text{ is satisfied}\\\\
    0 & \\text{otherwise}
\\end{cases}\\numberthis
})
</center>

It's also mentioned in [MessageFlowDetailed](MessageFlowDetailed.md#utility).


package feh.tec.agents.test.data

import feh.tec.agents.schedule2.{DTime, PreferencesContainer, AgentContextRelations, ObligationsContainer}

object Common {

  lazy val emptyObligations = ObligationsContainer(
    AgentContextRelations(
      whole = Set(),
      binaryWithin = Set(),
      binaryWithDefaultGraph = Set()
    ),
    Set()
  )

  lazy val emptyPreferences = PreferencesContainer(
    AgentContextRelations(
      whole = Set(),
      binaryWithin = Set(),
      binaryWithDefaultGraph = Set()
    ),
    Set()
  )

  lazy val timeDescriptor = DTime.descriptor(mBegin = 8 * 60, mEnd = 20 * 60, mStep = 30)

}

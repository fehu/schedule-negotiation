package feh.tec.agents.test.data

import feh.tec.agents.comm.{NegotiatingAgentRef, NegotiationRole}
import feh.tec.agents.schedule2.GenericCoherenceDrivenAgent.Capacity.SearchesDisciplines
import feh.tec.agents.schedule2._
import feh.tec.agents.test.data.Common._
import feh.tec.agents.test.data.Disciplines._
import feh.util.InUnitInterval

object Groups {
  val role = new NegotiationRole("group")

  def group(id: String, needs: Set[Discipline], obligations: ObligationsContainer, preferences: PreferencesContainer) = {
    val ik = Knowledge(Set(SearchesDisciplines(needs)), obligations.knowledge, preferences.knowledge)
    val rels = AgentRelations(obligations.ctx, preferences.ctx)

    AgentDescriptor(id, role, ik, rels, () => InUnitInterval.Including(0.5), () => InUnitInterval.Including(0.3))
  }

  def groupsDescriptors = Set(
    group("G0", Set(A1, A2, B1, B2, C1), emptyObligations, emptyPreferences),
    group("G1", Set(A1, A2, A3, B1, B2), emptyObligations, emptyPreferences),
    group("G2", Set(A1, A2, A3, B1, B2), emptyObligations, emptyPreferences),
    group("G3", Set(C1, C2, C3, A1, B2), emptyObligations, emptyPreferences),
    group("G4", Set(C1, C2, C3, A1, A2), emptyObligations, emptyPreferences),
    group("G5", Set(B1, B2, B3, C1, A2), emptyObligations, emptyPreferences),
    group("G6", Set(B1, B2, B3, C1, A1), emptyObligations, emptyPreferences),
    group("G7", Set(A4, A5, C3),         emptyObligations, emptyPreferences),
    group("G8", Set(B4, B5, A3),         emptyObligations, emptyPreferences),
    group("G9", Set(C4, C5, A3, B3),     emptyObligations, emptyPreferences)
  )

  def create(creator: Group.Creator): Set[NegotiatingAgentRef] = groupsDescriptors map creator.create

}

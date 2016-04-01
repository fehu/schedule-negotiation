package feh.tec.agents.test.data

import feh.tec.agents.comm.{NegotiatingAgentRef, NegotiationRole}
import feh.tec.agents.schedule2.Coherence.Contexts.ObligationFailed
import feh.tec.agents.schedule2.GenericCoherenceDrivenAgent.Capacity.CanTeach
import feh.tec.agents.schedule2._
import feh.tec.agents.test.data.Common._
import feh.tec.agents.test.data.Disciplines._
import feh.util.InUnitInterval

object Professors {
  val fullTime = NegotiationRole("Professor", Some("full"))
//  val partTime = NegotiationRole("Professor", Some("part"))

  def prof(id: String, canTeach: Set[Discipline], obligations: ObligationsContainer, preferences: PreferencesContainer) =
  {
    val ik = Knowledge(Set(CanTeach(canTeach)), obligations.knowledge, preferences.knowledge)
    val rels = AgentRelations(obligations.ctx, preferences.ctx)

    AgentDescriptor(id, fullTime, ik, rels, () => InUnitInterval.Including(0.5), () => InUnitInterval.Including(0.3))
  }

  case object ExceedsMaximumClasses extends ObligationFailed{ def message = "Exceeds Maximum Classes" }
  case object NotEnoughClasses      extends ObligationFailed{ def message = "Not Enough Classes" }

  case class MinMaxClasses(min: Int, max: Int) extends Coherence.RelationWhole[Coherence.Contexts.Obligations]{
    def apply(g: Coherence.Graph): Coherence.Contexts.Obligations#Value = g.nodes.size match {
      case n if n > max => Some(false -> Some(ExceedsMaximumClasses))
      case n if n < min => Some(false -> Some(NotEnoughClasses))
      case _            => Some(true  -> None)
    }
  }

  lazy val profObligations = ObligationsContainer(
    AgentContextRelations(
      whole = Set(MinMaxClasses(2, 5)),
      binaryWithin = Set(),
      binaryWithDefaultGraph = Set()
    ),
    Set()
  )


  def professorsDescriptors = Set(
    prof("P1", Set(A1, A2, A3),     profObligations, emptyPreferences),
    prof("P2", Set(B1, B2, B3),     profObligations, emptyPreferences),
    prof("P3", Set(C1, C2, C3),     profObligations, emptyPreferences),
    prof("P4", Set(A3, A4, A5),     profObligations, emptyPreferences),
    prof("P5", Set(B3, B4, B5),     profObligations, emptyPreferences),
    prof("P6", Set(C4, C5),         profObligations, emptyPreferences),
    prof("P7", Set(A1, A2, B1, B2), profObligations, emptyPreferences),
    prof("P8", Set(C1, C2, C3, B2), profObligations, emptyPreferences),
    prof("P9", Set(A3, A4),         profObligations, emptyPreferences)
  )

  def create(creator: Professor.Creator): Set[NegotiatingAgentRef] = professorsDescriptors map creator.create

}

package feh.tec.agents.schedule

import feh.tec.agents.comm.Negotiation
import feh.util.InUnitInterval

trait AbstractDecider

trait AbstractAssessor

trait ClassesBasicPreferencesDecider[Time] extends AbstractDecider{
  def whatDay_? (neg: Negotiation)                            : DayOfWeek
  def whatTime_?(neg: Negotiation, onDay: DayOfWeek)          : Time
  def howLong_? (neg: Negotiation, onDay: DayOfWeek, at: Time): Int // minutes
}

trait ClassesBasicPreferencesAssessor[Time] extends AbstractDecider{
  def assess( discipline: Discipline
            , length    : Int           = -1
            , onDay     : DayOfWeek     = null
            , at        : Option[Time]  = None      ): InUnitInterval
}


trait TimeAwareDecider  extends AbstractDecider {  def timeElapsed: Long  }
trait TimeAwareAssessor extends AbstractAssessor{  def timeElapsed: Long  }
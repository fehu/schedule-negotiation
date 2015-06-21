package feh.tec.agents.schedule

@deprecated
case class SchedulePolicy( partTimeStrictlyAfterFullTime: Boolean
                         , @deprecated("should be defined for DISCIPLINE, not global") maxStudentsInGroup: Int

                           )
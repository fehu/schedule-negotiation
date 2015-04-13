package feh.tec.agents.schedule

import feh.tec.agents.util.{FromString, AsString}

case class Discipline(code: String, name: String)

object DaysOfWeek extends Enumeration{
  val Mon, Tue, Wed, Thu, Sat, Sun = Value
}

case class Class[ProfName, Time]( discipline: Discipline
                                , dayOfWeek : DayOfWeek
                                , begins    : Time
                                , ends      : Time
                                , group     : GroupId
                                , professor : ProfessorId[ProfName]
                                , classroom : ClassRoomId
                                  )

case class GroupId          (uniqueId: String) extends AsString{  def asString = uniqueId  }
case class ProfessorId[Name](name    : Name  ) extends AsString{  def asString = name.toString  }
case class ClassRoomId() // todo

object Ids{
  implicit def groupIdFromString                      : FromString[GroupId]           = FromString( GroupId(_) )
  implicit def professorIdFromString[Name: FromString]: FromString[ProfessorId[Name]] = FromString( 
    name => 
      ProfessorId( implicitly[FromString[Name]].fromString(name) ) 
  )
}
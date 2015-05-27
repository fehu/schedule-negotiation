package feh.tec.agents.schedule

import akka.event.LoggingAdapter
import feh.tec.agents.schedule.AbstractDecider.DecideInterfaceDecideImpl
import feh.util._

import scala.annotation.tailrec

//import feh.tec.agents.schedule.ClassesBasicPreferencesDeciderImpl.DeciderPreferences

abstract class ClassesBasicPreferencesDeciderImplementations[Time: TimeDescriptor]   //(val preferences: DeciderPreferences[Time])
  extends ClassesBasicPreferencesDecider[Time]
  with DecideInterfaceDecideImpl
{
  def basedOn(p: Param[_]*): AbstractDecideInterface

  abstract class AbstractDecideInterface(val params: Seq[AbstractDecider#Param[_]]) extends DecideImpl {

    protected def decide_[A](a: DecisionDescriptor[A], prev: Map[DecisionDescriptor[Any], Decision[Any]]) = a match {
      case `whatDay_?`  => decideDay(prev).asInstanceOf[Decision[A]]
      case `whatTime_?` => decideTime(prev).asInstanceOf[Decision[A]]
      case `howLong_?`  => decideLength(prev).asInstanceOf[Decision[A]]
    }


    protected def decideDay(prev: Map[DecisionDescriptor[Any], Decision[Any]]): Decision[DayOfWeek]
    protected def decideTime(prev: Map[DecisionDescriptor[Any], Decision[Any]]): Decision[Time]
    protected def decideLength(prev: Map[DecisionDescriptor[Any], Decision[Any]]): Decision[Int]
  }

  
  trait DecideLength{
    self: AbstractDecideInterface =>
    
    
    def lengthDiscr: Int

    def possibleLengths(totalLength: Int) = {
      assert(totalLength % lengthDiscr == 0, s"discipline minutes $totalLength cannot be divided by $lengthDiscr with integer result")
      val n = totalLength / lengthDiscr
      for(i <- 1 to n) yield i*lengthDiscr
    }

  }
  
  trait TimeConstraints{
    self: AbstractDecideInterface /*with DecideLength */=>

    protected lazy val tdescr = implicitly[TimeDescriptor[Time]]

    def timetable: TimeTableRead[Time]


    /** check that there is no class at given time
      *  ensure that will end before the closing time
      */
    def satisfiesConstraints(day: DayOfWeek, time: Time, length: Int): Boolean = {
      satisfiesConstraints(time, length) && timetable.busyAt(day, time, tdescr.plus(time, length))
    }

    /** ensure that will end before the closing time */
    def satisfiesConstraints(time: Time, length: Int): Boolean = {
      tdescr.toMinutes(time) + length <= tdescr.toMinutes(tdescr.ending)
    }

//    @deprecated("has to know the length")
//    def satisfiesConstraints(time: Time): Boolean = ???
  }

  
  class DecideRandom(params: Seq[AbstractDecider#Param[_]],
                     val lengthDiscr: Int,
                     val disciplineTotalLength: Int,
                     val timetable: TimeTableRead[Time],
                     log: LoggingAdapter  // todo
                      )
    extends AbstractDecideInterface(params)
    with DecideLength
    with TimeConstraints
  {
//    protected lazy val discipline = params.find(_.productPrefix == disciplineParam) getOrThrow "No discipline found in the parameters"

    protected def decideDay(prev: Map[DecisionDescriptor[Any], Decision[Any]]): Decision[DayOfWeek] =
      Decision(Left( (DaysOfWeek.values - DaysOfWeek.Sun).randomChoice.get ))

    protected def decideTime(prev: Map[DecisionDescriptor[Any], Decision[Any]]): Decision[Time] ={
      def get = tdescr.randomly

      Decision(
        try Left(
          prev.get(howLong_?)
            .map{
                  _len =>
                    val len = _len.value.left.get.asInstanceOf[Int]
                    prev.get(whatDay_?).map{  day => fetchUntil(satisfiesConstraints(day.value.left.get.asInstanceOf[DayOfWeek], _: Time, len), get) }
                    .getOrElse( fetchUntil(satisfiesConstraints(_: Time, len), get) )
                }
            .getOrElse( get.head ) // no satisfaction check
        )
        catch {
          case ex: Throwable => Right(ex)
        }
      )
    }
      

    protected def decideLength(prev: Map[DecisionDescriptor[Any], Decision[Any]]): Decision[Int] =
      Decision( Left( possibleLengths(disciplineTotalLength).randomChoice.get ) )

  }
  
  // @tailrec
  protected def fetchUntil[T](cond: T => Boolean, it: Iterable[T], maxTries: Int = 100): T = {
    val res = it.head
    if(cond(res)) res
    else if(maxTries == 0) sys.error("couldn't find a value satisfying the condition")
    else fetchUntil(cond, it.tail, maxTries - 1)
  }
}

//object ClassesBasicPreferencesDeciderImpl{
//  case class DeciderPreferences[Time](days: Ordered[DayOfWeek], time: Ordered[Time], length: Ordered[Int])
//}
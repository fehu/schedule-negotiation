package feh.tec.agents.schedule


import scala.language.implicitConversions
import feh.util.InUnitInterval

trait AbstractDecider{

  abstract class DecisionDescriptor[+T](val name: String)

  case class Decision[+T](value: Either[T, Throwable])

  abstract class ParamDescriptor[T](val name: String)
  case class Param[T](d: ParamDescriptor[T], value: T) // todo: a lot of code to get value: d.value.left.get.asInstanceOf[...]

  def getParam[T](d: ParamDescriptor[T], in: Seq[Param[_]]): Param[T] = in.find(_.d == d).get.asInstanceOf[Param[T]]
  
  implicit def pairToParam[T](p: (ParamDescriptor[T], T)): Param[T] = Param(p._1, p._2)

  trait DecideInterface{
    def decide[A](a: DecisionDescriptor[A]): Decision[A]

    def decide[A, B](a: DecisionDescriptor[A],
                     b: DecisionDescriptor[B]): (Decision[A], Decision[B])

    def decide[A, B, C](a: DecisionDescriptor[A],
                        b: DecisionDescriptor[B],
                        c: DecisionDescriptor[C]): (Decision[A], Decision[B], Decision[C])
  }

  def basedOn(p: Param[_]*): DecideInterface

}

object AbstractDecider{
  
  trait DecideInterfaceDecideImpl {
    self: AbstractDecider =>

    trait DecideImpl extends DecideInterface{
      protected def decide_[A](a: DecisionDescriptor[A], prev: Map[DecisionDescriptor[Any], Decision[Any]]): Decision[A]
      protected def decide_[A](a: DecisionDescriptor[A], prev: Seq[(DecisionDescriptor[Any], Decision[Any])]): Decision[A] =
        decide_(a, prev.toMap)

      def decide[A](a: DecisionDescriptor[A]): Decision[A] = decide_(a, Nil)
      def decide[A, B](a: DecisionDescriptor[A], b: DecisionDescriptor[B]): (Decision[A], Decision[B]) = {
        val ad = decide_(a, Nil)
        val bd = decide_(b, Seq( (a -> ad).asInstanceOf[(DecisionDescriptor[Any], Decision[Any])] ))
        (ad, bd)
      }

      def decide[A, B, C](a: DecisionDescriptor[A], b: DecisionDescriptor[B], c: DecisionDescriptor[C]): (Decision[A], Decision[B], Decision[C]) = {
        val ad = decide_(a, Nil)
        val bd = decide_(b, Seq( (a -> ad).asInstanceOf[(DecisionDescriptor[Any], Decision[Any])] ))
        val cd = decide_(c, Seq(
          (a -> ad).asInstanceOf[(DecisionDescriptor[Any], Decision[Any])]
          , (b -> bd).asInstanceOf[(DecisionDescriptor[Any], Decision[Any])]
        ))
        (ad, bd, cd)
      }
    }

  }
}

trait AbstractAssessor

trait ClassesBasicPreferencesDecider[Time] extends AbstractDecider{

  object whatDay_?  extends  DecisionDescriptor[DayOfWeek]("day")
  object whatTime_? extends  DecisionDescriptor[Time]("time")
  object howLong_?  extends  DecisionDescriptor[Int]("length") // minutes

//  object negotiationParam extends ParamDescriptor("negotiation")
  object disciplineParam  extends ParamDescriptor[Discipline]("discipline")
  object dayParam         extends ParamDescriptor[DayOfWeek]("day")
  object timeParam        extends ParamDescriptor[Any]("time")
  object lengthParam      extends ParamDescriptor[Int]("length")

}

trait ClassesBasicPreferencesAssessor[Time] extends ClassesBasicPreferencesDecider[Time]{
  def assess( discipline: Discipline
            , length    : Int
            , onDay     : DayOfWeek
            , at        : Time      ): InUnitInterval
}


trait TimeAwareDecider  extends AbstractDecider {  def timeElapsed: Long  }
trait TimeAwareAssessor extends AbstractAssessor{  def timeElapsed: Long  }

trait CounterDecider {
  self: AbstractDecider =>

  type Proposal

  def counterPropose[R](prop: Proposal)(f: => R): R
}


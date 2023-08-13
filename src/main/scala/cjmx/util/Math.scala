package cjmx.util

object Math {

  def liftToBigDecimal: AnyRef => Option[BigDecimal] = _ match {
    case bi: java.math.BigInteger => Some(BigDecimal(new BigInt(bi)))
    case sbi: scala.math.BigInt => Some(BigDecimal(sbi))
    case bd: java.math.BigDecimal => Some(BigDecimal(bd))
    case sbd: scala.math.BigDecimal => Some(sbd)
    case s: java.lang.Short => Some(BigDecimal((s: Short).toInt))
    case i: java.lang.Integer => Some(BigDecimal(i))
    case l: java.lang.Long => Some(BigDecimal(l))
    case f: java.lang.Float => Some(BigDecimal((f: Float).toDouble))
    case d: java.lang.Double => Some(BigDecimal(d))
    case _ => None
  }

}

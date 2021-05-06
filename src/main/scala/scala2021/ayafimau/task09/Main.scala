package scala2021.ayafimau.task09

object Main extends App {

  val result = 42(USD) + 35(EUR)

  val resultToPound = result to GBP

  println(resultToPound)


  implicit class MoneyOps[T](x: T)(implicit n: Numeric[T]) {

    def apply(currency: Currency): Money = {
      // NOTE: toString is surprisingly a recommended method for converting Double to BigDecimal (due to rounding / precision)
      Money(BigDecimal(x.toString), currency)
    }
  }

  case class Money(sum: BigDecimal, currency: Currency) {

    // default operator + to USD conversion as the vase currency,  may be discussed as per requirements
    def +(that: Money): Money = Money(to(USD) + that.to(USD), USD)

    def to(currencyTo: Currency): Double = {
      if (currency == currencyTo) // just not to lose precision on conversion back & forth
        this.sum.setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
      else {
        val sumInUSD = sum * ratesToUSD(currency)
        val sumInTarget = sumInUSD / ratesToUSD(currencyTo)
        sumInTarget.setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
      }
    }

    // denotes conversion rates of 1 "currency unit" to USD
    val ratesToUSD = Map(USD -> 1.0, EUR -> 1.2, GBP -> 1.39)
  }

  trait PS extends Product with Serializable

  sealed trait Currency extends PS // to be used in Maps

  case object USD extends Currency

  case object EUR extends Currency

  case object GBP extends Currency

}

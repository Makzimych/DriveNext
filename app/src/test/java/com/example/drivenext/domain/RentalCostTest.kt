// ЛР4. Проверки расчёта стоимости и допустимости дат аренды.
package com.example.drivenext.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
class RentalCostTest {
 private val start=LocalDateTime.of(2026,10,20,8,0)
 @Test fun threeDaysIncludeInsuranceButExcludeRefundableDeposit() {
  val cost=RentalCost.calculate(2500,300,15000,start,start.plusDays(3))
  assertEquals(3L,cost.days);assertEquals(7500L,cost.rent);assertEquals(900L,cost.insurance)
  assertEquals(8400L,cost.total);assertEquals(15000L,cost.deposit)
 }
 @Test fun partialDaysRoundUp() {
  assertEquals(2L,RentalCost.calculate(2500,300,0,start,start.plusDays(1).plusMinutes(1)).days)
  assertEquals(1L,RentalCost.calculate(2500,300,0,start,start.plusNanos(1)).days)
 }
 @Test(expected=IllegalArgumentException::class) fun equalDatesAreInvalid() {RentalCost.calculate(2500,300,0,start,start)}
 @Test(expected=IllegalArgumentException::class) fun reversedDatesAreInvalid() {RentalCost.calculate(2500,300,0,start,start.minusDays(1))}
}

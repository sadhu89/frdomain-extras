package frdomain.ch6
package domain
package model

import java.util.{ Date, Calendar }

import cats._
import cats.data._
import cats.implicits._
import cats.instances.all._

import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.{ Uuid, ValidBigDecimal }
import eu.timepit.refined.types.string.NonEmptyString
import _root_.io.estatico.newtype.macros.newtype

import common._


object account {

  @newtype case class AccountNo(value: String)
  @newtype case class AccountName(value: String)
  
  
  case class Balance(amount: Amount = 0)
  sealed trait Account {
    def no: AccountNo
    def name: AccountName
    def dateOfOpen: Option[Date]
    def dateOfClose: Option[Date]
    def balance: Balance
  }
  
  final case class CheckingAccount (no: AccountNo, name: AccountName,
    dateOfOpen: Option[Date], dateOfClose: Option[Date] = None, balance: Balance = Balance()) extends Account
  
  final case class SavingsAccount (no: AccountNo, name: AccountName, rateOfInterest: Amount, 
    dateOfOpen: Option[Date], dateOfClose: Option[Date] = None, balance: Balance = Balance()) extends Account
  
  object Account {
  
    private def validateAccountNo(no: AccountNo): ValidationResult[AccountNo] = 
      if (no.value.isEmpty || no.value.size < 5) s"Account No has to be at least 5 characters long: found $no".invalidNel
      else no.validNel
  
    private def validateOpenCloseDate(od: Date, cd: Option[Date]): ValidationResult[(Option[Date], Option[Date])] = cd.map { c => 
      if (c before od) s"Close date [$c] cannot be earlier than open date [$od]".invalidNel
      else (od.some, cd).validNel
    }.getOrElse { (od.some, cd).validNel }
  
    private def validateRate(rate: BigDecimal): ValidationResult[BigDecimal] =
      if (rate <= BigDecimal(0)) s"Interest rate $rate must be > 0".invalidNel
      else rate.validNel
  
    def checkingAccount(no: AccountNo, name: AccountName, openDate: Option[Date], closeDate: Option[Date], 
      balance: Balance): ErrorOr[Account] = { 
  
      ( 
        validateAccountNo(no),
        validateOpenCloseDate(openDate.getOrElse(today), closeDate)
  
      ).mapN {(n, d) =>
        CheckingAccount(n, name, d._1, d._2, balance)
      }.toEither
    }
  
    def savingsAccount(no: AccountNo, name: AccountName, rate: BigDecimal, openDate: Option[Date], 
      closeDate: Option[Date], balance: Balance): ErrorOr[Account] = { 
  
      (
        validateAccountNo(no),
        validateOpenCloseDate(openDate.getOrElse(today), closeDate),
        validateRate(rate)
  
      ).mapN {(n, d, r) =>
        SavingsAccount(n, name, r, d._1, d._2, balance)
      }.toEither
    }
  
    private def validateAccountAlreadyClosed(a: Account): ValidationResult[Account] = {
      if (a.dateOfClose isDefined) s"Account ${a.no} is already closed".invalidNel
      else a.validNel
    }
  
    private def validateCloseDate(a: Account, cd: Date): ValidationResult[Date] = {
      if (cd before a.dateOfOpen.get) s"Close date [$cd] cannot be earlier than open date [${a.dateOfOpen.get}]".invalidNel
      else cd.validNel
    }
  
    def close(a: Account, closeDate: Date): ErrorOr[Account] = {
      (validateAccountAlreadyClosed(a), validateCloseDate(a, closeDate)).mapN {(acc, _) =>
        acc match {
          case c: CheckingAccount => c.copy(dateOfClose = Some(closeDate))
          case s: SavingsAccount  => s.copy(dateOfClose = Some(closeDate))
        }
      }.toEither
    }
  
    private def checkBalance(a: Account, amount: Amount): ValidationResult[Account] = {
      if (amount < 0 && a.balance.amount < -amount) s"Insufficient amount in ${a.no} to debit".invalidNel
      else a.validNel
    }
  
    def updateBalance(a: Account, amount: Amount): ErrorOr[Account] = {
      (validateAccountAlreadyClosed(a), checkBalance(a, amount)).mapN { (_, _) =>
        a match {
          case c: CheckingAccount => c.copy(balance = Balance(c.balance.amount + amount))
          case s: SavingsAccount  => s.copy(balance = Balance(s.balance.amount + amount))
        }
      }.toEither
    }
  
    def rate(a: Account) = a match {
      case SavingsAccount(_, _, r, _, _, _) => r.some
      case _ => None
    }
  }
}
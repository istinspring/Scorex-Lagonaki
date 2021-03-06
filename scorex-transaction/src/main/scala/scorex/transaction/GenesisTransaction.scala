package scorex.transaction

import com.google.common.primitives.{Bytes, Ints, Longs}
import play.api.libs.json.Json
import scorex.account.Account
import scorex.crypto.Base58
import scorex.transaction.LagonakiTransaction.TransactionType
import scorex.crypto.Sha256._


case class GenesisTransaction(override val recipient: Account,
                              override val amount: Long,
                              override val timestamp: Long)
  extends LagonakiTransaction(TransactionType.GenesisTransaction, recipient, amount, 0, timestamp,
    GenesisTransaction.generateSignature(recipient, amount, timestamp)) {

  import scorex.transaction.GenesisTransaction._
  import scorex.transaction.LagonakiTransaction._

  override def json() =
    jsonBase() ++ Json.obj("recipient" -> recipient.address, "amount" -> amount.toString)

  override lazy val creator: Option[Account] = None

  override def bytes() = {
    val typeBytes = Array(TransactionType.GenesisTransaction.id.toByte)

    val timestampBytes = Bytes.ensureCapacity(Longs.toByteArray(timestamp), TimestampLength, 0)

    val amountBytes = Bytes.ensureCapacity(Longs.toByteArray(amount), AmountLength, 0)

    val rcpBytes = Base58.decode(recipient.address).get
    require(rcpBytes.length == Account.AddressLength)

    val res = Bytes.concat(typeBytes, timestampBytes, rcpBytes, amountBytes)
    require(res.length == dataLength)
    res
  }

  override lazy val dataLength = TypeLength + BASE_LENGTH

  def isSignatureValid() = {
    val typeBytes = Bytes.ensureCapacity(Ints.toByteArray(TransactionType.GenesisTransaction.id), TypeLength, 0)
    val timestampBytes = Bytes.ensureCapacity(Longs.toByteArray(timestamp), TimestampLength, 0)
    val amountBytes = Bytes.ensureCapacity(Longs.toByteArray(amount), AmountLength, 0)
    val data = Bytes.concat(typeBytes, timestampBytes,
      Base58.decode(recipient.address).get, amountBytes)

    val h = hash(data)
    Bytes.concat(h, h).sameElements(signature)
  }

  override def validate()(implicit transactionModule: SimpleTransactionModule) =
    if (amount < 0) {
      ValidationResult.NegativeAmount
    } else if (!Account.isValidAddress(recipient.address)) {
      ValidationResult.InvalidAddress
    } else ValidationResult.ValidateOke

  override def involvedAmount(account: Account): Long =
    if (recipient.address.equals(account.address)) amount else 0

  override def balanceChanges(): Map[Account, Long] = Map(recipient -> amount)
}


object GenesisTransaction {

  import scorex.transaction.LagonakiTransaction._

  private val RECIPIENT_LENGTH = Account.AddressLength
  private val BASE_LENGTH = TimestampLength + RECIPIENT_LENGTH + AmountLength

  def generateSignature(recipient: Account, amount: BigDecimal, timestamp: Long) = {
    val typeBytes = Bytes.ensureCapacity(Ints.toByteArray(TransactionType.GenesisTransaction.id), TypeLength, 0)
    val timestampBytes = Bytes.ensureCapacity(Longs.toByteArray(timestamp), TimestampLength, 0)
    val amountBytes = amount.bigDecimal.unscaledValue().toByteArray
    val amountFill = new Array[Byte](AmountLength - amountBytes.length)

    val data = Bytes.concat(typeBytes, timestampBytes,
      Base58.decode(recipient.address).get, Bytes.concat(amountFill, amountBytes))

    val h = hash(data)
    Bytes.concat(h, h)
  }

  def parse(data: Array[Byte]): LagonakiTransaction = {
    require(data.length >= BASE_LENGTH, "Data does not match base length")

    var position = 0

    //READ TIMESTAMP
    val timestampBytes = java.util.Arrays.copyOfRange(data, position, position + TimestampLength)
    val timestamp = Longs.fromByteArray(timestampBytes)
    position += TimestampLength

    //READ RECIPIENT
    val recipientBytes = java.util.Arrays.copyOfRange(data, position, position + RECIPIENT_LENGTH)
    val recipient = new Account(Base58.encode(recipientBytes))
    position += RECIPIENT_LENGTH

    //READ AMOUNT
    val amountBytes = java.util.Arrays.copyOfRange(data, position, position + AmountLength)
    val amount = Longs.fromByteArray(amountBytes)

    GenesisTransaction(recipient, amount, timestamp)
  }
}
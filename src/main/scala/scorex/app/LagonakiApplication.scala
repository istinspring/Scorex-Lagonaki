package scorex.app

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import com.typesafe.config.ConfigFactory
import scorex.account.{Account, PrivateKeyAccount, PublicKeyAccount}
import scorex.app.api.http.HttpServiceActor
import scorex.block.Block
import scorex.consensus.ConsensusModule
import scorex.consensus.nxt.NxtLikeConsensusModule
import scorex.consensus.qora.QoraLikeConsensusModule
import scorex.network.message._
import scorex.network.{BlockchainSyncer, NetworkController}
import scorex.transaction.LagonakiTransaction.ValidationResult
import scorex.transaction._
import scorex.transaction.state.database.UnconfirmedTransactionsDatabaseImpl
import scorex.transaction.state.wallet.Wallet
import scorex.utils.{NTP, ScorexLogging}
import spray.can.Http

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LagonakiApplication(val settingsFilename: String) extends ScorexLogging {
  private val appConf = ConfigFactory.load().getConfig("app")
  implicit val consensusModule: ConsensusModule[_] = appConf.getString("consensusAlgo") match {
    case "NxtLikeConsensusModule" => new NxtLikeConsensusModule
    case "QoraLikeConsensusModule" => new QoraLikeConsensusModule
    case algo =>
      log.error(s"Unknown consensus algo: $algo. Use NxtLikeConsensusModule instead.")
      new NxtLikeConsensusModule
  }

  implicit val settings = new LagonakiSettings(settingsFilename)
  implicit val transactionModule = new SimpleTransactionModule

  lazy val storedState = transactionModule.state
  lazy val blockchainStorage = transactionModule.history

  private implicit lazy val actorSystem = ActorSystem("lagonaki")
  lazy val networkController = actorSystem.actorOf(Props(classOf[NetworkController], this))
  lazy val blockchainSyncer = actorSystem.actorOf(Props(classOf[BlockchainSyncer], this))

  private lazy val walletFileOpt = settings.walletDirOpt.map(walletDir => new java.io.File(walletDir, "wallet.s.dat"))
  lazy val wallet = new Wallet(walletFileOpt, settings.walletPassword, settings.walletSeed.get)

  def checkGenesis(): Unit = {
    if (blockchainStorage.isEmpty) {
      val genesisBlock = Block.genesis()
      storedState.processBlock(genesisBlock)
      blockchainStorage.appendBlock(genesisBlock).ensuring(_.height() == 1)
      log.info("Genesis block has been added to the state")
    }
  }.ensuring(blockchainStorage.height() >= 1)

  def run() {
    require(transactionModule.balancesSupport)
    require(transactionModule.accountWatchingSupport)

    checkGenesis()

    blockchainSyncer ! BlockchainSyncer.CheckState

    val httpServiceActor = actorSystem.actorOf(Props(classOf[HttpServiceActor], this), "http-service")
    val bindCommand = Http.Bind(httpServiceActor, interface = "0.0.0.0", port = settings.rpcPort)
    IO(Http) ! bindCommand

    //CLOSE ON UNEXPECTED SHUTDOWN
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() {
        stopAll()
      }
    })
  }

  def stopAll() = synchronized {
    log.info("Stopping message processor")
    networkController ! NetworkController.ShutdownNetwork

    log.info("Stopping actors (incl. block generator)")
    actorSystem.shutdown()

    //CLOSE WALLET
    log.info("Closing wallet")
    wallet.close()

    //TODO catch situations when we need this and remove
    Future {
      Thread.sleep(10000)
      log.error("Halt app!")
      Runtime.getRuntime.halt(0)
    }

    //FORCE CLOSE
    log.info("Exiting from the app...")
    System.exit(0)

  }

  def onNewOffchainTransaction(transaction: LagonakiTransaction) =
    if (UnconfirmedTransactionsDatabaseImpl.putIfNew(transaction)) {
      networkController ! NetworkController.BroadcastMessage(TransactionMessage(transaction))
    }

  def createPayment(sender: PrivateKeyAccount, recipient: Account, amount: Long, fee: Long): PaymentTransaction = {
    val time = NTP.correctedTime()
    val sig = PaymentTransaction.generateSignature(sender, recipient, amount, fee, time)
    val payment = new PaymentTransaction(new PublicKeyAccount(sender.publicKey), recipient, amount, fee, time, sig)
    if (payment.validate() == ValidationResult.ValidateOke) {
      onNewOffchainTransaction(payment)
    }
    payment
  }
}
package akka

import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client.AsyncHandler
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.HttpResponseBodyPart
import com.ning.http.client.HttpResponseHeaders
import com.ning.http.client.HttpResponseStatus
import com.ning.http.client.Response

import akka.actor.Actor.actorOf
import akka.actor.Actor
import akka.actor.PoisonPill
import akka.config.Supervision.OneForOneStrategy
import akka.config.Supervision.Permanent
import akka.dispatch.Dispatchers
import akka.event.EventHandler
import akka.routing.Routing.Broadcast
import akka.routing.CyclicIterator
import akka.routing.Routing

object FeedReader extends App {

  read(nrOfFetchers = 10, nrOfFeeds = 5, fillingRate = 1)

  // ====================
  // ===== Messages =====
  // ====================
  sealed trait Message
  case object Read extends Message
  case class Fetch(url: String) extends Message
  case class CompleteFetch(url: String) extends Message

  object Feed {
    // This is a mock implementation. In production, it should connect to a datastore / database.
    def list(nrOfFeeds: Int) = {
      Feed("http://feeds.feedburner.com/TechCrunch")::Feed("http://feeds.reuters.com/reuters/topNews"):: Nil
    }
  }
  
  case class Feed(val url: String)

  class Reader(nrOfFetchers: Int, nrOfFeeds: Int, fillingRate: Int) extends Actor {
    var nrOfFetching: Int = _

    self.faultHandler = OneForOneStrategy(List(classOf[Throwable]), 5, 5000)

    // Create the fetchers
    val fetchers = Vector.fill(nrOfFetchers)(actorOf[Fetcher])

    // Wrap them with a load-balancing router
    val router = Routing.loadBalancerActor(CyclicIterator(fetchers)).start()

    // Phase 1, can accept a Read message
    def fromClient: Receive = {
      case Read =>
        // Get feeds
        val feeds = Feed.list(nrOfFeeds - nrOfFetching)

        // Update number of currently fetching
        nrOfFetching = nrOfFetching + feeds.size

        // Schedule fetcher
        for (feed <- feeds) router ! Fetch(feed.url)
    }

    // Phase 2, accept complete ack from the fetcher
    def fromFetcher: Receive = {
      case CompleteFetch(url) =>
        nrOfFetching -= 1

        EventHandler.info(this, "Finish: %s".format(url))

        // When the number of completed fetchers reach the filling rate, ask the reader to assign work
        // to the fetchers
        if (nrOfFeeds - nrOfFetching >= fillingRate) {
          self ! Read
        }
    }

    // From client or from fetchers
    def receive = fromClient orElse fromFetcher

    // Linking up our fetchers with this reader as to supervise them
    override def preStart = fetchers foreach { self.startLink(_) }

    // When we are stopped, stop our team of fetchers and our router
    override def postStop() {
      // Unlinked all fetchers
      fetchers.foreach(self.unlink(_))

      // Send a PoisonPill to all fetchers telling them to shut down themselves
      router ! Broadcast(PoisonPill)

      // Send a PoisonPill to the router, telling him to shut himself down
      router ! PoisonPill
    }
  }

  object Fetcher {
    val dispatcher =
      Dispatchers.newExecutorBasedEventDrivenWorkStealingDispatcher("pool")
        .setCorePoolSize(100)
        .setMaxPoolSize(100)
        .build
  }

  class Fetcher extends Actor {
    self.lifeCycle = Permanent
    self.dispatcher = Fetcher.dispatcher

    def receive = {
      case Fetch(url) =>
        // Fetch the content
        val httpHandler = new AsyncHandler[Response]() {
          val builder =
            new Response.ResponseBuilder()

          def onThrowable(t: Throwable) {
            EventHandler.error(this, t.getMessage)
          }

          def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = {
            builder.accumulate(bodyPart)
            STATE.CONTINUE
          }

          def onStatusReceived(responseStatus: HttpResponseStatus) = {
            STATE.CONTINUE
          }
          def onHeadersReceived(headers: HttpResponseHeaders) = {
            EventHandler.info(this, "Content-Type: %s".format(headers.getHeaders.getFirstValue("Content-Type")))
            STATE.CONTINUE
          }

          def onCompleted() = {
            builder.build()
          }
        }

        val client = new AsyncHttpClient
        
        // Please note that the following is blocking. To use Akka capability, one way
        // is to wrap this in Akka Future and remove the call to "get" as in:
        // https://github.com/HendraWijaya/syndor/blob/master/syndor-feedbot/src/main/scala/syndor/feedbot/UrlFetcher.scala
        val response = client.prepareGet(url).execute(httpHandler).get() // Blocking

        self reply CompleteFetch(url)
    }
  }

  def read(nrOfFetchers: Int, nrOfFeeds: Int, fillingRate: Int) {
    // create the reader
    val reader = actorOf(new Reader(nrOfFetchers, nrOfFeeds, fillingRate)).start()

    //send Read message
    reader ! Read
  }
}

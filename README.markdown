Demonstration of highly concurrent feed fetchers using Akka and Async HTTP Client. It also shows how to use appropriate dispatcher and load balancer in Akka for maximum performance.

## Dependencies
All dependencies are setup in build.sbt

  1. [akka-actor 1.2-RC3](http://www.akka.io/)
  2. [async-http-client 1.6.4](https://github.com/sonatype/async-http-client) 
  3. [slf4j-nop 1.6.0](http://www.slf4j.org/)

## Running
This project is built with [sbt](https://github.com/harrah/xsbt) so you need to have it setup first in your machine. Then to run this application, you simply type:

    $ sbt
    $ > update
    $ > run

Once running, the actors will start working to do their work.

## How it Works
This sample application demonstrates a feed reader. It has two actors:

  1. Reader
  2. Fetcher

There are three messages involved:

  1. Read: to trigger the Reader actor
  2. Fetch(url: String): from Reader actor to Fetcher actor
  3. CompleteFetch: from Fetcher actor to Reader actor

The process starts by sending a Read message to the Reader actor. The actor then receives a Read message to indicate that it should start getting a list of feed URLs and ask its workers (in this case Fetcher actors) to actually fetch the feeds asynchronously. In this case, the Reader simply wraps the feed url into a Fetch message and sends it to a Fetcher actor for every feed URL. The Fetcher actor receives this Fetch message and uses async-http-client to do an HTTP request based on the URL. Once the Fetcher actor has finished its work, it sends a CompleteFetch message to the Reader actor so the Reader actor would know how many Fetchers have finished their work before deciding to send a new Read message to itself again to look for any new feeds or feeds that have not been fetched from the feed datastore.

To help improve the performance, I have defined a work stealing dispatcher for the Fetcher actors. It basically allows actors to donate its messages to another actors under the same dispatcher when they are too busy to handle the rest. I also define a load balancer to help distribute the incoming messages to Fetcher actors. So this load balancer actor is acting like a proxy. Whenever, the Reader actor sends a Fetch message, it sends it to the load balancer that in turns will forward the message to a Fetcher actor.

Obviously, there should be someway to tweak around so that we can define the number of Fetcher actors to use, the number of feeds to look at a time, and how often the Reader should look for new feeds from the feed datastore. In this case, the Reader actor takes three parameters:

  1. nrOfFetchers: the number of Fetcher actors to use that the Reader should create.
  2. nrOfFeeds: the number of feeds to fetch when a Read message is received.
  3. fillingRate: determine how often the Reader actor should send another Read message to itself and check the datastore for any new or remaining feeds that would need to be fetched. For example, if the number is 2, another Read message will be sent to itself if two fetchers already finish their jobs.

As a side note, I have decided to show the sample with Async HTTP Client because it is the best library I have found so far for asynchronous HTTP client. Of course, there are others that offer similar functionalities like:

  1. [Dispatch](http://dispatch.databinder.net/Dispatch.html)
  2. [Apache HTTP Client](http://hc.apache.org/)


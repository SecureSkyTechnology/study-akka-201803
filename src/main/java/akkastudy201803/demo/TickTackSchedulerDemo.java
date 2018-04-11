package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.Duration;

/**
 * @see https://doc.akka.io/docs/akka/2.5/scheduler.html
 */
public class TickTackSchedulerDemo implements Runnable {
    static class Ticker extends AbstractActor {
        private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        private final AtomicInteger counter;

        public Ticker(final AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public void postStop() {
            log.info("Tacker stopped");
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("Tick", m -> {
                final int newCnt = counter.incrementAndGet();
                log.info("{}#{}", m, newCnt);
                getSender().tell(new TickTack(m, newCnt), getSelf());
            }).build();
        }
    }

    static class TickTack {
        final String m;
        final int n;

        public TickTack(final String m, final int n) {
            this.m = m;
            this.n = n;
        }
    }

    static class Tacker extends AbstractActor {
        private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        @Override
        public void postStop() {
            log.info("Tacker stopped");
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(TickTack.class, tt -> {
                log.info("{}-Tack#{}", tt.m, tt.n);
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        final AtomicInteger counter = new AtomicInteger(0);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            final ActorRef ticker = system.actorOf(Props.create(Ticker.class, counter));
            final ActorRef tacker = system.actorOf(Props.create(Tacker.class));
            Cancellable cancellable =
                system.scheduler().schedule(
                    Duration.Zero(),
                    Duration.create(1000, TimeUnit.MILLISECONDS),
                    ticker,
                    "Tick",
                    system.dispatcher(),
                    tacker);
            System.out.println(">>> Press ENTER to stop tick-tack <<<");
            br.readLine();
            cancellable.cancel();
            System.out.println("stopped tick-tack.");
            System.out.println(">>> Press ENTER to exit <<<");
            br.readLine();
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
    }

}

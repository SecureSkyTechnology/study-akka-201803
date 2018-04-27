package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import akka.actor.AbstractActor;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.Duration;

/**
 * @see https://doc.akka.io/docs/akka/2.5/scheduler.html
 */
public class SingleThreadedScheduleDemo implements Runnable {
    static class TimerPattern1 extends AbstractActor {
        private static final long TIMER_PERIOD = 10;
        private static final int BLOCKSEC_START = 5;
        private static final int BLOCKSEC_MAX = 15;
        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        private boolean enable = false;
        private int blockSec;
        private boolean expandBlockSec = true;

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("start", m -> {
                this.enable = true;
                this.blockSec = BLOCKSEC_START;
                log.info("timer pattern 1 start.");
                getSelf().tell("tick", ActorRef.noSender());
            }).matchEquals("stop", m -> {
                this.enable = false;
                log.info("timer pattern 1 stop.");
            }).matchEquals("tick", m -> {
                if (!this.enable) {
                    return;
                }
                getContext().system().scheduler().scheduleOnce(Duration.create(TIMER_PERIOD, TimeUnit.SECONDS), () -> {
                    getSelf().tell("tick", ActorRef.noSender());
                }, getContext().dispatcher());
                try {
                    log.info("timer pattern 1 blocking op start for {} secs.", this.blockSec);
                    Thread.sleep(this.blockSec * 1000);
                    log.info("timer pattern 1 blocking op end.");
                } catch (InterruptedException ignore) {
                }
                if (this.expandBlockSec) {
                    if (BLOCKSEC_MAX == this.blockSec) {
                        this.blockSec--;
                        this.expandBlockSec = false;
                    } else {
                        this.blockSec++;
                    }
                } else {
                    if (BLOCKSEC_START == this.blockSec) {
                        this.blockSec++;
                        this.expandBlockSec = true;
                    } else {
                        this.blockSec--;
                    }
                }
                log.info("timer pattern 1 next blocking secs = {}", this.blockSec);
            }).build();
        }
    }

    static class TimerPattern2 extends AbstractActorWithTimers {
        private static final long TIMER_PERIOD = 10;
        private static final int BLOCKSEC_START = 5;
        private static final int BLOCKSEC_MAX = 15;
        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        private static final String TIMER_KEY = "selftimerABC";
        private int blockSec;
        private boolean expandBlockSec = true;

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("start", m -> {
                this.blockSec = BLOCKSEC_START;
                getTimers().startPeriodicTimer(TIMER_KEY, "tick", Duration.create(TIMER_PERIOD, TimeUnit.SECONDS));
                log.info("timer pattern 2 start.");
            }).matchEquals("stop", m -> {
                getTimers().cancel(TIMER_KEY);
                log.info("timer pattern 2 stop.");
            }).matchEquals("tick", m -> {
                try {
                    log.info("timer pattern 2 blocking op start for {} secs.", this.blockSec);
                    Thread.sleep(this.blockSec * 1000);
                    log.info("timer pattern 2 blocking op end.");
                } catch (InterruptedException ignore) {
                }
                if (this.expandBlockSec) {
                    if (BLOCKSEC_MAX == this.blockSec) {
                        this.blockSec--;
                        this.expandBlockSec = false;
                    } else {
                        this.blockSec++;
                    }
                } else {
                    if (BLOCKSEC_START == this.blockSec) {
                        this.blockSec++;
                        this.expandBlockSec = true;
                    } else {
                        this.blockSec--;
                    }
                }
                log.info("timer pattern 2 next blocking secs = {}", this.blockSec);
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        final ActorRef timerp1 =
            system.actorOf(Props.create(TimerPattern1.class).withDispatcher("my-pinned-dispatcher"));
        final ActorRef timerp2 =
            system.actorOf(Props.create(TimerPattern2.class).withDispatcher("my-pinned-dispatcher"));
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            boolean finish = false;
            while (!finish) {
                System.out.println(">>> start(1/2) or stop(1/2) or exit <<<");
                final String cmd = br.readLine().trim();
                switch (cmd) {
                case "start":
                    timerp1.tell("start", ActorRef.noSender());
                    timerp2.tell("start", ActorRef.noSender());
                    break;
                case "start1":
                    timerp1.tell("start", ActorRef.noSender());
                    break;
                case "start2":
                    timerp2.tell("start", ActorRef.noSender());
                    break;
                case "stop":
                    timerp1.tell("stop", ActorRef.noSender());
                    timerp2.tell("stop", ActorRef.noSender());
                    break;
                case "stop1":
                    timerp1.tell("stop", ActorRef.noSender());
                    break;
                case "stop2":
                    timerp2.tell("stop", ActorRef.noSender());
                    break;
                case "exit":
                    finish = true;
                }
            }
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
    }

}

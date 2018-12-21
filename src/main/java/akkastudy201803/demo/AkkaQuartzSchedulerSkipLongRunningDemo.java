package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * @see https://github.com/enragedginger/akka-quartz-scheduler
 */
public class AkkaQuartzSchedulerSkipLongRunningDemo implements Runnable {

    static class LongRunningActor extends AbstractActor {
        private static final int BLOCKSEC_START = 5;
        private static final int BLOCKSEC_MAX = 15;
        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        private int blockSec = BLOCKSEC_START;
        private boolean expandBlockSec = true;
        private int divisor = 1;

        @Override
        public void preStart() throws Exception {
            log.info(".. preStart()");
            super.preStart();
        }

        @Override
        public void postStop() throws Exception {
            log.info(".. postStop()");
            super.postStop();
        }

        @Override
        public void postRestart(Throwable reason) throws Exception {
            // notify child's restart to parent.
            getContext().getParent().tell("tack", getSelf());
            log.info(".. postRestart({})", reason.getMessage());
            super.postRestart(reason);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("tick", m -> {
                try {
                    log.info(">> blocking op start for {} secs.", this.blockSec);
                    Thread.sleep(this.blockSec * 1000);
                    @SuppressWarnings("unused")
                    final int dummy = BLOCKSEC_START / divisor;
                    log.info("<< blocking op end.");
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
                log.info(".. next blocking secs = {}", this.blockSec);
                getSender().tell("tack", getSelf());
            }).match(Integer.class, m -> {
                divisor = m.intValue();
            }).build();
        }
    }

    static class TimerSupervisorDemo extends AbstractActor {
        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        private final ActorRef timerActor =
            getContext().actorOf(Props.create(LongRunningActor.class).withDispatcher("my-pinned-dispatcher"));;
        private boolean isRunning = false;

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("tick", m -> {
                if (isRunning) {
                    log.info("timer supervisor : previous job is not finished, skipped.");
                    return;
                }
                timerActor.tell("tick", getSelf());
                isRunning = true;
            }).matchEquals("tack", m -> {
                log.info("timer supervisor : previous job finished (or restarted).");
                isRunning = false;
            }).matchEquals("invoke-zero-division", m -> {
                timerActor.tell(0, getSelf());
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        final ActorRef demo = system.actorOf(Props.create(TimerSupervisorDemo.class));
        QuartzSchedulerExtension scheduler = QuartzSchedulerExtension.get(system);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        final String AKKA_QUARTZ_SC_NAME = "Every10SecDemo";
        try {
            boolean finish = false;
            while (!finish) {
                System.out.println(">>> start or stop or exit, '0' to zero divide <<<");
                final String cmd = br.readLine().trim();
                switch (cmd) {
                case "start":
                    scheduler.schedule(AKKA_QUARTZ_SC_NAME, demo, "tick");
                    break;
                case "stop":
                    scheduler.cancelJob(AKKA_QUARTZ_SC_NAME);
                    break;
                case "0":
                    demo.tell("invoke-zero-division", ActorRef.noSender());
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

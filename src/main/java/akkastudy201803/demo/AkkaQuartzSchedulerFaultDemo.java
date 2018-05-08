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
public class AkkaQuartzSchedulerFaultDemo implements Runnable {

    static class FaultyTimerDemo extends AbstractActor {
        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        private int cnt = 0;

        @Override
        public void postRestart(Throwable reason) throws Exception {
            log.info("postRestart({})", reason.getMessage());
            super.postRestart(reason);
        }

        @Override
        public void postStop() throws Exception {
            log.info("postStop()");
            super.postStop();
        }

        @Override
        public void preStart() throws Exception {
            log.info("preStart()");
            super.preStart();
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("tick", m -> {
                cnt++;
                log.info("cnt = {}", cnt);
                if ((cnt / 2) == 0) {
                    cnt = (cnt % 2) / (cnt / 2);
                }
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        final ActorRef demo =
            system.actorOf(Props.create(FaultyTimerDemo.class).withDispatcher("my-pinned-dispatcher"));
        QuartzSchedulerExtension scheduler = QuartzSchedulerExtension.get(system);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        final String AKKA_QUARTZ_SC_NAME = "Every3SecDemo";
        try {
            boolean finish = false;
            while (!finish) {
                System.out.println(">>> start or stop or exit <<<");
                final String cmd = br.readLine().trim();
                switch (cmd) {
                case "start":
                    scheduler.schedule(AKKA_QUARTZ_SC_NAME, demo, "tick");
                    break;
                case "stop":
                    scheduler.cancelJob(AKKA_QUARTZ_SC_NAME);
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

package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import scala.concurrent.duration.Duration;

/**
 * @see https://doc.akka.io/docs/akka/2.5/fsm.html
 */
public class FSMTimerDemo implements Runnable {

    static enum BogusGrepState {
        PROMPT,
        COUNTDOWN
    }

    static class CountDownWatchActor extends AbstractFSM<BogusGrepState, Integer> {
        static final String TIMER_ID = "count-down-tick-tack";
        {
            startWith(BogusGrepState.PROMPT, 1);
            when(BogusGrepState.PROMPT, matchEventEquals("start", (event, data) -> {
                final int coundDown = (int) (Math.random() * 8) + 2;
                setTimer(TIMER_ID, "tick", Duration.create(1, TimeUnit.SECONDS), true);
                return goTo(BogusGrepState.COUNTDOWN).using(coundDown);
            }));
            when(BogusGrepState.COUNTDOWN, matchEventEquals("tick", (event, data) -> {
                final int newCount = this.stateData() - 1;
                if (newCount < 0) {
                    return goTo(BogusGrepState.PROMPT);
                } else {
                    return goTo(BogusGrepState.COUNTDOWN).using(newCount);
                }
            }));
            when(BogusGrepState.COUNTDOWN, matchEventEquals("cancel", (event, data) -> goTo(BogusGrepState.PROMPT)));
            when(BogusGrepState.PROMPT, matchEventEquals("stop", (event, data) -> stop()));
            whenUnhandled(matchAnyEvent((m, statet) -> {
                log().warning("received unhandled request {} in state {}, data {}", m, stateName(), stateData());
                return stay();
            }));

            onTransition(matchState(BogusGrepState.PROMPT, BogusGrepState.COUNTDOWN, () -> {
                log().info("count-down started from {} : 'cancel' to stop count-down.", nextStateData());
            }).state(BogusGrepState.COUNTDOWN, BogusGrepState.COUNTDOWN, () -> {
                log().info("tick-tack {} -> {} : 'cancel' to stop count-down.", stateData(), nextStateData());
            }).state(BogusGrepState.COUNTDOWN, BogusGrepState.COUNTDOWN, () -> {
                log().info(":P");
            }).state(BogusGrepState.COUNTDOWN, BogusGrepState.PROMPT, () -> {
                cancelTimer(TIMER_ID);
                final int curr = nextStateData();
                final String commonPrompt = "'start' to start new count-down / 'stop' to stop count-down watch.";
                if (curr > 0) {
                    log().info("count-down cancelled. {}", commonPrompt);
                } else {
                    log().info("count-down completed. {}", commonPrompt);
                }
            }));
            onTermination(matchStop(Normal(), (state, data) -> {
                log().info("stopped normally, state = {} / data = {}", state, data);
            }));

            initialize();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            final ActorRef countDownWatch = system.actorOf(Props.create(CountDownWatchActor.class));
            System.out.println("ENTER 'start', 'stop' command, Ctrl-C to exit.");
            while (true) {
                final String cmd = br.readLine().trim();
                countDownWatch.tell(cmd, ActorRef.noSender());
            }
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
    }
}

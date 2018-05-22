package akkastudy201803;

import static org.junit.Assert.assertFalse;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.AbstractActor;
import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Identify;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.Duration;

/**
 * @see https://doc.akka.io/docs/akka/2.5/general/supervision.html
 * @see https://doc.akka.io/docs/akka/2.5/fault-tolerance.html
 * 
 * copied and modified from above akka docs sample code.
 */
public class SupervisorDemoOneForOneTest {
    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    static class OneForOneSuperVisorDemo extends AbstractActor {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        private final SupervisorStrategy strategy;

        public OneForOneSuperVisorDemo() {
            this(10);
        }

        public OneForOneSuperVisorDemo(final int maxNrOfRetries) {
            strategy =
                new OneForOneStrategy(
                    maxNrOfRetries,
                    Duration.create(1, TimeUnit.MINUTES),
                    DeciderBuilder
                        .match(ArithmeticException.class, e -> SupervisorStrategy.resume())
                        .match(NullPointerException.class, e -> SupervisorStrategy.restart())
                        .match(IllegalArgumentException.class, e -> SupervisorStrategy.stop())
                        .matchAny(o -> SupervisorStrategy.escalate())
                        .build());
        }

        @Override
        public SupervisorStrategy supervisorStrategy() {
            return strategy;
        }

        @Override
        public void preStart() throws Exception {
            log.info("preStart() : before super's");
            super.preStart();
            log.info("preStart() : after super's");
        }

        @Override
        public void postStop() throws Exception {
            log.info("postStop() : before super's");
            super.postStop();
            log.info("postStop() : after super's");
        }

        @Override
        public void preRestart(Throwable reason, Optional<Object> message) throws Exception {
            log.info("preRestart() : before super's, reason={},{}", reason.getClass(), reason.getMessage());
            super.preRestart(reason, message);
            log.info("preRestart() : after super's, reason={},{}", reason.getClass(), reason.getMessage());
        }

        @Override
        public void postRestart(Throwable reason) throws Exception {
            log.info("postRestart() : before super's, reason={},{}", reason.getClass(), reason.getMessage());
            super.postRestart(reason);
            log.info("postRestart() : after super's, reason={},{}", reason.getClass(), reason.getMessage());
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(Props.class, props -> {
                getSender().tell(getContext().actorOf(props), getSelf());
            }).build();
        }
    }

    static class Child extends AbstractActor {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        int state = 0;

        @Override
        public void preStart() throws Exception {
            log.info("preStart() : before super's");
            super.preStart();
            log.info("preStart() : after super's");
        }

        @Override
        public void postStop() throws Exception {
            log.info("postStop() : before super's");
            super.postStop();
            log.info("postStop() : after super's");
        }

        @Override
        public void preRestart(Throwable reason, Optional<Object> message) throws Exception {
            log.info("preRestart() : before super's, reason={},{}", reason.getClass(), reason.getMessage());
            super.preRestart(reason, message);
            log.info("preRestart() : after super's, reason={},{}", reason.getClass(), reason.getMessage());
        }

        @Override
        public void postRestart(Throwable reason) throws Exception {
            log.info("postRestart() : before super's, reason={},{}", reason.getClass(), reason.getMessage());
            super.postRestart(reason);
            log.info("postRestart() : after super's, reason={},{}", reason.getClass(), reason.getMessage());
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                .match(Exception.class, exception -> {
                    throw exception;
                })
                .match(Integer.class, i -> state = i)
                .matchEquals("get", s -> getSender().tell(state, getSelf()))
                .matchEquals("done", m -> getContext().stop(getSelf()))
                .build();
        }
    }

    @Test
    public void testOneForOneSuperVisorDemo() throws Exception {
        TestKit probe = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(OneForOneSuperVisorDemo.class), "one-for-one-supervisor-demo-1");
        supervisor.tell(Props.create(Child.class), probe.getRef());
        ActorRef child = probe.expectMsgClass(ActorRef.class);

        child.tell(42, ActorRef.noSender());
        child.tell("get", probe.getRef());
        probe.expectMsg(42);

        // resume : actor does not restart.
        child.tell(new ArithmeticException(), ActorRef.noSender());
        child.tell("get", probe.getRef());
        probe.expectMsg(42);

        // restart : child state reset.
        child.tell(new NullPointerException(), ActorRef.noSender());
        child.tell("get", probe.getRef());
        probe.expectMsg(0);

        // stop : terminate actor.
        probe.watch(child);
        child.tell(new IllegalArgumentException(), ActorRef.noSender());
        probe.expectMsgClass(Terminated.class);
        probe.unwatch(child);

        supervisor.tell(Props.create(Child.class), probe.getRef());
        child = probe.expectMsgClass(ActorRef.class);
        probe.watch(child);
        child.tell("get", probe.getRef());
        probe.expectMsg(0);
        // other exception -> escalated, restart supervisor itself -> kill all children.
        child.tell(new Exception(), ActorRef.noSender());
        probe.expectMsgClass(Terminated.class);
        child.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());
    }

    @Test
    public void testNormalyStoppedActorDoesNotRestart() throws Exception {
        ActorRef supervisor =
            system.actorOf(
                Props.create(OneForOneSuperVisorDemo.class),
                "one-for-one-supervisor-normal-stopped-actor-does-not-restart-demo");

        TestKit probe1 = new TestKit(system);
        supervisor.tell(Props.create(Child.class), probe1.getRef());
        ActorRef child1 = probe1.expectMsgClass(ActorRef.class);

        TestKit probe2 = new TestKit(system);
        supervisor.tell(Props.create(Child.class), probe2.getRef());
        ActorRef child2 = probe2.expectMsgClass(ActorRef.class);

        child1.tell(42, ActorRef.noSender());
        child1.tell("get", probe1.getRef());
        probe1.expectMsg(42);
        child2.tell(12, ActorRef.noSender());
        child2.tell("get", probe2.getRef());
        probe2.expectMsg(12);

        // normal stop : terminate actor by actor itself
        probe1.watch(child1);
        child1.tell("done", ActorRef.noSender());
        probe1.expectMsgClass(Terminated.class);
        probe1.unwatch(child1);

        // child1 does not restart.
        child1.tell(new Identify(1), probe1.getRef());
        ActorIdentity id = probe1.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());

        // child2 stay alive.
        child2.tell(34, ActorRef.noSender());
        child2.tell("get", probe2.getRef());
        probe2.expectMsg(34);
    }

    @Test
    public void testRestartMaxRetryCountOver() throws Exception {
        TestKit probe = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(OneForOneSuperVisorDemo.class), "one-for-one-supervisor-demo-2");
        supervisor.tell(Props.create(Child.class), probe.getRef());
        ActorRef child = probe.expectMsgClass(ActorRef.class);

        child.tell(42, ActorRef.noSender());
        child.tell("get", probe.getRef());
        probe.expectMsg(42);
        for (int i = 0; i < 10; i++) {
            // restart : child state reset.
            child.tell(new NullPointerException(), ActorRef.noSender());
            child.tell("get", probe.getRef());
            probe.expectMsg(0);
        }
        child.tell("get", probe.getRef());
        probe.expectMsg(0);

        // restart max-retry-count over, then stopped.
        probe.watch(child);
        child.tell(new NullPointerException(), ActorRef.noSender());
        probe.expectMsgClass(Terminated.class);
        child.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());
    }

    @Test
    public void testRestartMaxRetryCountOverForEachActor() throws Exception {
        final int RETRY_MAX = 3;
        ActorRef supervisor =
            system.actorOf(Props.create(OneForOneSuperVisorDemo.class, RETRY_MAX), "one-for-one-supervisor-demo-3");

        TestKit probe1 = new TestKit(system);
        supervisor.tell(Props.create(Child.class), probe1.getRef());
        ActorRef child1 = probe1.expectMsgClass(ActorRef.class);

        TestKit probe2 = new TestKit(system);
        supervisor.tell(Props.create(Child.class), probe2.getRef());
        ActorRef child2 = probe2.expectMsgClass(ActorRef.class);

        child1.tell(42, ActorRef.noSender());
        child1.tell("get", probe1.getRef());
        probe1.expectMsg(42);
        child2.tell(12, ActorRef.noSender());
        child2.tell("get", probe2.getRef());
        probe2.expectMsg(12);

        for (int i = 0; i < RETRY_MAX; i++) {
            // restart : child(1) state reset.
            child1.tell(new NullPointerException(), ActorRef.noSender());
            child1.tell("get", probe1.getRef());
            probe1.expectMsg(0);
        }
        child1.tell("get", probe1.getRef());
        probe1.expectMsg(0);

        // child (2) is not affected.
        child2.tell("get", probe2.getRef());
        probe2.expectMsg(12);

        // restart max-retry-count over, then child (1) stopped.
        probe1.watch(child1);
        child1.tell(new NullPointerException(), ActorRef.noSender());
        probe1.expectMsgClass(Terminated.class);
        child1.tell(new Identify(1), probe1.getRef());
        ActorIdentity id = probe1.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());

        // child (2) is alive.
        child2.tell("get", probe2.getRef());
        probe2.expectMsg(12);

        for (int i = 0; i < RETRY_MAX; i++) {
            // restart : child(1) state reset.
            child2.tell(new NullPointerException(), ActorRef.noSender());
            child2.tell("get", probe2.getRef());
            probe2.expectMsg(0);
        }
        child2.tell("get", probe2.getRef());
        probe2.expectMsg(0);

        // restart max-retry-count over, then child (2) stopped.
        probe2.watch(child2);
        child2.tell(new NullPointerException(), ActorRef.noSender());
        probe2.expectMsgClass(Terminated.class);
        child2.tell(new Identify(2), probe2.getRef());
        id = probe2.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());
    }

    @Test
    public void testResetSupervisorRestartCount() throws Exception {
        final int RETRY_MAX = 3;
        TestKit probe = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(OneForOneSuperVisorDemo.class, RETRY_MAX), "one-for-one-supervisor-demo-4");
        supervisor.tell(Props.create(Child.class), probe.getRef());
        ActorRef child = probe.expectMsgClass(ActorRef.class);

        child.tell(42, ActorRef.noSender());
        child.tell("get", probe.getRef());
        probe.expectMsg(42);

        // restart max - 1 times.
        for (int i = 0; i < RETRY_MAX - 1; i++) {
            // restart : child state reset.
            child.tell(new NullPointerException(), ActorRef.noSender());
            child.tell("get", probe.getRef());
            probe.expectMsg(0);
        }
        child.tell("get", probe.getRef());
        probe.expectMsg(0);

        // other exception -> escalated, restart supervisor itself -> kill all children.
        probe.watch(child);
        child.tell(new Exception(), ActorRef.noSender());
        probe.expectMsgClass(Terminated.class);
        // child actor was stopped.
        child.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());

        supervisor.tell(Props.create(Child.class), probe.getRef());
        child = probe.expectMsgClass(ActorRef.class);

        child.tell(24, ActorRef.noSender());
        child.tell("get", probe.getRef());
        probe.expectMsg(24);

        // confirm supervisor's retry count was reset.
        // (yes, this child is "new" child actor, so strictly speaking, we do not need this test :P)
        for (int i = 0; i < RETRY_MAX - 1; i++) {
            // restart : child state reset.
            child.tell(new NullPointerException(), ActorRef.noSender());
            child.tell("get", probe.getRef());
            probe.expectMsg(0);
        }
        child.tell("get", probe.getRef());
        probe.expectMsg(0);
    }

    @Test
    public void testRetryCountIs1() throws Exception {
        TestKit probe = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(OneForOneSuperVisorDemo.class, 1), "one-for-one-supervisor-demo-retry-1");
        supervisor.tell(Props.create(Child.class), probe.getRef());
        ActorRef child = probe.expectMsgClass(ActorRef.class);

        child.tell(42, ActorRef.noSender());
        child.tell("get", probe.getRef());
        probe.expectMsg(42);

        // 1st restart.
        child.tell(new NullPointerException(), ActorRef.noSender());
        child.tell("get", probe.getRef());
        probe.expectMsg(0);

        child.tell(21, ActorRef.noSender());
        child.tell("get", probe.getRef());
        probe.expectMsg(21);

        // 2nd restart -> retry over, stop.
        probe.watch(child);
        child.tell(new NullPointerException(), ActorRef.noSender());
        probe.expectMsgClass(Terminated.class);
        // child actor was stopped.
        child.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());
    }

    @Test
    public void testRetryCountIs0() throws Exception {
        TestKit probe = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(OneForOneSuperVisorDemo.class, 0), "one-for-one-supervisor-demo-retry-0");
        supervisor.tell(Props.create(Child.class), probe.getRef());
        ActorRef child = probe.expectMsgClass(ActorRef.class);

        child.tell(42, ActorRef.noSender());
        child.tell("get", probe.getRef());
        probe.expectMsg(42);

        // 1st restart -> retry over, stop.
        probe.watch(child);
        child.tell(new NullPointerException(), ActorRef.noSender());
        probe.expectMsgClass(Terminated.class);
        // child actor was stopped.
        child.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());
    }

    static class OneForOneSuperVisorAllStopDemo extends AbstractActor {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        private final SupervisorStrategy strategy;

        public OneForOneSuperVisorAllStopDemo() {
            strategy =
                new OneForOneStrategy(
                    10,
                    Duration.create(1, TimeUnit.MINUTES),
                    DeciderBuilder.matchAny(o -> SupervisorStrategy.stop()).build());
        }

        @Override
        public SupervisorStrategy supervisorStrategy() {
            return strategy;
        }

        @Override
        public void preStart() throws Exception {
            log.info("preStart() : before super's");
            super.preStart();
            log.info("preStart() : after super's");
        }

        @Override
        public void postStop() throws Exception {
            log.info("postStop() : before super's");
            super.postStop();
            log.info("postStop() : after super's");
        }

        @Override
        public void preRestart(Throwable reason, Optional<Object> message) throws Exception {
            log.info("preRestart() : before super's, reason={},{}", reason.getClass(), reason.getMessage());
            super.preRestart(reason, message);
            log.info("preRestart() : after super's, reason={},{}", reason.getClass(), reason.getMessage());
        }

        @Override
        public void postRestart(Throwable reason) throws Exception {
            log.info("postRestart() : before super's, reason={},{}", reason.getClass(), reason.getMessage());
            super.postRestart(reason);
            log.info("postRestart() : after super's, reason={},{}", reason.getClass(), reason.getMessage());
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(Props.class, props -> {
                getSender().tell(getContext().actorOf(props), getSelf());
            }).build();
        }
    }

    @Test
    public void testAllStopDemo() throws Exception {
        TestKit probe = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(OneForOneSuperVisorAllStopDemo.class), "one-for-one-supervisor-all-stop-demo");
        supervisor.tell(Props.create(Child.class), probe.getRef());
        ActorRef child = probe.expectMsgClass(ActorRef.class);

        child.tell(42, ActorRef.noSender());
        child.tell("get", probe.getRef());
        probe.expectMsg(42);

        // 1st exception -> stop.
        probe.watch(child);
        child.tell(new NullPointerException(), ActorRef.noSender());
        probe.expectMsgClass(Terminated.class);
        // child actor was stopped.
        child.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());
    }
}

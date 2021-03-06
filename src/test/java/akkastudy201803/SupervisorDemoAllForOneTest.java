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
import akka.actor.AllForOneStrategy;
import akka.actor.Identify;
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
 */
public class SupervisorDemoAllForOneTest {
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

    static class DivPair {
        final int a;
        final int b;

        DivPair(final int a, final int b) {
            this.a = a;
            this.b = b;
        }
    }

    static class DivActor extends AbstractActor {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        int lastDiv = 0;

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
            return receiveBuilder().match(DivPair.class, m -> {
                log.info("DivPiar received, a={}, b={}", m.a, m.b);
                lastDiv = m.a / m.b;
                getSender().tell(lastDiv, getSelf());
            }).match(Exception.class, exception -> {
                throw exception;
            }).matchEquals("done", m -> {
                getContext().stop(getSelf());
            }).matchAny(m -> {
                getSender().tell(lastDiv, getSelf());
            }).build();
        }
    }

    static class AllForOneSuperVisorDemo extends AbstractActor {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        final Props props = Props.create(DivActor.class);
        private final SupervisorStrategy strategy;

        AllForOneSuperVisorDemo() {
            this(-1);
        }

        AllForOneSuperVisorDemo(final int maxNrOfRetries) {
            strategy = new AllForOneStrategy(
                /* -1 to maxNrOfRetries, and a non-infinite Duration to withinTimeRange
                 * then maxNrOfRetries is treated as 1
                 */
                maxNrOfRetries,
                Duration.create(1, TimeUnit.MINUTES),
                DeciderBuilder
                    .match(ArithmeticException.class, e -> SupervisorStrategy.restart())
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
            return receiveBuilder().match(String.class, m -> {
                getSender().tell(getContext().actorOf(props, m), getSelf());
            }).build();
        }
    }

    @Test
    public void testAllForOneSuperVisorDemo() throws Exception {
        TestKit probe = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(AllForOneSuperVisorDemo.class), "all-for-one-supervisor-demo-1");

        supervisor.tell("div1", probe.getRef());
        ActorRef div1 = probe.expectMsgClass(ActorRef.class);
        div1.tell(new DivPair(6, 3), probe.getRef());
        probe.expectMsg(2);
        div1.tell(new Object(), probe.getRef());
        probe.expectMsg(2);

        supervisor.tell("div2", probe.getRef());
        ActorRef div2 = probe.expectMsgClass(ActorRef.class);
        div2.tell(new DivPair(10, 2), probe.getRef());
        probe.expectMsg(5);
        div2.tell(new Object(), probe.getRef());
        probe.expectMsg(5);

        div1.tell(new DivPair(1, 0), probe.getRef());
        div1.tell(new Object(), probe.getRef());
        probe.expectMsg(0);
        // all-for-one strategy : div1 throws ArithmeticException -> restart all children. 
        div2.tell(new Object(), probe.getRef());
        probe.expectMsg(0); // div2 actor reset.

        div1.tell(new DivPair(6, 2), probe.getRef());
        probe.expectMsg(3);
        div1.tell(new Object(), probe.getRef());
        probe.expectMsg(3);

        div2.tell(new DivPair(20, 2), probe.getRef());
        probe.expectMsg(10);
        div2.tell(new Object(), probe.getRef());
        probe.expectMsg(10);

        // div1 second exception -> retry over, stop all children.
        div1.tell(new DivPair(1, 0), probe.getRef());

        div1.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());

        div2.tell(new Identify(2), probe.getRef());
        id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());
    }

    @Test
    public void testNormalyStoppedActorDoesNotRestart() throws Exception {
        TestKit probe = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(
                Props.create(AllForOneSuperVisorDemo.class),
                "all-for-one-supervisor-normal-stopped-actor-does-not-restart-demo");

        supervisor.tell("div1", probe.getRef());
        ActorRef div1 = probe.expectMsgClass(ActorRef.class);
        div1.tell(new DivPair(6, 3), probe.getRef());
        probe.expectMsg(2);
        div1.tell(new Object(), probe.getRef());
        probe.expectMsg(2);

        supervisor.tell("div2", probe.getRef());
        ActorRef div2 = probe.expectMsgClass(ActorRef.class);
        div2.tell(new DivPair(10, 2), probe.getRef());
        probe.expectMsg(5);
        div2.tell(new Object(), probe.getRef());
        probe.expectMsg(5);

        // normal stop : terminate actor by actor itself
        probe.watch(div1);
        div1.tell("done", ActorRef.noSender());
        probe.expectMsgClass(Terminated.class);
        probe.unwatch(div1);

        // all-for-one strategy : div1 stopped, but div2 not affected. 
        div2.tell(new Object(), probe.getRef());
        probe.expectMsg(5);

        // div1 does not restart.
        div1.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());
    }

    @Test
    public void testAllForOneSuperVisorDemo2() throws Exception {
        TestKit probe = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(AllForOneSuperVisorDemo.class), "all-for-one-supervisor-demo-2");

        supervisor.tell("div1", probe.getRef());
        ActorRef div1 = probe.expectMsgClass(ActorRef.class);
        div1.tell(new DivPair(6, 3), probe.getRef());
        probe.expectMsg(2);
        div1.tell(new Object(), probe.getRef());
        probe.expectMsg(2);

        supervisor.tell("div2", probe.getRef());
        ActorRef div2 = probe.expectMsgClass(ActorRef.class);
        div2.tell(new DivPair(10, 2), probe.getRef());
        probe.expectMsg(5);
        div2.tell(new Object(), probe.getRef());
        probe.expectMsg(5);

        // div2 1st exception
        div2.tell(new DivPair(1, 0), probe.getRef());
        // 2nd exception, but 1st for div1 -> not exceed retry count (for each child) yet.
        div1.tell(new DivPair(1, 0), probe.getRef());
        // div2 2nd exception -> stop all children.
        div2.tell(new DivPair(1, 0), probe.getRef());

        div1.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());

        div2.tell(new Identify(2), probe.getRef());
        id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());
    }

    @Test
    public void testResetSupervisorRestartCount() throws Exception {
        TestKit probe = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(AllForOneSuperVisorDemo.class, 6), "all-for-one-supervisor-demo-3");

        supervisor.tell("div1", probe.getRef());
        ActorRef div1 = probe.expectMsgClass(ActorRef.class);
        supervisor.tell("div2", probe.getRef());
        ActorRef div2 = probe.expectMsgClass(ActorRef.class);

        div1.tell(new DivPair(1, 0), probe.getRef());
        div1.tell(new DivPair(1, 0), probe.getRef());
        div1.tell(new DivPair(1, 0), probe.getRef());
        div1.tell(new DivPair(6, 3), probe.getRef());
        probe.expectMsg(2);
        div1.tell(new Object(), probe.getRef());
        probe.expectMsg(2);

        div2.tell(new DivPair(1, 0), probe.getRef());
        div2.tell(new DivPair(1, 0), probe.getRef());
        div2.tell(new DivPair(1, 0), probe.getRef());
        div2.tell(new DivPair(10, 2), probe.getRef());
        probe.expectMsg(5);
        div2.tell(new Object(), probe.getRef());
        probe.expectMsg(5);

        // other exception -> escalated, restart supervisor itself -> kill all children.
        probe.watch(div1);
        div1.tell(new Exception(), ActorRef.noSender());
        // child actor throw received exception, no reply to sender.
        probe.expectMsgClass(Terminated.class);

        // child actor was stopped.
        div1.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());

        div2.tell(new Identify(2), probe.getRef());
        id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());

        // confirm supervisor's retry count was reset.
        // (yes, this child is "new" child actor, so strictly speaking, we do not need this test :P)
        supervisor.tell("div1", probe.getRef());
        div1 = probe.expectMsgClass(ActorRef.class);
        supervisor.tell("div2", probe.getRef());
        div2 = probe.expectMsgClass(ActorRef.class);

        div1.tell(new DivPair(1, 0), probe.getRef());
        div1.tell(new DivPair(1, 0), probe.getRef());
        div1.tell(new DivPair(1, 0), probe.getRef());
        div1.tell(new DivPair(6, 3), probe.getRef());
        probe.expectMsg(2);
        div1.tell(new Object(), probe.getRef());
        probe.expectMsg(2);

        div2.tell(new DivPair(1, 0), probe.getRef());
        div2.tell(new DivPair(1, 0), probe.getRef());
        div2.tell(new DivPair(1, 0), probe.getRef());
        div2.tell(new DivPair(10, 2), probe.getRef());
        probe.expectMsg(5);
        div2.tell(new Object(), probe.getRef());
        probe.expectMsg(5);
    }

    @Test
    public void testSupervisorRetryMaxIs0() throws Exception {
        TestKit probe1 = new TestKit(system);
        TestKit probe2 = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(AllForOneSuperVisorDemo.class, 0), "all-for-one-supervisor-demo-4-retry-max-0");

        supervisor.tell("div1", probe1.getRef());
        ActorRef div1 = probe1.expectMsgClass(ActorRef.class);
        probe1.watch(div1);
        supervisor.tell("div2", probe2.getRef());
        ActorRef div2 = probe2.expectMsgClass(ActorRef.class);
        probe2.watch(div2);

        div1.tell(new DivPair(6, 3), probe1.getRef());
        probe1.expectMsg(2);
        div2.tell(new DivPair(6, 2), probe2.getRef());
        probe2.expectMsg(3);

        // 1st exception -> retry over, stop.
        div1.tell(new DivPair(1, 0), probe1.getRef());
        // all-for-one => stop all children.

        probe1.expectMsgClass(Terminated.class);
        div1.tell(new Identify(1), probe1.getRef());
        ActorIdentity id1 = probe1.expectMsgClass(ActorIdentity.class);
        assertFalse(id1.getActorRef().isPresent());

        probe2.expectMsgClass(Terminated.class);
        div2.tell(new Identify(2), probe2.getRef());
        ActorIdentity id2 = probe2.expectMsgClass(ActorIdentity.class);
        assertFalse(id2.getActorRef().isPresent());
    }

    @Test
    public void testSupervisorRetryMaxIs0ButSelfStop() throws Exception {
        TestKit probe1 = new TestKit(system);
        TestKit probe2 = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(AllForOneSuperVisorDemo.class, 0), "all-for-one-supervisor-demo-5-retry-max-0");

        supervisor.tell("div1", probe1.getRef());
        ActorRef div1 = probe1.expectMsgClass(ActorRef.class);
        probe1.watch(div1);
        supervisor.tell("div2", probe2.getRef());
        ActorRef div2 = probe2.expectMsgClass(ActorRef.class);
        probe2.watch(div2);

        div1.tell(new DivPair(6, 3), probe1.getRef());
        probe1.expectMsg(2);
        div2.tell(new DivPair(6, 2), probe2.getRef());
        probe2.expectMsg(3);

        // div1 self stop.
        div1.tell("done", probe1.getRef());

        probe1.expectMsgClass(Terminated.class);
        div1.tell(new Identify(1), probe1.getRef());
        ActorIdentity id1 = probe1.expectMsgClass(ActorIdentity.class);
        assertFalse(id1.getActorRef().isPresent());

        // self stop does not invoke supervisor's exception matcher -> div2 still alive.
        div2.tell(new DivPair(10, 2), probe2.getRef());
        probe2.expectMsg(5);
    }

    static class AllForOneSuperVisorAllStopDemo extends AbstractActor {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        final Props props = Props.create(DivActor.class);
        private final SupervisorStrategy strategy;

        AllForOneSuperVisorAllStopDemo() {
            strategy = new AllForOneStrategy(
                /* -1 to maxNrOfRetries, and a non-infinite Duration to withinTimeRange
                 * then maxNrOfRetries is treated as 1
                 */
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
            return receiveBuilder().match(String.class, m -> {
                getSender().tell(getContext().actorOf(props, m), getSelf());
            }).build();
        }
    }

    @Test
    public void testAllStopDemo() throws Exception {
        TestKit probe1 = new TestKit(system);
        TestKit probe2 = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(Props.create(AllForOneSuperVisorAllStopDemo.class), "all-for-one-supervisor-all-stop-demo");

        supervisor.tell("div1", probe1.getRef());
        ActorRef div1 = probe1.expectMsgClass(ActorRef.class);
        probe1.watch(div1);
        supervisor.tell("div2", probe2.getRef());
        ActorRef div2 = probe2.expectMsgClass(ActorRef.class);
        probe2.watch(div2);

        div1.tell(new DivPair(6, 3), probe1.getRef());
        probe1.expectMsg(2);
        div2.tell(new DivPair(6, 2), probe2.getRef());
        probe2.expectMsg(3);

        // 1st exception -> stop.
        div1.tell(new DivPair(1, 0), probe1.getRef());
        // all-for-one => stop all children.

        probe1.expectMsgClass(Terminated.class);
        div1.tell(new Identify(1), probe1.getRef());
        ActorIdentity id1 = probe1.expectMsgClass(ActorIdentity.class);
        assertFalse(id1.getActorRef().isPresent());

        probe2.expectMsgClass(Terminated.class);
        div2.tell(new Identify(2), probe2.getRef());
        ActorIdentity id2 = probe2.expectMsgClass(ActorIdentity.class);
        assertFalse(id2.getActorRef().isPresent());
    }

    @Test
    public void testAllStopDemoButSelfStop() throws Exception {
        TestKit probe1 = new TestKit(system);
        TestKit probe2 = new TestKit(system);
        ActorRef supervisor =
            system.actorOf(
                Props.create(AllForOneSuperVisorAllStopDemo.class),
                "all-for-one-supervisor-all-stop-demo-self-stop");

        supervisor.tell("div1", probe1.getRef());
        ActorRef div1 = probe1.expectMsgClass(ActorRef.class);
        probe1.watch(div1);
        supervisor.tell("div2", probe2.getRef());
        ActorRef div2 = probe2.expectMsgClass(ActorRef.class);
        probe2.watch(div2);

        div1.tell(new DivPair(6, 3), probe1.getRef());
        probe1.expectMsg(2);
        div2.tell(new DivPair(6, 2), probe2.getRef());
        probe2.expectMsg(3);

        // div1 self stop.
        div1.tell("done", probe1.getRef());

        probe1.expectMsgClass(Terminated.class);
        div1.tell(new Identify(1), probe1.getRef());
        ActorIdentity id1 = probe1.expectMsgClass(ActorIdentity.class);
        assertFalse(id1.getActorRef().isPresent());

        // self stop does not invoke supervisor's exception matcher -> div2 still alive.
        div2.tell(new DivPair(10, 2), probe2.getRef());
        probe2.expectMsg(5);
    }
}

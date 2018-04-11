package akkastudy201803;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.AbstractActor;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.Duration;

/**
 * @see https://doc.akka.io/docs/akka/current/testing.html
 * 
 * this test code were copied from sample code in above document.
 */
public class TestKitSampleTest {
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

    static class SomeActor extends AbstractActor {
        ActorRef target = null;

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("hello", message -> {
                getSender().tell("world", getSelf());
                if (target != null)
                    target.forward(message, getContext());
            }).match(ActorRef.class, actorRef -> {
                target = actorRef;
                getSender().tell("done", getSelf());
            }).build();
        }
    }

    @Test
    public void testIt() {
        new TestKit(system) {
            {
                final ActorRef subject = system.actorOf(Props.create(SomeActor.class));
                final TestKit probe = new TestKit(system);
                subject.tell(probe.getRef(), getRef());
                expectMsg(duration("1 second"), "done");

                within(duration("3 seconds"), () -> {
                    subject.tell("hello", getRef());
                    awaitCond(probe::msgAvailable);
                    expectMsg(Duration.Zero(), "world");
                    probe.expectMsg(Duration.Zero(), "hello");
                    assertEquals(getRef(), probe.getLastSender());
                    expectNoMessage();
                    return null;
                });
            }
        };
    }

    static final class TriggerScheduling {
    }

    static final class ScheduledMessage {
    }

    static class TestTimerActor extends AbstractActorWithTimers {
        private static Object SCHED_KEY = "SchedKey";

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(TriggerScheduling.class, msg -> triggerScheduling()).build();
        }

        void triggerScheduling() {
            getTimers()
                .startSingleTimer(SCHED_KEY, new ScheduledMessage(), Duration.create(500, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testOverrideActorMethod() {
        final TestKit probe = new TestKit(system);
        final ActorRef target = system.actorOf(Props.create(TestTimerActor.class, () -> new TestTimerActor() {
            @Override
            void triggerScheduling() {
                probe.getRef().tell(new ScheduledMessage(), getSelf());
            }
        }));
        target.tell(new TriggerScheduling(), ActorRef.noSender());
        probe.expectMsgClass(ScheduledMessage.class);
    }

    @Test
    public void testWithinDuration() {
        final TestKit probe = new TestKit(system);
        probe.getRef().tell(42, ActorRef.noSender());
        probe.within(Duration.Zero(), Duration.create(1, "second"), () -> {
            assertEquals((Integer) 42, probe.expectMsgClass(Integer.class));
            return null;
        });
    }

    @Test
    public void testProbeWatch() {
        final TestKit probe = new TestKit(system);
        final ActorRef dummy = system.actorOf(Props.empty());
        probe.watch(dummy);
        dummy.tell(PoisonPill.getInstance(), ActorRef.noSender());
        final Terminated msg = probe.expectMsgClass(Terminated.class);
        assertEquals(msg.getActor(), dummy);
    }

    @Test
    public void testProbeReply() {
        final TestKit probe1 = new TestKit(system);
        final TestKit probe2 = new TestKit(system);
        probe2.getRef().tell("hello", probe1.getRef());
        probe2.expectMsgEquals("hello");
        probe2.reply("world");
        probe1.expectMsgEquals("world");
        assertEquals(probe2.getRef(), probe1.getLastSender());
    }

    @Test
    public void testProbeForward() {
        final TestKit probe1 = new TestKit(system);
        final TestKit probe2 = new TestKit(system);
        probe2.getRef().tell("hello", probe1.getRef());
        probe2.expectMsgEquals("hello");
        probe2.forward(probe1.getRef());
        probe1.expectMsgEquals("hello");
        assertEquals(probe1.getRef(), probe1.getLastSender());
    }
}

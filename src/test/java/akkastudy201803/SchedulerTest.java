package akkastudy201803;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.testkit.javadsl.TestKit;

/**
 * @see https://doc.akka.io/docs/akka/2.5/scheduler.html
 */
public class SchedulerTest {

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

    @Test
    public void testScheduleDemo() {
        TestKit probe = new TestKit(system);
        Cancellable canceller =
            system.scheduler().schedule(
                Duration.ofMillis(50),
                Duration.ofMillis(50),
                probe.getRef(),
                "test",
                system.dispatcher(),
                ActorRef.noSender());
        assertFalse(canceller.isCancelled());
        // after 10ms : not received.
        probe.expectNoMessage(Duration.ofMillis(10));
        // after 100ms : received.
        probe.expectMsgEquals(Duration.ofMillis(100), "test");
        // after 200ms : at least 3 message received.
        List<Object> l = probe.receiveN(3, Duration.ofMillis(200));
        assertTrue(canceller.cancel());
        assertTrue(canceller.isCancelled());

        assertEquals(3, l.size());
        assertEquals("test", l.get(0));
        assertEquals("test", l.get(1));
        assertEquals("test", l.get(2));

        assertFalse(canceller.cancel());
    }

    @Test
    public void testScheduleOnceDemo() {
        TestKit probe = new TestKit(system);
        system.scheduler().scheduleOnce(
            Duration.ofMillis(50),
            probe.getRef(),
            "test",
            system.dispatcher(),
            ActorRef.noSender());
        // after 10ms : not received.
        probe.expectNoMessage(Duration.ofMillis(10));
        // after 100ms : received.
        probe.expectMsgEquals(Duration.ofMillis(100), "test");
    }

}

package akkastudy201803;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.Duration;

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
                Duration.create(50, TimeUnit.MILLISECONDS),
                Duration.create(50, TimeUnit.MILLISECONDS),
                probe.getRef(),
                "test",
                system.dispatcher(),
                ActorRef.noSender());
        assertFalse(canceller.isCancelled());
        // after 10ms : not received.
        probe.expectNoMessage(Duration.create(10, TimeUnit.MILLISECONDS));
        // after 100ms : received.
        probe.expectMsgEquals(Duration.create(100, TimeUnit.MILLISECONDS), "test");
        // after 200ms : at least 3 message received.
        List<Object> l = probe.receiveN(3, Duration.create(200, TimeUnit.MILLISECONDS));
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
            Duration.create(50, TimeUnit.MILLISECONDS),
            probe.getRef(),
            "test",
            system.dispatcher(),
            ActorRef.noSender());
        // after 10ms : not received.
        probe.expectNoMessage(Duration.create(10, TimeUnit.MILLISECONDS));
        // after 100ms : received.
        probe.expectMsgEquals(Duration.create(100, TimeUnit.MILLISECONDS), "test");
    }

}

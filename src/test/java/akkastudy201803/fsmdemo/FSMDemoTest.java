package akkastudy201803.fsmdemo;

import java.util.LinkedList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

/*
 * FSM sample from:
 * @see https://doc.akka.io/docs/akka/2.5/fsm.html#a-simple-example
 */

/* Message Class */

final class SetTarget {
    private final ActorRef ref;

    public SetTarget(ActorRef ref) {
        this.ref = ref;
    }

    public ActorRef getRef() {
        return ref;
    }

    @Override
    public String toString() {
        return "SetTarget{" + "ref=" + ref + '}';
    }
}

final class Queue {
    private final Object obj;

    public Queue(Object obj) {
        this.obj = obj;
    }

    public Object getObj() {
        return obj;
    }

    @Override
    public String toString() {
        return "Queue{" + "obj=" + obj + '}';
    }
}

final class Batch {
    private final List<Object> list;

    public Batch(List<Object> list) {
        this.list = list;
    }

    public List<Object> getList() {
        return list;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Batch batch = (Batch) o;

        return list.equals(batch.list);
    }

    @Override
    public int hashCode() {
        return list.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Batch{list=");
        list.stream().forEachOrdered(e -> {
            builder.append(e);
            builder.append(",");
        });
        int len = builder.length();
        builder.replace(len, len, "}");
        return builder.toString();
    }
}

enum Flush {
    Flush
}

/* State Data */

interface Data {
}

enum Uninitialized implements Data {
    Uninitialized
}

class Todo implements Data {
    private final ActorRef target;
    private final List<Object> queue;

    public Todo(ActorRef target, List<Object> queue) {
        this.target = target;
        this.queue = queue;
    }

    public ActorRef getTarget() {
        return target;
    }

    public List<Object> getQueue() {
        return queue;
    }

    @Override
    public String toString() {
        return "Todo{" + "target=" + target + ", queue=" + queue + '}';
    }

    public Todo addElement(Object element) {
        List<Object> nQueue = new LinkedList<>(queue);
        nQueue.add(element);
        return new Todo(this.target, nQueue);
    }

    public Todo copy(List<Object> queue) {
        return new Todo(this.target, queue);
    }

    public Todo copy(ActorRef target) {
        return new Todo(target, this.queue);
    }
}

public class FSMDemoTest {
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
    public void testBuncherActorBatchesCorrectly() {
        new TestKit(system) {
            {
                final ActorRef buncher = system.actorOf(Props.create(Buncher.class));
                final ActorRef probe = getRef();

                buncher.tell(new SetTarget(probe), probe);
                buncher.tell(new Queue(42), probe);
                buncher.tell(new Queue(43), probe);
                LinkedList<Object> list1 = new LinkedList<>();
                list1.add(42);
                list1.add(43);
                expectMsgEquals(new Batch(list1));
                buncher.tell(new Queue(44), probe);
                buncher.tell(Flush.Flush, probe);
                buncher.tell(new Queue(45), probe);
                LinkedList<Object> list2 = new LinkedList<>();
                list2.add(44);
                expectMsgEquals(new Batch(list2));
                LinkedList<Object> list3 = new LinkedList<>();
                list3.add(45);
                expectMsgEquals(new Batch(list3));
                system.stop(buncher);
            }
        };
    }

    @Test
    public void testBuncherActorDoesntBatchUninitialized() {
        new TestKit(system) {
            {
                final ActorRef buncher = system.actorOf(Props.create(Buncher.class));
                final ActorRef probe = getRef();

                buncher.tell(new Queue(42), probe);
                expectNoMessage();
                system.stop(buncher);
            }
        };
    }
}

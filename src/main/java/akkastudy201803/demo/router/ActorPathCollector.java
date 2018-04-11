package akkastudy201803.demo.router;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import akka.actor.AbstractActor;
import akka.actor.ActorPath;

public class ActorPathCollector extends AbstractActor {

    final CountDownLatch latch;
    final Map<String, Integer> actorPathCounts = new HashMap<>();

    ActorPathCollector(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(ActorPath.class, actorPath -> {
            final String key = actorPath.toString();
            int c = actorPathCounts.getOrDefault(key, 0);
            c++;
            actorPathCounts.put(key, c);
            latch.countDown();
        }).matchEquals("show", m -> {
            final Set<String> actorPaths = actorPathCounts.keySet();
            final int maxlen = actorPaths.stream().mapToInt(s -> s.length()).max().getAsInt();
            final String fmt = "%-" + maxlen + "s | %s%n";
            for (Entry<String, Integer> e : actorPathCounts.entrySet()) {
                final String actorPath = e.getKey();
                final int c = e.getValue();
                System.out.printf(fmt, actorPath, String.join("", Collections.nCopies(c, "+")));
            }
        }).build();
    }
}

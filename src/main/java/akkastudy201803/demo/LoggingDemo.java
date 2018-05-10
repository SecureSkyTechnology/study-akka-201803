package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import com.typesafe.config.Config;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.event.MarkerLoggingAdapter;
import akka.event.slf4j.Slf4jLogMarker;

/**
 * @see https://doc.akka.io/docs/akka/2.5/logging.html
 */
public class LoggingDemo implements Runnable {

    static class LoggingDemoActor extends AbstractActorWithTimers {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchAny(m -> {
                log.error("error-level demo : received Object.toString=[{}]", m);
                log.warning("warning-level demo : received Object.toString=[{}]", m);
                log.info("info-level demo : received Object.toString=[{}]", m);
                log.debug("debug-level demo : received Object.toString=[{}]", m);
                // NO TRACE LEVEL :P
                log.info("placeholder demo1 : {}, {}, {}", "hello", 100, new URL("http://localhost/hello"));
                log.info("placeholder demo2 : {}, {}, {}", new String[] { "abc", "def" });

                /* see : https://doc.akka.io/docs/akka/2.5/logging.html#using-markers
                 * see : https://github.com/akka/akka/blob/master/akka-slf4j/src/test/scala/akka/event/slf4j/Slf4jLoggerSpec.scala
                 */
                final MarkerLoggingAdapter mlog = Logging.withMarker(this);
                final Marker slf4jRawMarker = MarkerFactory.getMarker("slf4j mark");
                final Slf4jLogMarker marker = new Slf4jLogMarker(slf4jRawMarker);
                mlog.error(marker, "slf4j marker logging, error-level demo");
                mlog.warning(marker, "slf4j marker logging, warning-level demo");
                mlog.info(marker, "slf4j marker logging, info-level demo");
                mlog.debug(marker, "slf4j marker logging, debug-level demo");
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        final Config config = system.settings().config();
        System.out.println("akka.loglevel = " + config.getString("akka.loglevel"));
        System.out.println("akka.stdout-loglevel = " + config.getString("akka.stdout-loglevel"));
        System.out.println("akka.logging-filter = " + config.getString("akka.logging-filter"));
        List<String> loggers = config.getStringList("akka.loggers");
        for (int i = 0; i < loggers.size(); i++) {
            System.out.println("akka.loggers[" + i + "] = " + loggers.get(i));
        }
        System.out.println("akka.log-config-on-start = " + config.getString("akka.log-config-on-start"));

        System.out.println("Test Dead Letter message logging...");
        for (int i = 0; i < 30; i++) {
            final String msg = "Message to Dead Letter No." + i;
            system.deadLetters().tell(msg, ActorRef.noSender());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            final ActorRef logdemo = system.actorOf(Props.create(LoggingDemoActor.class));

            logdemo.tell("hello, akka logging", ActorRef.noSender());

            System.out.println(">>> Press ENTER to exit <<<");
            system.log().error("error-level demo : ActorSystem#log()");
            system.log().warning("warning-level demo : ActorSystem#log()");
            system.log().info("info-level demo : ActorSystem#log()");
            system.log().debug("debug-level demo : ActorSystem#log()");
            br.readLine();
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
        System.out.println(
            "\ntry : java -Dconfig.file=custom-application.conf -jar (...)/study-akka-201803-1.0.0-SNAPSHOT.jar");
    }
}

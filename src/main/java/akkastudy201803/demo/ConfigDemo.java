package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * @see https://github.com/lightbend/config
 * @see https://doc.akka.io/docs/akka/2.5/general/configuration.html
 */
public class ConfigDemo implements Runnable {

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        final Config config = system.settings().config();
        System.out.println("config-in-reference-conf = " + config.getString("config-in-reference-conf"));
        System.out.println("env1 = " + config.getString("env1"));
        System.out.println("env2 = " + config.getString("env2"));
        System.out.println("configdemo1.key1.key1a = " + config.getString("configdemo1.key1.key1a"));
        System.out.println("configdemo1.key1.key1b = " + config.getString("configdemo1.key1.key1b"));
        List<Integer> key2a = config.getIntList("configdemo1.key2.key2a");
        for (int i = 0; i < key2a.size(); i++) {
            System.out.println("configdemo1.key2.key2a[" + i + "] = " + key2a.get(i));
        }
        List<Boolean> key2b = config.getBooleanList("configdemo1.key2.key2b");
        for (int i = 0; i < key2b.size(); i++) {
            System.out.println("configdemo1.key2.key2b[" + i + "] = " + key2b.get(i));
        }
        List<String> key3 = config.getStringList("configdemo1.key3");
        for (int i = 0; i < key3.size(); i++) {
            System.out.println("configdemo1.key3[" + i + "] = " + key3.get(i));
        }
        Config configdemo2 = config.getConfig("configdemo2");
        System.out.println("configdemo2.key1 = " + configdemo2.getString("key1"));
        System.out.println("configdemo2.key2 = " + configdemo2.getString("key2"));

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            System.out.println(">>> Press ENTER to exit <<<");
            br.readLine();
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
        System.out.println(
            "\ntry : java -Dconfig.file=custom-application.conf -jar (...)/study-akka-201803-1.0.0-SNAPSHOT.jar");
    }
}

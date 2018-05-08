package akkastudy201803;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import akkastudy201803.demo.ActorMemoryLeakDemo;
import akkastudy201803.demo.ActorMemoryLeakSolutionDemo;
import akkastudy201803.demo.ActorReceiveTimeoutDemo;
import akkastudy201803.demo.ActorStashDemo;
import akkastudy201803.demo.AkkaQuartzSchedulerDemo;
import akkastudy201803.demo.AkkaQuartzSchedulerFaultDemo;
import akkastudy201803.demo.AkkaQuartzSchedulerForPinnedDispatcherDemo;
import akkastudy201803.demo.AkkaQuartzSchedulerSkipLongRunningDemo;
import akkastudy201803.demo.ConfigDemo;
import akkastudy201803.demo.FSMAutoStateTimeoutDemo;
import akkastudy201803.demo.FSMTimerDemo;
import akkastudy201803.demo.LoggingDemo;
import akkastudy201803.demo.PinnedDispatcherDemo;
import akkastudy201803.demo.SingleThreadedScheduleDemo;
import akkastudy201803.demo.ThreadStarvationDemo;
import akkastudy201803.demo.ThreadStarvationSolutionDemo;
import akkastudy201803.demo.TickTackSchedulerDemo;
import akkastudy201803.demo.TimerSchedulerDemo;
import akkastudy201803.demo.router.BalancingPoolDemo;
import akkastudy201803.demo.router.BroadcastPoolDemo;
import akkastudy201803.demo.router.RandomPoolDemo;
import akkastudy201803.demo.router.RoundRobinGroupDemo;
import akkastudy201803.demo.router.RoundRobinPoolDemo;

public class Main {

    static Runnable[] demos = new Runnable[] {
        // @formatter:off
        new TickTackSchedulerDemo(),
        new TimerSchedulerDemo(),
        new LoggingDemo(),
        new ConfigDemo(),
        new AkkaQuartzSchedulerDemo(),
        new AkkaQuartzSchedulerForPinnedDispatcherDemo(),
        new AkkaQuartzSchedulerFaultDemo(),
        new AkkaQuartzSchedulerSkipLongRunningDemo(),
        new ActorReceiveTimeoutDemo(),
        new ActorStashDemo(),
        new ThreadStarvationDemo(),
        new ThreadStarvationSolutionDemo(),
        new PinnedDispatcherDemo(),
        new RoundRobinPoolDemo(),
        new RandomPoolDemo(),
        new BroadcastPoolDemo(),
        new BalancingPoolDemo(),
        new RoundRobinGroupDemo(),
        new FSMAutoStateTimeoutDemo(),
        new FSMTimerDemo(),
        new ActorMemoryLeakDemo(),
        new ActorMemoryLeakSolutionDemo(),
        new SingleThreadedScheduleDemo(),
        // @formatter:on
        };

    public static void main(String[] args) throws Exception {
        System.out.println("Welcome to akka demos!!");
        for (int i = 0; i < demos.length; i++) {
            Runnable r = demos[i];
            System.out.println("Demo No.[" + i + "] - " + r.getClass().getCanonicalName());
        }
        System.out.print("Enter demo number (exit for -1):");
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            int i = Integer.parseInt(br.readLine().trim());
            if (i >= demos.length) {
                System.out.println("Enter 0 - " + (demos.length - 1) + " number.");
                return;
            }
            if (i < 0) {
                return;
            }
            new Thread(demos[i]).start();
        } catch (NumberFormatException e) {
            System.out.println("Enter 0 - " + (demos.length - 1) + " number.");
        } finally {
            System.out.println("Exiting main thread...");
        }
    }
}

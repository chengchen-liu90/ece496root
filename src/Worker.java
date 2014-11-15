/*
 * Responsibility for Worker:
 * 1.Run Benchmark of current available node, get speed information for Scheduler
 * 2.Get Hardware information and Benchmark information of the current node
 * 3.Create child directories 'Worker1, Worker2, etc..' for nodes
 * 4.Push a job from 'Queued Job' into 'Current Job' and execute it
 * 5.Watch Current Job, if it's done delete it
 * 6.Update result in 'results'
 */
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.File;
import java.math.BigInteger;
import java.net.*;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.data.Stat;


public class Worker{	//worker node, need to know hardware configurations
	static ZkConnector zkc;

    final static String JOB_TRACKER_PATH = "/jobTracker";
	final static String WORKER_PATH = "/worker";
	final static String JOBS_PATH = "/jobs";
	final static String RESULT_PATH = "/result";
	final static String JOBPOOL_PATH = "/jobpool";

	Watcher fsWatcher;
	Watcher WorkerWatcher;
	String Workerid=null;			//workerid of this node(worker)
	String[] hardware_info;
	long core=0, mem_total_JVM, mem_free, mem_max, mem_cur_JVM;
	long maxMemory;
	
	public static void main(String[] args) throws IOException, KeeperException, InterruptedException, NumberFormatException, ClassNotFoundException {
		if (args.length != 2) {
            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. Worker zkServer:zkPort jobTrackerPort");
            return;
        }
		List<String> jobs = null, tasks = null; 
		Worker worker = new Worker(args[0]);
	    worker.Hardware_config();

		System.out.println("Sleeping...");
		while (true) {
		    try{ Thread.sleep(5000); } catch (Exception e) {}
		} 
	 }
	
	public Worker(String hosts){
		zkc = new ZkConnector();
        try {
            zkc.connect(hosts);
            
			Stat stats = zkc.exists("/worker",null);
			if(stats==null) {						//create worker root directory
				zkc.getZooKeeper().create("/worker", null, ZkConnector.acl, CreateMode.PERSISTENT);
				System.out.println("/worker created");
			}
			
			//create child directory of worker, assign id to that worker
			String path = zkc.getZooKeeper().create("/worker/worker-", null, ZkConnector.acl, CreateMode.EPHEMERAL_SEQUENTIAL);
			String[] temp = path.split("-");
			Workerid = temp[1];			//create workerid of this worker
			
        } catch(Exception e) {
            System.out.println("Zookeeper connect "+ e.getMessage());
        }
		
        WorkerWatcher = new Watcher(){
        	 @Override
             public void process(WatchedEvent event) {
        		 		;		//do something to handle event, need to figure out how to write it
					}			
        };
	}
	
	/*private execute{
		  	p = Runtime.getRuntime().exec("host -t a " + domain);
		    p.waitFor();
		 
		    BufferedReader reader = 
		         new BufferedReader(new InputStreamReader(p.getInputStream()));
		 
		    String line = "";			
		    while ((line = reader.readLine())!= null) {
			sb.append(line + "\n");
		    }
	}*/
	
	public static void Hardware_config(){
		/* Total number of processors or cores available to the JVM */
	    System.out.println("Available processors (cores): " + 
	        Runtime.getRuntime().availableProcessors());

	    /* Total amount of free memory available to the JVM */
	    System.out.println("Free memory (bytes): " + 
	        Runtime.getRuntime().freeMemory());

	    /* This will return Long.MAX_VALUE if there is no preset limit */
	    long maxMemory = Runtime.getRuntime().maxMemory();
	    /* Maximum amount of memory the JVM will attempt to use */
	    System.out.println("Maximum memory (bytes): " + 
	        (maxMemory == Long.MAX_VALUE ? "no limit" : maxMemory));

	    /* Total memory currently available to the JVM */
	    System.out.println("Total memory available to JVM (bytes): " + 
	        Runtime.getRuntime().totalMemory());

	    /* Get a list of all filesystem roots on this system */
	    File[] roots = File.listRoots();

	    /* For each filesystem root, print some info */
	    for (File root : roots) {
	      System.out.println("File system root: " + root.getAbsolutePath());
	      System.out.println("Total space (bytes): " + root.getTotalSpace());
	      System.out.println("Free space (bytes): " + root.getFreeSpace());
	      System.out.println("Usable space (bytes): " + root.getUsableSpace());
	    }
	}
	
	
	private void benchmark(){
		;
	}
	
	
}
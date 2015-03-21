/* Each Worker represent a Node for data Analysis
 *
 *
 * Responsibility for Worker:
 * 1.Take in input for Dataset input file
 * 2.Initialize NPAIRS program (without Qvalue)
 * 3.Connected to Zookeeper and create FreeWorkers on Zookeeper 
 * 4.Create Watches for /Jobs/worker-myid
 * 5.Create Watches for Result/Jobid
 * 6.Execute Jobs assgined from Scheduler with specified Q value
 * 8.Check return code of run_NPAIRS.sh script, if error occurs, re-execute the same Job
 * 9.Create Result Object to put result onto Zookeeper
 * 10.Wait until all jobs all done, remove Worker and its FreeWorkers
 * 11.exit system/quit process
 *
 *
	@Author Ruoyang (Leo) Wang, ruoyang.wang@mail.utoronto.ca
 */


import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.data.Stat;


public class Worker{	
	//change flag to false will disable all print statements
	private static boolean DEBUG = true;

	//Zookeeper connector object
	static ZkConnector zkc;
	static WorkerObject wk;

	//hostname of this node
	static String hostname;
	
	//HashTable stores multi-thread for execution of NPAIRS 
	static HashMap<String, WorkerThreadHandler> checkMap = new HashMap<String,WorkerThreadHandler>();
	static int index=0;

	//max_execution indicates the max Number of Jobs this node can run in parallel
	static int max_executions=0;
	static int iterator=0;
	static int Qcount = 0;
	static WorkerThreadHandler worker_t[];


	// Zookeeper directory pathes, each path is a component
    final static String JOB_TRACKER_PATH = "/jobTracker";
	final static String WORKER_PATH = "/worker";
	final static String JOBS_PATH = "/jobs";
	final static String RESULT_PATH = "/result";
	final static String SEQ_PATH = "/seq";
	final static String JOBPOOL_PATH = "/jobpool";
	final static String FREE_WORKERS_PATH = "/freeWorkers";
	
	static long startTime;
	static String CurrentState;
	static String inputName;
	static String JobName;

	//Executor for pool of threads to be used
	ExecutorService executor;


	/*watchers for worker and results*/
	static Watcher WorkerWatcher;
	static Watcher ResultWatcher;
	static Watcher ResultChildWatcher;

	//workerid of this node(worker), generated by Zookeeper
	static String Workerid=null;			
	String Workerpath=null;
	
	//long benchmarkTime = 0;
	static long executionTime = 0;
	
	public static void main(String[] args) throws IOException, KeeperException, InterruptedException, NumberFormatException, ClassNotFoundException {
		if (args.length != 3) {
            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. Worker zkServer:zkPort");
            return;
        }
		
		List<String> jobs = null, tasks = null; 
		String myHostName;
		String WorkerServerInfo;
		
		/*get input file name for initializing NPAIRS*/
		inputName = args[1];
		JobName = args[2];
		
		try{
			myHostName = InetAddress.getLocalHost().getHostName();
		}catch (Exception e){
			System.out.println("Failed to get host name");
            e.printStackTrace();
			return;
		}		
		WorkerServerInfo = myHostName;
		System.out.println( args[0]);

		/*creating WorkerObject to handle Worker information
			1. retrieving Node power (how many jobs this node can run in parallel)
			2. storing all this Node's useful information
		*/
		Create_WorkerObj(Workerid);				
		max_executions= wk.Node_power(inputName);

		//Constructing Worker
		Worker wker = new Worker(args[0],WorkerServerInfo);
		wker.createPersistentFolders();
		//Building Watches
		wker.Building(args[1]);
		

		//currently Worker is idling
		System.out.println("Sleeping...");
		while (true) {
		    try{ Thread.sleep(5000); } catch (Exception e) {}
		} 
	 }
	 
	 
	public static void Thread_complete(long execution, int retcode, String currentJob, int threadNum, int Q, String location,int jobID){
			synchronized(zkc){
				
				iterator_decrement();
				String WorkerJobPath = JOBS_PATH+"/worker-"+Workerid;
				if(DEBUG)	
						System.out.println("finish executing jobs....."+WorkerJobPath+"/"+currentJob);
				
				//removing this job if a thread c=has completed it
				zkc.delete(WorkerJobPath+"/"+currentJob,-1);
				
				//remove a thread from checkMap						
				checkMap.remove(currentJob);
				
				//getting the execution time and worker info
				wk.executionTime = execution;
				String wkInfo = wk.toNodeDataString();

				/*creating a result Object
					-putting result object onto Zookeeper 
					-result object represents the finished job's information
				*/
				ResultObject RO = new ResultObject(hostname, location, execution, jobID, Q);
				String resultInfo = RO.toNodeDataString();
				index+=1;

				//create free worker object (a job finished means a freeWorker can be created)
				zkc.create(										
						FREE_WORKERS_PATH+"/"+"worker-"+Workerid+":"+index,       // Path
						wkInfo,   // information
						CreateMode.EPHEMERAL  	// Znode type, set to EPHEMERAL.
				);
				
				if(DEBUG)
					System.out.println(jobID+"        " +resultInfo);

				//putting the result Object onto Zookeeper
				zkc.create(
						RESULT_PATH+"/"+jobID+"/"+RO.get_Result_Node_Name(),
						resultInfo,
						CreateMode.PERSISTENT
				);
				
				if(DEBUG)
					System.out.println("what's the iterator count: ----  "+iterator);
				
				if(iterator == 0){			//if all worker are completed (Free)
					if(DEBUG)
						System.out.println("current worker free, set watch for resultChild:  ");
					String ResultChildrenPath= RESULT_PATH+"/"+JobName;
					List<String> children=zkc.getChildren(ResultChildrenPath);
					
					
					if(children.size()==Qcount){
						JobStatusObject jso = new JobStatusObject(Qcount);
						jso.startTime = startTime;
						jso.completed();
						
						//set data for worker
						zkc.setData(											
						            RESULT_PATH+"/"+JobName,       // Path
						            jso.toZnode(),  // znode information
									-1
						           );	
					}
					else
						zkc.getChildren(RESULT_PATH+"/"+JobName, ResultChildWatcher);
				}
			}	
	}
	
	
	public Worker(String hosts, String WorkerServerInfo){

		zkc = new ZkConnector();
        try {
            zkc.connect(hosts);
            
			Stat stats = zkc.exists("/worker",null);
			if(stats==null) {						//create worker root directory for the first time
				zkc.getZooKeeper().create("/worker", null, ZkConnector.acl, CreateMode.PERSISTENT);
				System.out.println("/worker created");
			}
			
			/*WorkerWatcher implementation:
				1.Whenever current Worker path children changed will call Worker_Watch()
				2.inside Worker_Watch() will check if having jobs assigned
				3.Worker_Watch() created thread to execute jobs
			*/
			WorkerWatcher = new Watcher(){
	        	 @Override
	             public void process(WatchedEvent event) {
					try{Thread.sleep(5000);}
					catch(Exception e){}
					 
	                 if(DEBUG)
					 	System.out.println("waiting for jobs");
	                 switch (event.getType()){
	                 	case NodeChildrenChanged:
	                 		Worker_Watch();
	                 		
	                 }
					zkc.getChildren(JOBS_PATH+"/worker-"+Workerid, WorkerWatcher );
						List<String> children=zkc.getChildren(JOBS_PATH+"/worker-"+Workerid);
				
					if(DEBUG)
						System.out.println("how many children after? ------ "+children.size());
					
                    
	        	 	}			
	        };

	        
			/*implementation for resutlWatcher
				1.keep track of if all jobs are finished
				2.if not all finished then this worker wait
				3.if all jobs done then worker exit
			*/
	        ResultWatcher = new Watcher(){
				@Override
	             public void process(WatchedEvent event) {
			         int retcode=0;

			         retcode =Result_Watch();

			         if(retcode == 0)
						zkc.getChildren(RESULT_PATH, ResultWatcher);
					 else
						zkc.getData(RESULT_PATH+"/"+JobName,ResultWatcher,null);
	             }
	        };


//------------------------------------------------Result Children Watcher construction----------------------------------------------------------
	        ResultChildWatcher = new Watcher(){
				@Override
	             public void process(WatchedEvent event) {
	             	switch (event.getType()){
					case NodeChildrenChanged:
	                 		try {
	                 			synchronized(zkc){
			             			String ResultChildrenPath= RESULT_PATH+"/"+JobName;
									List<String> children=zkc.getChildren(ResultChildrenPath);
									if(children.size()==Qcount){
											JobStatusObject jso = new JobStatusObject(Qcount);
											jso.startTime = startTime;
											jso.completed();

											
											/*set data for currentJob under RESULT_PATH*/
											zkc.setData(											
														RESULT_PATH+"/"+JobName,    
														jso.toZnode(),  // JobStatusObject information
														-1
											);	
									}	
										
								}

	                 		}
	                 		catch(Exception e) {
	                            e.printStackTrace();
	                        }

	             }
	             if(iterator ==0 && CurrentState.equalsIgnoreCase("ACTIVE"))			//start watching the children only if all workers are free
	             	zkc.getChildren(RESULT_PATH+"/"+JobName, ResultChildWatcher);
	            }
	        };
//=======================================================================================================================================
			
			
        } catch(Exception e) {
            System.out.println("Zookeeper connect "+ e.getMessage());
        }
		
       
	}
	

	public int Worker_Watch(){
			try {
	                 String WorkerJobPath= JOBS_PATH+"/worker-"+Workerid;
	                 String currentJob="dummy";
	                 String taskinfo=null;
	                 int retcode;
         			synchronized(zkc){										
			                
			                	Stat stat = zkc.exists(WorkerJobPath, null);

			                	List<String> children=zkc.getChildren(WorkerJobPath);
							if(DEBUG)
								System.out.println("how many children inside watch right now? ===== "+children.size());
							JobObject jo = new JobObject();
			                	for(String child: children){
			                		System.out.println(child);

			                		if(checkMap.get(child)==null){

									taskinfo = zkc.getData(WorkerJobPath+"/"+child, null, stat);

									if(taskinfo!=null){
										if(DEBUG)
											System.out.println("taskinfo is not null, can execute now");
					            			currentJob = child;													
							        		jo.parseJobString(taskinfo);
							        		int Qvalue= jo.qValue;
										int jobID =jo.jobId;
							        		String inputLocation= jo.inputFile;
							        		WorkerThreadHandler t = new WorkerThreadHandler();
							        		t.setVal(inputName, Qvalue, currentJob, jobID);
					            			checkMap.put(currentJob,t);

										/*use executor to submit the pool of threads for execution */
										executor.submit(t);
					         			/*synchronized counter incrementation*/
					            			iterator_increment();
									}
			                			
			                		}
			                	}
	                    	
	                  }	
	                   
                     return 0;
	
            } catch (Exception e) {
                e.printStackTrace();
				return -1;
            }
	
	}



	/* detail definition of Result_Watch:
	 *	1. if worker is online but there is no Job from Client, return 0
	 *	2. if worker is online and found Job, return 1 to indicate ready
	 *	
	 *	Result_Watch() method is used in watch set to monitor Result, if jobs under result are all done, can exit Worker
	 */
	public int Result_Watch(){
		try {
	               System.out.println("inside the ResultWatcher for watching result root");
	                 				
	                 				
	               String Jobinfo =null;
			       synchronized(zkc){
		         			String ResultChildrenPath= RESULT_PATH+"/"+JobName;
							Stat stat = zkc.exists(ResultChildrenPath, null);	
							Jobinfo = zkc.getData(ResultChildrenPath, null, stat);
							
						}
						if(Jobinfo !=null){
							String[] tokens = Jobinfo.split(":");
							System.out.println("check tokens:  ------ "+tokens[0]+" ---- "+tokens[1] +"------ "+tokens[2]);
							
							/* if 0 means first time initialization, update to argument value*/
							if(Qcount ==0){
								Qcount = Integer.parseInt(tokens[0]);
								startTime = Long.parseLong(tokens[2]);
							}
							
							if(tokens[1].equalsIgnoreCase("ACTIVE")){

								CurrentState= "ACTIVE";
							}

							else if(tokens[1].equalsIgnoreCase("KILLED")||tokens[1].equalsIgnoreCase("COMPLETED")){
								if(DEBUG)
									System.out.println("Kill or completed request, ready to exit ;)");
								CurrentState= "DONE";
								//scheduler ask to kill the whole Job set, so Halt the program and exit JVM
								System.exit(-1);		
							}
							return 1;
						}
					

         		}
         		catch(Exception e) {
                    e.printStackTrace();
                }	
                return 0;	
	
	}
	
	
	/*iterator to check if all threads within this worker process are done
	 * main purpose is to determine if Worker can exit
	 * Use synchornization to avoid concurrent operation 
	 */
	public synchronized static void iterator_increment(){
		iterator+=1;
	}

	public synchronized static void iterator_decrement(){
		iterator-=1;
	}




	public void Building(String filename){
			//create child directory of worker, assign EPHEMERAL_SEQUENTIAL unique id to that worker
			try{
				
				Workerpath = zkc.getZooKeeper().create(WORKER_PATH+"/"+"worker-", null, ZkConnector.acl, CreateMode.EPHEMERAL_SEQUENTIAL);
				String[] temp = Workerpath.split("-");
				Workerid = temp[1];			//create workerid of this worker
				wk.setNodeName("worker-"+Workerid);
				get_Host_Name();
				
				if(DEBUG)
					System.out.println("node power is #:  "+max_executions +"  print node ID: "+Workerid);
				
				if(max_executions ==-1)
					max_executions=1;
				wk.setHostName(this.hostname);
				String info = wk.toNodeDataString();
				System.out.println(info);
				

				/*watch worker, ready to work*/
				zkc.getChildren(JOBS_PATH+"/worker-"+Workerid, WorkerWatcher );
				

				//use a cachedThreadPool method, re-use old threads to avoid too much overhead for creating threads every job
				this.executor = Executors.newCachedThreadPool();
	
				//set data for worker
				zkc.setData(											
		                    WORKER_PATH+"/"+"worker-"+Workerid,       // Path
		                    info,  // information
							-1
		                    );

				for(index=0;index<max_executions;index++){
					if(DEBUG)
						System.out.println("creating freeworkerObjects");

					/*during construction, create freeWorkers according to this Worker's computation power
						-if workerObject return 5, will create 5 freeWorkers onto Zookeeper
						-freeWorker will notify jobTracker
						-Scheduler will assign 5 tasks to this worker at a time
					*/
					zkc.create(					 				//create free worker object
			                FREE_WORKERS_PATH+"/"+"worker-"+Workerid+":"+index,       // Path
			                info,   // information
			                CreateMode.EPHEMERAL  	// Znode type, set to EPHEMERAL.
			     	);

				}
				

		                    
		        
				/*watch result, wait to get Job*/
				int retcode = Result_Watch();
				if(retcode == 0)
					zkc.getChildren(RESULT_PATH, ResultWatcher);
				else
					zkc.getData(RESULT_PATH+"/"+JobName,ResultWatcher,null);

				/*watch worker, ready to work*/
				List<String> children=zkc.getChildren(JOBS_PATH+"/worker-"+Workerid);
				if(children.size()>0)
					Worker_Watch();
				zkc.getChildren(JOBS_PATH+"/worker-"+Workerid, WorkerWatcher );
				
				}catch(Exception e) {
            			System.out.println("Building Worker: "+ e.getMessage());
        		}
		}

	




	  private static synchronized void createOnePersistentFolder(String Path, String value){	
		// create folder
 		Stat stat = zkc.exists(Path,null);
        if (stat == null) { 
	        System.out.println("Creating " + Path);
	        Code ret = zkc.create(
	                    Path,         // Path of znode
	                    value,        // Data
	                    CreateMode.PERSISTENT   // Znode type, set to PERSISTENT.
	                    );
	        if (ret == Code.OK) {
				System.out.println(Path.toString()+" path created!");
	   	 	} else {
				System.out.println(Path.toString()+" path creation failed!");
			}
        }
    }

	/**
	 * Create persistent folders.
 	 */
    private void createPersistentFolders(){
		// create jobs folder
		createOnePersistentFolder(JOBS_PATH, null);

		// create seq folder
		createOnePersistentFolder(SEQ_PATH, "1");

		// create worker folder
		createOnePersistentFolder(WORKER_PATH, null);
		
		createOnePersistentFolder(FREE_WORKERS_PATH, null);
		// create result folder
		createOnePersistentFolder(RESULT_PATH, null);

		// create jobpool folder
		createOnePersistentFolder(JOBPOOL_PATH, null);
    }

	//Get hostname of this Node
	public void get_Host_Name(){
		try{
			Process p = Runtime.getRuntime().exec("hostname");
				p.waitFor();		//create shell object and retrieve cpucore number
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			this.hostname=br.readLine();
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	/* After getting CPUcore, exeuctionTime information, need to update the Current WorkerObject*/
	public static void Update_WorkerObj(){
		try{
			Process p = Runtime.getRuntime().exec("sh ../src/cpu_core.sh");
    			p.waitFor();		//create shell object and retrieve cpucore number
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			wk.cpucore = br.readLine();
			wk.executionTime= executionTime;
			
		
		}catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	/*Creating WorkerObject which is used to store all useful Worker(This node)'s information*/
	private static void Create_WorkerObj(String wkid){
			String wkname= "worker-"+wkid;
			wk= new WorkerObject(wkname);
			Update_WorkerObj();
	}
	
	

	
	
}

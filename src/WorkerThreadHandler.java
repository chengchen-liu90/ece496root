import java.net.*;
import java.io.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class WorkerThreadHandler implements Runnable {
		String inputLocation;
		String currentJob;
		static private int threadNumber=0;
		int Qvalue;
		@Override
		public void run() {
				long startTime = System.nanoTime();
				int retcode = -1;					     			
				 try{ 
													//mock of execution, depends on where we put zookeeper and NPAIRS executables we can change shell command 
					System.out.println("executing jobs.....");
					String command = "sh ../execute/execute.sh " + this.inputLocation+" "+ this.Qvalue;				
					Process p = Runtime.getRuntime().exec(command);
					retcode=p.waitFor();
									
					} catch (Exception e) {
						e.printStackTrace();
					}	//TODO:assume this is running the child node for now
												
							                	 	
					long endTime = System.nanoTime();	                            		
					long executionTime = (endTime - startTime);
					Worker.Thread_complete(executionTime, retcode, this.currentJob, this.threadNumber);
		}
		
		public void setVal(String input, int val, String currentJob){
			this.inputLocation = input;
			this.Qvalue = val;
			this.currentJob = currentJob;
			this.threadNumber+=1;
		
		}
		public int get_thread_number(){
			return this.threadNumber;
		}
		
		
}

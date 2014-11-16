public class JobObject {

	public Integer jobId;
	public Integer nValue;

	// location of the input file for the job.
	public String inputFile; 


//** Below are params that are not currently used but anticipated to be used. **

	// location of the output file for the job.
	public String outputFile; 
	// estimated time by the scheduler.
	public Integer estimatedTime;
	// actual completion time
	public Integer completionTime;
	// reqirement for this job
	public String requirement;
//*******************************************************************************

	public final String DELIMITER = ":";

	// constuctor

    public JobObject(Integer jobId, Integer nValue) {
		this.jobId = jobId;
		this.nValue = nValue;
    };

    public JobObject(Integer jobId, Integer nValue, String inputFile) {
		this.jobId = jobId;
		this.nValue = nValue;
		this.inputFile = inputFile;
    };

	public void parseJobString(String jobString) {
		String [] partial = jobString.split(DELIMITER);
		
		this.jobId = Integer.parseInt(partial[0]);
		this.nValue = Integer.parseInt(partial[1]);
		this.inputFile = partial[2];
	}

	public void parseResultString(String resultString) {
		String [] partial = resultString.split(DELIMITER);
		
		this.jobId = Integer.parseInt(partial[0]);
		this.nValue = Integer.parseInt(partial[1]);
		this.outputFile = partial[2];
		this.completionTime = Integer.parseInt(partial[3]);
	}

	public String toJobString() {
		// Formate: "nValue:inputFile"
		return jobId.toString() + DELIMITER + nValue.toString() + DELIMITER + inputFile;
	}
	
	public String toResultString() {
		// Formate: "nValue:outputFile:completionTime"
		return jobId.toString() + DELIMITER + nValue.toString() + DELIMITER + outputFile + DELIMITER + completionTime.toString();
	}

}

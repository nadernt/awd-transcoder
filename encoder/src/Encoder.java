/*
 * MIT License
 *
 * Copyright (c) 2013-01-31 Nader Naderi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.SQL.SQL;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.debuger.Debuger;
import com.processinfo.ProcessInfo;
import com.restcommands.RestCommands;

public class Encoder {

	static AmazonEC2      ec2;
	static AmazonS3 s3;
	protected static Properties config;
	protected static AWSCredentials credentials;
	private static String DefaultSettingsPath;
	//private static Scanner scan = new Scanner(System.in);


	private static RestCommands restcommands;
	private static String MyInstancePrivateIP;
	private static String MyInstanceID;
	private static ProcessInfo processinfo = new ProcessInfo();
	private static int MaxNumberOfJobsSlotPerServer;
	private static int oldNumOfRunnedFFmpeg=0;
	private static int TryTimeToResetConductorFile=0;
	public static Debuger DebugMessage;
	/*
	 * Main encoding scheduler thread and variables 
	 */
	private static int EncoderMainLoopTimingSequence=0;
	private static ScheduledExecutorService EncodingMainSchedulerTimer = Executors.newSingleThreadScheduledExecutor();

	/*
	 * Main encoding progress report thread and variable 
	 */
	private static int EncodingProgressReportTimingSequence=0;
	private static ScheduledExecutorService EncodingProgressReportTimer = Executors.newSingleThreadScheduledExecutor();

	/*
	 * Statistics encoding scheduler thread variables 
	 */
	private static int EncodStaticNumbOfItemSendAsBunch=0;
	private static String EncodStaticMetaInfoSendAsBunch="";

	// Timer to calculate idle time of machine if it is jobless for a long time it will terminate itself to avoid over payment for EC2 instance. 
	private static ScheduledExecutorService SleepTimer = Executors.newSingleThreadScheduledExecutor();
	private static int SleepTimerCounter=0;
	private static int JoblessTimerLoopTimingSequence=0;
	/*
	 * Purge garbage files scheduler thread. 
	 */
	private static int EncoderVaccumCleanerTimingSequence=0;
	private static ScheduledExecutorService EncoderVaccumCleanerSchedulerTimer = Executors.newSingleThreadScheduledExecutor();

	// flag for stopping all processes 
	private static boolean blHaltEverything=false; 

	private static boolean blPauseEverything=false;

	/* The flag which indicates this EC2 instance has the first rank in the servers list. */
	private  static boolean IamSenior=false;

	interface Constants {
		public static final String _ERR_SqlQueryWasntSuccess = "SQL Query Was Not Success";
		public static final String _ERR_CannotAccessResourceIDIP = "Cannot access resources ip & id";
		public static final String _ERR_GeneralError = "General Error";
		public static final String _ERR_ErrorLogServersInDB = "Error log servers in DB";
		public static final String _ERR_ErrorAddEC2Instance = "Error add EC2 instance";
		public static final String _ERR_ErrorAddEC2InstanceInConductor = "Error add EC2 instances in Conductor";
		public static final String _ERR_FatalError = "Fatal Erro";
		public static final String _ERR_DBAccessError = "DB access error";
		public static final String _ERR_ServiceIsNotAvailable = "serverr";
		public static final String _ERR_CouldnotAccessSystemFile ="Could not access system file!";
		/**
		 * Flag for current process stage and success and fails for internal usage
		 * of servers. The values are as follow: 
		 * if = -1 -> Error had found in the encoding file.
		 * if = 0  -> The process is in the queue list to be picked up by one of servers for process. 
		 * if = 1  -> Queuing the file and waiting for encoding to be start.
		 * if = 2  -> Encode is progressing.
		 * if = 3  -> Encoding finished successfully.
		 **/

		public static final String _CUR_ENC_PROC_ERR_FOUND = "-1";
		public static final String _CUR_ENC_PROC_WAIT_FOR_PICKUP = "0";
		public static final String _CUR_ENC_PROC_PICKED_WAIT_TOSTART_ENCODE = "1";
		public static final String _CUR_ENC_PROC_ENCODE_PROGRESSING = "2";
		public static final String _CUR_ENC_PROC_ENCODE_FINISHED = "3";
	}

	/**
	 * Encoder class Main method.
	 *  
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args){

		try{

			System.out.println("===========================================");
			System.out.println("Encoder v1.0");
			System.out.println ("OS Info: " + System.getProperty("os.name") + System.getProperty("os.version") + System.getProperty("os.arch"));
			System.out.println("===========================================");

			// initialization 
			init();

			// Main convention  loop of the Encoder program. 
			MyDotComEncoder();

			// Server idle time calculation in case of long time it shutdowns the server.
			SleepTimerManagerThread();

			// Encoding progress report thread to the DB.
			EncodingProgressReport();

			// Cleans garbage files in the  temporary conversion directory based on a schedule.
			VacuumCleaners();

		}
		catch (Exception e){
			System.out.println("Exception in main function: " + e.getMessage());
			System.exit(1);
		}

	}

	/**
	 * Initialization of program "connect to DB, load configurations, connect to Amazon, 
	 * update this machine state (ready to work) in the DB for the Conductor".
	 * 
	 */
		private static void init() {

			try{

				DefaultSettingsPath =  System.getProperty("user.dir") + "/settings/";

				config = new Properties();

				config.load(new FileInputStream(DefaultSettingsPath +"encoder_initial_config.properties"));

				System.out.println("Config info loaded: Ok.");

				DebugMessage = new Debuger(Boolean.parseBoolean(config.getProperty("DebugModeEnable")));

				EncoderMainLoopTimingSequence = Integer.parseInt(config.getProperty("EncoderMainLoopTimingSequence"));

				EncodingProgressReportTimingSequence = Integer.parseInt(config.getProperty("EncodingProgressReportTimingSequence"));

				MaxNumberOfJobsSlotPerServer = Integer.parseInt(config.getProperty("MaxNumberOfJobsSlotPerServer"));

				EncoderVaccumCleanerTimingSequence = Integer.parseInt(config.getProperty("EncoderVaccumCleanerTimingSequence"));

				JoblessTimerLoopTimingSequence = Integer.parseInt(config.getProperty("JoblessTimerLoopTimingSequence"));

				/**********************************************************************
				 *********************** DB INITIALS ************************
				 **********************************************************************/

				if (SQL.connect(config.getProperty("PostgraSQLServerAddress")
						+ ":" + config.getProperty("PostgraSQLServerPort")
						+ "/", config.getProperty("PostgraSQLUserName"),
						config.getProperty("PostgraSQLPassWord"),
						config.getProperty("MySqlDataBase"))) {

					System.out.println("Connect to db: Ok.");

				} else {

					DebugMessage.ShowErrors("init()", "Cannot access postgrsql database", "",false);
					System.exit(1);

				}


				/**********************************************************************
				 ***************************** EC2 INITIALS ***************************
				 **********************************************************************/
				getAmazonCloud();

				// Get current machine IP (private IP) & ID from Amazon.
				String [] strIPID = getMyPrivateIPandInstanceID();

				if(strIPID!=null)
				{

					MyInstancePrivateIP = strIPID[1];
					MyInstanceID = strIPID[0];

					DebugMessage.Debug("Machine IP & ID info> ID: " + MyInstanceID + " - IP: " + MyInstancePrivateIP ,"init()",true,1);

					// Registering current machine IP in the encoder_servers table.
					registerMyIP(strIPID[1],strIPID[0]);

					// Get this machine database ID by its instance id in the database.
					int intDB_Order_ID = GetMyDbOrderID(strIPID[0]);

					DebugMessage.Debug("The weight (the order) of this machine in the servers : " + intDB_Order_ID,"init()",true,1);

					if(intDB_Order_ID<0)
						throw new Exception(Constants._ERR_DBAccessError);

					/*
					 * If the below condition equals 1 . There is just one instance or the current instance has the highest rank 
					 * (It means it is first created instance in the group). So we return just its database values. 
					 */
					boolean isMachineSenior = IsMachineSenior(intDB_Order_ID);

					if(isMachineSenior)
						IamSenior=true;
					else
						IamSenior=false;

					File theDir = new File(config
							.getProperty("TempEncodingFolderProductionPath"));

					// if the directory does not exist, create it
					if (!theDir.exists())
					{

						DebugMessage.ShowInfo("creating directory: " + config.getProperty("TempEncodingFolderProductionPath"),true);

						boolean result = theDir.mkdir();

						if(result)
							DebugMessage.ShowInfo("directory created",true);
						else
							throw new Exception(Constants._ERR_CouldnotAccessSystemFile);

					}

					if(IamSenior)
						DebugMessage.ShowInfo("Machine is SENIOR",true);
					else
						DebugMessage.ShowInfo("Machine is NOT SENIOR.",true);

					EncodingThreadsReport.getInstance();

					/*
					 *  Here we fill in the servers list the ready flag for this machine.
					 */

					int queryRes =  SQL.query("UPDATE "+ config.getProperty("TableInstanceServers") +" SET ready_to_work = 1 WHERE instance_id='" + MyInstanceID	+ "';");

					if(queryRes==0 || !SQL.queryWasSuccess())
						throw new Exception(Constants._ERR_DBAccessError);

				}
				else
				{
					throw new Exception(Constants._ERR_CannotAccessResourceIDIP);
				}

			}catch (Exception e){

				DebugMessage.ShowErrors("init()", e.getMessage(),"The program halted in the initial stage",true);

				killThisMachine();

				System.exit(1);

			}


		}
		
		
	/**
	 * Update the progress of encoding jobs through a timer thread and by the help of a singleton class.
	 * 
	 * @throws Exception
	 */
	public static void updateProgress(boolean EmergencyUpdate) throws Exception{

		String strQuery  = EncodingThreadsReport.getInstance().getEncodeJobProgressReport(EmergencyUpdate);

		// nothing for report.
		if (strQuery == null)
			return;

		// updates progress report in DB
		int rowAffect = SQL.query(strQuery);

		if(!SQL.queryWasSuccess() || (rowAffect==0))
			throw new Exception(Constants._ERR_DBAccessError);

	}

	/**
	 * The timer thread of encoding progress report to the DB. 
	 * The time is in second. 
	 * 
	 */
	private static void  EncodingProgressReport() {

		EncodingProgressReportTimer.scheduleAtFixedRate(new Runnable() {
			public void run() {

				try{
					if(!blHaltEverything && !blPauseEverything){

						updateProgress(false);

					}

				}

				catch (Exception e)
				{

					DebugMessage.ShowInfo(e.getMessage(),false);

					if(e.getMessage() == Constants._ERR_DBAccessError)
						ShutdownAllThreads();
				}
			}
		}, 0, EncodingProgressReportTimingSequence, TimeUnit.SECONDS);

	}

	/**
	 * Main Daemon thread for managing ending jobs.
	 *  
	 */
	private static void  MyDotComEncoder() {
		EncodingMainSchedulerTimer.scheduleAtFixedRate(new Runnable() {
			public void run() {

				try{

					int RestHeaderVal = ListenToConductorCommands();

					DebugMessage.Debug("Rest command current value:" + RestHeaderVal,"MyDotComEncoder()",false,0);

					if(RestHeaderVal==-1)
					{
						throw new RuntimeException(Constants._ERR_CouldnotAccessSystemFile);
					}
					//Shutdown machine.
					else if (RestHeaderVal==451) // 451 Unavailable For Legal Reasons (Internet draft)
					{
						ConductorMsgDelegator.getInstance().setConductorMsg(1);
						blPauseEverything =true;
						killThisMachine();
					}

					//SQL.query("update " + config.getProperty("TableUploadSessions") + " set current_process='1',instance_id='"+ MyInstanceID +"' where id = IN (select id from " + config.getProperty("TableUploadSessions") + " where current_process='0' order by id limit " + strMaxNumberOfJobsSlotPerServer +")");
					// Pause everything up to get new command for continue.
					else if (RestHeaderVal==100) 
					{
						ConductorMsgDelegator.getInstance().setConductorMsg(1);
						updateProgress(true);
						blPauseEverything =true;

					}	
					// Continue everything.
					else if (RestHeaderVal==101)
					{
						restConductorCommandFile();
						ConductorMsgDelegator.getInstance().setConductorMsg(0);
						blPauseEverything =false;
					}

					//Stop Everything or Shutdown machine.
					if(blHaltEverything)
					{
						ConductorMsgDelegator.getInstance().setConductorMsg(2);
					}


					DebugMessage.Debug("Halt & Pause states: " + blHaltEverything + "," + blPauseEverything, "MyDotComEncoder()",false, 0);

					if(!blHaltEverything && !blPauseEverything){

						int amIauthorizedToTakeJob = 0;

						// Get the number of total job in this machine from os (running encoding threads).
						int numOfRunnedFFmpeg = processinfo.GetProgramRunningInstances("ffmpeg");

						// We have enough job (full slot) so we do not take new job
						if(MaxNumberOfJobsSlotPerServer == numOfRunnedFFmpeg)
						{
							return;
						}				

						/*%%%%%%*/
						/**%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
						 * Make below line false if you want to make one not senior encoder machine. 
						 * Except mark it as comment. 
						 *%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
						 */
						//IamSenior=false;


						if(!IamSenior)
						{

							amIauthorizedToTakeJob = getMyMachinScheduleRoundRobin(MyInstanceID);


							/* *
							 * if :  1 OK the other machines are full so I can take some jobs.
							 * if :  0 the process in another machines are not up to maximum capacity so wait up to they become full. 
							 * if : -1 function query was not successful and there is no server (vague).
							 * if : -2 DB access error.
							 * */
							if(amIauthorizedToTakeJob ==0 ){

								//updateMyCurrentUnderProcessJobinDB(numOfRunnedFFmpeg);

								return;
							}
							else if(amIauthorizedToTakeJob==1){
								// Take some jobs. The bellow code is just to keep the code flow nice and readable. 	
								amIauthorizedToTakeJob=1;        		
							}
							else if(amIauthorizedToTakeJob==-1){
								// can not access servers info they return zero 
								throw new Exception(Constants._ERR_GeneralError);
							}
							// We had database error
							else if(amIauthorizedToTakeJob==-2){
								throw new Exception(Constants._ERR_DBAccessError);	        		
							}



						}
						else{
							// I am senior machine
							amIauthorizedToTakeJob=1;
						}

						if(amIauthorizedToTakeJob==1)
						{

							int numOfJobsToTake = MaxNumberOfJobsSlotPerServer - numOfRunnedFFmpeg;

							// There is no empty process slot in this machine (the encoding capacity is full). 
							if(numOfJobsToTake==0)
							{
								// Reset sleep timer to zero. Because we took some encoding jobs and we do not need to shutdown this machine.  
								SleepTimerCounter=0;
								return;
							}

							/*
							 * The below codes try to fetch the jobs equal to the value of numOfJobsToTake variable 
							 * but it is possible in some cases there is no more job in the db queue (we request more than what exist) 
							 * so it returns everything is possible.   
							 */
							ArrayList<String[]> list;
							String temporaryEncodingJobsImprint = imprintMyEncodingJobs(String.valueOf(numOfJobsToTake));

							// Check if: Is there any job to take or not? If array is zero in length it means we do not have job or there was some type of error!
							if (temporaryEncodingJobsImprint == "0")
								return;
							else if(temporaryEncodingJobsImprint== "-1")
								throw new Exception(Constants._ERR_DBAccessError);
							else {

								list = fetchForMeEncodingJobs(temporaryEncodingJobsImprint, String.valueOf(numOfJobsToTake));
							}

							// Reset sleep timer to zero. Because we took some encoding jobs and we do not need to shutdown this machine.  
							SleepTimerCounter=0;

							ExcuteEncodeJobs(list);

						}

					}
				}
				catch (Exception e)
				{
					DebugMessage.ShowErrors("MyDotComEncoder() thread", e.getMessage(),"",false);

					switch (e.getMessage()) {

					case Constants._ERR_CouldnotAccessSystemFile:
						ShutdownAllThreads();
						break;
					case Constants._ERR_GeneralError:
						ShutdownAllThreads();
						break;
					case Constants._ERR_DBAccessError:
						ShutdownAllThreads();
						break;

					default:
						DebugMessage.ShowErrors("MyDotComEncoder() thread", e.getMessage(),"Unkown Error",false);
						break;
					}
				}


			}
		}, 0, EncoderMainLoopTimingSequence, TimeUnit.SECONDS);

	}


	/**
	 * Garbage cleaner thread:<br/>
	 * Cleans garbage files in the temporary conversion directory based on a schedule.
	 * 
	 */
	private static void VacuumCleaners() {
		EncoderVaccumCleanerSchedulerTimer.scheduleAtFixedRate(new Runnable() {
			public void run() {

				try {
					if (!blHaltEverything) {
						purgeGarbageTempConversionDirectory(
								config.getProperty("TempEncodingFolderProductionPath")
								+ "/", "", Integer.parseInt(config
										.getProperty("TimeDifferenceToPurge")));
					}
				} catch (Exception e) {

					DebugMessage.ShowErrors("VacuumCleaners() method", e.getMessage(),"Unable to shutdown in VacuumCleaners by the reason.",false);

				}

			}
		}, 0, EncoderVaccumCleanerTimingSequence, TimeUnit.HOURS);

	}

	/**
	 * Delete old temporary files in the tmp conversion directory. The Encoder program 
	 * delete automatically files after convention but this function clean those files
	 * which may be remained by some failed threads.
	 * 
	 * @param directoryToPurge: The temporary conversion directory to purge.
	 * @param extension: The extension to clean. Example <b> ".txt" </b> to delete files with TXT extension.<br/> 
	 * 					 To list all files with different extensions just to double quote <b>e.g ""</b>.
	 * @param timeToPurge: The time in the past.That is a negative number and it is compare to current time<br/> 
	 * 						for example -3 means the files 3 hours before current time will be deleted 
	 */
	private static void purgeGarbageTempConversionDirectory(
			String directoryToPurge, String extension, int timeToPurge) {
		Calendar calendar = Calendar.getInstance();
		Date nowTime = new Date();

		calendar.setTime(nowTime);
		calendar.add(Calendar.HOUR, timeToPurge);

		List<String> garbageFiles = new ArrayList<String>();
		File dir = new File(directoryToPurge);

		for (File file : dir.listFiles()) {
			if (file.getName().endsWith((extension))) {

				if (calendar.getTimeInMillis() >= file.lastModified())
					garbageFiles.add(file.getName());

			}
		}

		// Now Delete Files
		for (int i = 0; i < garbageFiles.size(); i++) {
			File file = new File(directoryToPurge + garbageFiles.get(i));
			file.delete();
		}

	}

	/**
	 * Shutdown all of threads in the program.
	 *  
	 */
	private static void ShutdownAllThreads(){
		ConductorMsgDelegator.getInstance().setConductorMsg(1);
		blHaltEverything=true;

		// Remove this machine from servers list.
		SQL.query("delete FROM " + config.getProperty("TableInstanceServers") + " WHERE instance_id = '" + MyInstanceID + "'");

		// Remove this machine from EC2
		killThisMachine();
		
		SleepTimer.shutdownNow();
		
		killThisMachine();
		
		EncodingMainSchedulerTimer.shutdownNow();	
	}


	/**
	 *  The thread seeder function. It runs enough threads for the number of jobs has taken by the MyDotComEncoder() thread.
	 *  
	 * @param EncodeJoblist
	 */
	private static void ExcuteEncodeJobs(ArrayList<String[]> EncodeJoblist){
		try{
			int listSize = EncodeJoblist.size();

			DebugMessage.Debug("Number of taken jobs: " + listSize, "ExcuteEncodeJobs()",false, 0);

			ExecutorService executor[] = new ExecutorService[listSize];

			//Future<String> future = executor.submit(new EncoderThread(arrEncodeJob));

			for(int i=0; i < listSize; i++) {
				executor[i]=  Executors.newFixedThreadPool(1);
				Future<String> future = executor[i].submit(new EncoderThread(EncodeJoblist.get(i)));
			}

		}catch(Exception e){

			DebugMessage.ShowErrors("ExcuteEncodeJobs() method", e.getMessage(),"",false);
		}
	}

	/**
	 * Amazon cloud credential submitter method (The real shit in the world of programming when you don't have anything to write for a function!). 
	 * 
	 * @throws Exception
	 */
	private static void getAmazonCloud() throws Exception {

		credentials = new PropertiesCredentials(Encoder.class.getResourceAsStream("settings/"+ "AwsCredentials.properties"));

		ec2 = new AmazonEC2Client(credentials);
		s3 = new AmazonS3Client(credentials);

	}

	/**
	 * Check whether this machine (Instance) is Senior machine by
	 * the simple check of ID order of DB. 
	 * Senior Machine means the machine which has priority to 
	 * take job before every machine It never shutdowns because
	 * at least one machine should be all the time run and take
	 * the jobs (standby). 
	 * 
	 * @param dbOrderID
	 * @return
	 * @throws Exception
	 */
	private static boolean IsMachineSenior(int dbOrderID) throws Exception{

		ResultSet result = SQL.select("select id as id from " + config.getProperty("TableInstanceServers"));

		//System.out.println("select min(id) as id from " + config.getProperty("TableInstanceServers") + " where "+ dbOrderID +" = (select min(id) from " + config.getProperty("TableInstanceServers") + ");");

		if(!SQL.queryWasSuccess())
			throw new Exception(Constants._ERR_DBAccessError);

		boolean IsSeniorOrNot=true;

		while (result.next()) {

			//check is there less order machine. If there is this machine is NOT senior
			if(result.getInt("id")<dbOrderID)
			{
				result.last();
				return false;
			}	
		}


		return IsSeniorOrNot;

	}


	/**
	 * Terminates this machine completely in EC2 and there is no return (program will be terminated).
	 * 
	 */

	public static void killThisMachine() {

		try {

			DebugMessage.ShowInfo("Killed!",true);

			// Config setting for allowing machine to be shutdown. If false we can't shutdown (it is good just for debugging).
			if(Boolean.parseBoolean(config.getProperty("AllowToKillItSelf")))
			{
				/*https://ec2.amazonaws.com/
	        		?Action=ModifyInstanceAttribute
	        		&InstanceId=i-87ad5eec
	        		&DisableApiTermination.Value=false
	        		&AUTHPARAMS*/
				//wget -q -O - http://169.254.169.254/lateta/instance-id

				String instanceId = "";

				String wget = "sudo wget -q -O - " + config.getProperty("MetaInfoServerIP") + "/instance-id";

				Process p = Runtime.getRuntime().exec(wget);
				p.waitFor();

				BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));

				instanceId = br.readLine();

				if(instanceId.equals("")) 
					throw new Exception(Constants._ERR_ServiceIsNotAvailable);

				List<String> instancesToTerminate = new ArrayList<String>();
				instancesToTerminate.add(instanceId);
				TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest();
				terminateRequest.setInstanceIds(instancesToTerminate);
				ec2.terminateInstances(terminateRequest);
			}
		} catch(Exception e) {
			DebugMessage.ShowErrors("killThisMachine()", e.getMessage(),"",true);
			System.exit(1);
		}
	}

	/**
	 * Register this machine private IP in the DB. 
	 * 
	 * @param strInstanceIP
	 * @param srtInstanceID
	 * @return
	 * @throws Exception
	 */
	private static boolean registerMyIP(String strInstanceIP, String srtInstanceID) throws Exception
	{
		try
		{
			int rowAffect  = SQL.query("UPDATE "+ config.getProperty("TableInstanceServers") +" SET private_ip = '" + strInstanceIP + "' WHERE instance_id='" + srtInstanceID	+ "';");

			// try to register this machine if not exist in the table of servers by inserting.(if above query for updating did not work insert it) register it 
			if (rowAffect==0)
				rowAffect  = SQL.query("INSERT INTO "+ config.getProperty("TableInstanceServers") +	"(private_ip,instance_id) VALUES('" + strInstanceIP + "','" + srtInstanceID + "');");

			DebugMessage.Debug("Register of this machine: " + rowAffect + " (more than 0 means success).","registerMyIP()",false,0);

			if(!SQL.queryWasSuccess() || (rowAffect==0))
				throw new Exception(Constants._ERR_DBAccessError);

			return true;
		}
		catch (Exception e){

			DebugMessage.ShowErrors("registerMyIP()", e.getMessage(), "",false);
			return false;
		}


	}

/**
 * Get this machine private IP address from EC2 meta URL.
 * 
 * @return
 */
	private static String [] getMyPrivateIPandInstanceID() {
		String IPID []= {null,null};
		try {

			String strNamesToPost []= {""};
			String strValsToPost []= {""};

			restcommands.restCommands(config.getProperty("MetaInfoServerIP") + "instance-id",strNamesToPost , strValsToPost, false);
			IPID[0] = restcommands.ServerResult();

			if(restcommands.ServerHeader()!=200)
				throw new Exception(Constants._ERR_CannotAccessResourceIDIP);


			restcommands.restCommands(config.getProperty("MetaInfoServerIP") + "local-ipv4",strNamesToPost , strValsToPost, false);
			IPID[1] = restcommands.ServerResult();

			if(restcommands.ServerHeader()!=200)
				throw new Exception(Constants._ERR_CannotAccessResourceIDIP);

			//System.out.println("ID" + restcommands.ServerResult()+ "-- " + restcommands.ServerHeader());

			return IPID;
		} catch (Exception e) {

			DebugMessage.ShowErrors("getMyPrivateIPandInstanceID()", e.getMessage(), "",true);
			return null;
		}

	}

	/**
	 * Convert SQL query results to String array.
	 * 
	 * @param result
	 * @return
	 */
	private static String [] convertSQLResultSetToArray(ResultSet result){

		try {

			ResultSetMetaData rsmd = (ResultSetMetaData) result
					.getMetaData();

			int noColumns = rsmd.getColumnCount();

			//SQL.showSelect(result);
			String [] record = new String[noColumns];
			result.next(); 
			for (int i = 0; i < noColumns; i++)
			{
				record[i] = result.getString(i+1);
			}	


			return record;

		} catch (Exception e) {

			return null;

		}

	}

	/**
	 * Get this machine database ID by its EC2 instance id in the database.
	 * 
	 * @param strInstanceID (id of instance which we want to find its fellow)
	 * @return integer (if > 0 = id of instance, if == 0 = this instance is first created instance, if < 0  = db error)
	 */
	private static int GetMyDbOrderID(String strInstanceID){
		try{

			ResultSet result = SQL.select("select * from "+ config.getProperty("TableInstanceServers") +" where instance_id = '" + strInstanceID + "';");

			if(!SQL.queryWasSuccess() || SQL.mysql_num_rows()==0)
				throw new Exception(Constants._ERR_DBAccessError);


			result.next();

			// Initialize the global variable of current system database ID 
			return result.getInt("id");


		}catch (Exception e){

			return -1;	
		}
	}

	/**
	 * This as bar graph algorithm. The formula is as follow: 
	 * 
	 *          	Jobs are waiting + Jobs are in progress
	 * ------------------------------------------------------------------- => if Y > 0 = we can take job.   
	 *        Server order number (Weight) + Encoding slots per server
	 * 
	 * @param MyInstanceID
	 * @return int (if :  1 OK the other machines are full so I can take some jobs. if : -2 function query was not successful and there is no server -vague-. if :  0 the process is not full so wait up to be full).    
	 */
	private static int getMyMachinScheduleRoundRobin(String MyInstanceID){

		try{

			ResultSet resStatistics = SQL.select("select count(*) FROM " + config.getProperty("TableInstanceServers") + 
					" WHERE id <= (SELECT id FROM " + config.getProperty("TableInstanceServers") + 
					" WHERE instance_id = '" + MyInstanceID + "')"  + " UNION ALL SELECT COUNT(id) FROM " + 
					config.getProperty("TableUploadSessions") + 
					" WHERE current_process IN (" + 
					Constants._CUR_ENC_PROC_WAIT_FOR_PICKUP + "," + 
					Constants._CUR_ENC_PROC_ENCODE_PROGRESSING + "," + 
					Constants._CUR_ENC_PROC_PICKED_WAIT_TOSTART_ENCODE + ");");

			if(!SQL.queryWasSuccess())
				throw new Exception(Constants._ERR_DBAccessError);

			//SQL.showSelect(resStatistics);
			resStatistics.next();
			int intMyOrderInServers = resStatistics.getInt("count");

			DebugMessage.Debug("Order in the servers db "+ config.getProperty("TableInstanceServers") +" list: " + intMyOrderInServers,"getMyMachinScheduleRoundRobin()",false,0);


			resStatistics.next();
			int  intNumJobProcess = resStatistics.getInt("count");

			DebugMessage.Debug("Number of jobs under processing or wating for process in the " + config.getProperty("TableUploadSessions") + ": " + intNumJobProcess,"getMyMachinScheduleRoundRobin()",true,0);

			// There is no job in the server so wait 
			if(intNumJobProcess==0)
			{

				DebugMessage.Debug("There is no job to do!","getMyMachinScheduleRoundRobin()",false,0);

				return 0;
			}	

			/**
			 * *************************************************************************************
			 * **************************HERE IS VERY IMPORTANT*************************************
			 * *************************************************************************************
			 * This if condition has two important responsibility:
			 * 		A- Checks if the senior machine is died change this machine role to Senior machine.
			 * 		B- If this machine at the moment is senior just return 1 value and tell to main thread 
			 * 		   "Because I am senior machine I can take job all the time and I do not need to wait for senior machines".  
			 */

			// This is senior server so it should take job
			if(intMyOrderInServers==1){

				IamSenior=true;

				DebugMessage.Debug("There is senior server!","getMyMachinScheduleRoundRobin()",false,0);

				return 1;
			}

			float intEncodingBarGraph =  (float)intNumJobProcess / (float) (intMyOrderInServers * MaxNumberOfJobsSlotPerServer);

			DebugMessage.Debug("intEncodingBarGraph = " + intEncodingBarGraph  + " | " +  "intNumJobProcess = " + intNumJobProcess + " | " + 
					"intMyOrderInServers = " + intMyOrderInServers + " | " + "MaxNumberOfJobsSlotPerServer = " + MaxNumberOfJobsSlotPerServer,
					"getMyMachinScheduleRoundRobin()",true,0);

			/* *
			 * if :  1 OK the other machines are full so I can take some jobs. [We can take jobs. There is no empy capacity in the other machines]
			 * if :  0 the process is not full so wait up to be full. [We have empty capacity in the other machines]
			 * */

			if(intEncodingBarGraph <= 0.5)
			{
				DebugMessage.Debug("We have empty capacity in the other machines. So wait!","getMyMachinScheduleRoundRobin()",false,0);
				return 0;
			}
			else if(intEncodingBarGraph > 0.5)
			{

				DebugMessage.Debug("We can take jobs. There is no empy capacity in the other machines.","getMyMachinScheduleRoundRobin()",false,0);
				return 1;
			}

			return 0;
		}
		catch (Exception e) {

			DebugMessage.ShowErrors("getMyMachinScheduleRoundRobin()", e.getMessage(), "",true);

			return -2;
		}

	}

	/**
	 * Generates String token for imprinting encoding jobs by threads before conversion process. 
	 * It means each machine before taking job put one sign for the selected jobs to tell to the
	 * other machines "Hey I have chosen this before!" so we do not have any conflict.  	
	 * 
	 * @return String
	 */
	private static  String generateToken(int len) {

		String ALPHA_NUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

		StringBuffer sb = new StringBuffer(len);
		for (int i = 0; i < len; i++) {
			int ndx = (int) (Math.random() * ALPHA_NUM.length());
			sb.append(ALPHA_NUM.charAt(ndx));
		}

		return sb.toString();
	}

	/**
	 * 
	 * 
	 * @param queryTemporaryImprint
	 * @param strNumAuthorizEncdJobInThisQue
	 * @return
	 */
	private static ArrayList <String[]> fetchForMeEncodingJobs(String queryTemporaryImprint, String strNumAuthorizEncdJobInThisQue ){
		try {

			ResultSet result = SQL.select("select * from " + config.getProperty("TableUploadSessions") + " where query_temp_imprint='" +
					queryTemporaryImprint + "' order by id limit " + strNumAuthorizEncdJobInThisQue +";");

			ResultSetMetaData rsmd = (ResultSetMetaData) result
					.getMetaData();

			ArrayList<String[]> list = new ArrayList<String[]>();

			while(result.next()){

				String[] record = new String[rsmd.getColumnCount()];

				for(int i=1; i< rsmd.getColumnCount();i++)
				{
					record[i-1] = result.getString(i);
				}

				list.add(record);
			}
			//String[][] arrLocations = locations.toArray(new String[locations.size()][0]);
			/*String[] strings = list.get(1);
			    for (int j = 0; j < strings.length; j++) {
			        System.out.print(strings[j] + " ");
			    }
			    System.out.println();*/
			return list	;		


		} catch (Exception e) {

			DebugMessage.ShowErrors("fetchForMeEncodingJobs()", e.getMessage(), "",true);

			return null;
		}

	}


	/**
	 * Lock and fetch possible number of encoding jobs for this machine.
	 * It means each machine before taking job put one sign for the selected jobs to tell to the
	 * other machines "Hey I have chosen this before!" so we do not have any conflict.  		
	 * 
	 * @param strNumAuthorizEncdJobInThisQue 
	 * @return (Array of string containing the database ids of selected records to transcode) 
	 */
	private static String imprintMyEncodingJobs(String strNumAuthorizEncdJobInThisQue){

		try {

			/*
			 * It is possible sometimes the past fetched jobs invokes late by the operating system 
			 * therefore we fetch jobs and select them again by over selection here but we didn't still changed the old jobs to under encoding flag. 
			 * Based on this scenario here we create a temporary key for every group of jobs for avoiding to conflict with old jobs which has chosen by this machine a few seconds ago.
			 */
			String queryTemporaryImprint = generateToken(6);

			/**
			 * Flag for current process stage and success and fails for internal usage
			 * of servers. The values are as follow: 
			 * if = -1 -> Error had found in the encoding file.
			 * if = 0  -> The process is in the queue list recently and to pick up by one of servers for process. 
			 * if = 1  -> Queuing and the file picked up and waiting for encoding to be start.
			 * if = 2  -> Encode is progressing.
			 * if = 3  -> Encoding finished successfully.
			 **/


			/*
			 *  Choosing free jobs for this server to be encoded. This query is ATOM so upon we select we update it to 
			 *  say to the other servers these are reserved for this Encode server. 
			 */
			String strQuery="update " + config.getProperty("TableUploadSessions") + " set current_process='1',instance_id='"+ MyInstanceID +"',query_temp_imprint='"+ 
					queryTemporaryImprint +"' where id IN (select id from " + config.getProperty("TableUploadSessions") + " where current_process='0' order by id limit " + strNumAuthorizEncdJobInThisQue +")";

			int intNumAffectedRows = SQL.query(strQuery);

			if(intNumAffectedRows==-1)
				throw new Exception(Constants._ERR_DBAccessError);



			if(intNumAffectedRows>0)
				return queryTemporaryImprint;
			else	
				// No job to take or someone took it sooner.
				return "0";



		} catch (Exception e) {

			return "-1";

		}

	}


	/**
	 * Time to kill this machine when there is not any job to do after specific time.
	 * -> Time to kill = JoblessTimerLoopTimingSequence * SleepTimerCounter <-
	 * 
	 */
	private static void SleepTimerManagerThread(){

		// 	check if in the config auto shutdown is accepted.
		if(Boolean.parseBoolean(config.getProperty("SleepAccepted"))){

			SleepTimer.scheduleAtFixedRate(new Runnable() {
				public void run() {

					try{
						/*
						 * Checks if this machine is not a senior machine apply the idle timer. 
						 * We do this because we want to be sure at least all the time we have one encoder machine (which is senior encoder)  
						 */
						if(!blHaltEverything && !IamSenior){

							SleepTimerCounter++;

							//System.out.println("Time idle passed =" + (SleepTimerCounter * Integer.parseInt(config.getProperty("JoblessTimerLoopTimingSequence"))) + " minutes");

							if((SleepTimerCounter * Integer.parseInt(config.getProperty("JoblessTimerLoopTimingSequence")))	>= Integer.parseInt(config.getProperty("JoblessTimeToShutdownInMunuts")))
							{

								/*
								 * In any case there should be some machines to
								 * support peaks. If the mentioned case is happened we must stop 
								 * machines from removing themselves by sleep timer process. Here
								 * the below code up to blHaltEverything variable is responsible for
								 * this checking this state.
								 */
								ResultSet result = SQL.select("select * from  " + config.getProperty("TableInstanceServers"));

								result.last();

								if( result.getRow() == Integer.parseInt(config.getProperty("MinimumEssentialTiers")) )
								{
									SleepTimerCounter=0;
									return;
								}



								blHaltEverything=true;

								ConductorMsgDelegator.getInstance().setConductorMsg(1);

								// Remove this machine from servers list.
								SQL.query("delete FROM " + config.getProperty("TableInstanceServers") + " WHERE instance_id = '" + MyInstanceID + "'");
								Thread.sleep(1000);

								// Remove this machine from EC2
								killThisMachine();
								SleepTimer.shutdownNow();
							}

						}

					}catch (Exception e){

						DebugMessage.ShowErrors("SleepTimerManagerThread()", e.getMessage(), "",true);

					}


				}
			}, 0, JoblessTimerLoopTimingSequence, TimeUnit.MINUTES);
		}
	}

//	/**
//	 * 
//	 * @param numOfRunnedFFmpeg
//	 */
//	private static void updateMyCurrentUnderProcessJobinDB(int numOfRunnedFFmpeg)
//	{
//		try{
//			if((oldNumOfRunnedFFmpeg != numOfRunnedFFmpeg) && Boolean.parseBoolean(config.getProperty("UpdateRealtimeNumberOfUnderProcessJobs")))
//			{
//				// Update the number of current job on this machine in the databas.
//				int numAffectResult = SQL.query("update "+ config.getProperty("TableInstanceServers") +" set num_cur_run_jobs = '" + String.valueOf(numOfRunnedFFmpeg) + "' WHERE instance_id='" + MyInstanceID + "';");
//
//				if(numAffectResult==0)
//					throw new Exception(Constants._ERR_DBAccessError);
//			}
//
//			oldNumOfRunnedFFmpeg = numOfRunnedFFmpeg;
//		}
//		catch(Exception e){
//
//			DebugMessage.ShowErrors("updateMyCurrentUnderProcessJobinDB()", e.getMessage(), "",true);
//
//		}
//
//	}

	/**
	 * Reports and handles to Conductor
	 * @param strIDinDB
	 */
	public static void UnfinishUnsuccessJobReport(String strItemToken,boolean blCriticalError){
		try{

			// Checks if error reporting is enable in the config settings. 
			if(Boolean.parseBoolean(config.getProperty("AllowUnsuccessJobReport")))
			{
				// If error is database or file IO access. We will shutdown system. 
				if(blCriticalError)
				{

					// We had an error so we do diagnostic check for file IO access and Database.
					if (!SQL.connect(config.getProperty("PostgraSQLServerAddress")
							+ ":" + config.getProperty("PostgraSQLServerPort")
							+ "/", config.getProperty("PostgraSQLUserName"),
							config.getProperty("PostgraSQLPassWord"),
							config.getProperty("MySqlDataBase"))) {

						ShutdownAllThreads();
						return;
					}


					String strNamesToPost []= {"encoders_error_report","instance_id","instance_ip"};

					String strValsToPost []= {"TRUE",MyInstanceID,MyInstancePrivateIP};

					restcommands.restCommands(config.getProperty("ReportServerURL"),strNamesToPost , strValsToPost, false);

					//The Conductor server is not alive so terminate everything.
					if(restcommands.ServerHeader()!=200)
					{
						blHaltEverything=true;
						EncodingMainSchedulerTimer.shutdownNow();
						return;
					}

				}
				else
				{
					String strNamesToPost []= {"encoders_error_report","instance_id","item_token"};

					String strValsToPost []= {"TRUE",MyInstanceID,strItemToken};

					restcommands.restCommands(config.getProperty("ReportServerURL"),strNamesToPost , strValsToPost, false);

					//The Conductor server is not alive so terminate everything.
					if(restcommands.ServerHeader()!=200)
					{
						blHaltEverything=true;
						EncodingMainSchedulerTimer.shutdownNow();
						return;
					}
				}
			}
		}
		catch(Exception e){

			DebugMessage.ShowErrors("UnfinishUnsuccessJobReport()", e.getMessage(), "",true);

			return;
		}
	}

	/** 
	 * Finish job success report. It consolidate a number of items and after that send them as report to avoid bandwidth consumption.
	 * 
	 */
	public void FinishSuccessJobReport(String meta_info){


		try{

			if(!blHaltEverything && Boolean.parseBoolean(config.getProperty("AllowSuccessJobReport"))){

				EncodStaticNumbOfItemSendAsBunch++;
				EncodStaticMetaInfoSendAsBunch = EncodStaticMetaInfoSendAsBunch + meta_info;

				if(EncodStaticNumbOfItemSendAsBunch > Integer.parseInt(config.getProperty("EncoderStatisticsNumberOfItemsToSendAsBunch")))
				{


					String strNamesToPost []= {"encode_item_report","instance_id","encoded_item","meta_info"};

					/* 
					 * NOTE: We do EncodStaticMetaInfoSendAsBunch.substring(1) in the below 
					 * code because the format of meta info is ",#,xxx,xxx,xxx" 
					 * therefore we remove first comma ",#," to send this "#,xxx,xxx,xxx". 
					 */
					String strValsToPost []= {"TRUE",MyInstanceID,String.valueOf(EncodStaticNumbOfItemSendAsBunch),EncodStaticMetaInfoSendAsBunch.substring(1)};

					restcommands.restCommands(config.getProperty("ReportServerURL"),strNamesToPost , strValsToPost, false);

					//The Conductor server is not alive so terminate everything.
					if(restcommands.ServerHeader()!=200)
					{
						blHaltEverything=true;
						EncodingMainSchedulerTimer.shutdownNow();
						return;
					}

					EncodStaticNumbOfItemSendAsBunch=0;
					EncodStaticMetaInfoSendAsBunch="";
				}


			}
		}

		catch (Exception e)
		{
			DebugMessage.ShowErrors("FinishSuccessJobReport()", e.getMessage(), "",true);
		}
	}

	/**
	 *  IMPORTANT: the log file in the node.js and here should be same in the ramdisk.
	 *  Because node.js works as server and get the commands from conductor server and
	 *  share them as command in a file in the ramdisk.
	 *  
	 *  The file I chose is "restcommand.txt" in this directory:
	 *  System.getProperty("user.home") + "/ramdisk/"
	 *  (in ubuntu it is "/home/ubuntu/ramdisk/")
	 *  
	 *  
	 * @return
	 */
	private static int ListenToConductorCommands(){

		try{


			/*
			 * IMPORTANT: the log file in the node.js and here should be same in the ramdisk. 
			 * Because node.js works as server and get the commands from conductor server and 
			 * share them as command in a file in the ramdisk.
			 * 
			 *   The file I chose is "restcommand.txt" in this directory:
			 *    System.getProperty("user.home") + "/ramdisk/" 
			 *    (in ubuntu it is "/home/ubuntu/ramdisk/")
			 *   
			 * 
			 */
			//System.out.println(System.getProperty("user.home") + "/ramdisk/" + "restcommand.log");
			File file = new File(System.getProperty("user.home") + "/ramdisk/" + "restcommand.log");

			// File is now busy by the node.js
			if(!file.canRead())
				return 0;

			if (file.exists())
			{

				FileInputStream fstream = new FileInputStream(file.getAbsolutePath());
				DataInputStream in = new DataInputStream(fstream);
				BufferedReader br = new BufferedReader(new InputStreamReader(in));

				String strLine;

				String ReadHttpHeader="";

				while ((strLine = br.readLine()) != null)   {

					DebugMessage.Debug("The Rest file Contents: " + strLine,"ListenToConductorCommands()",true,0);
					ReadHttpHeader = strLine;
				}
				in.close();

				if(ReadHttpHeader.length() > 0){
					return Integer.parseInt(ReadHttpHeader);
				}

				else
					return 0;
			}
			else{
				return 0;	
			}
		}
		catch(Exception e){

			DebugMessage.ShowErrors("ListenToConductorCommands()", e.getMessage(), "",true);
			return -1;
		}

	}

/**
 * Resets conductor command file to be ready for new commands.
 * 
 * @throws Exception
 */
	private static void restConductorCommandFile() throws Exception{

		try {

			String content = "";

			File file = new File(System.getProperty("user.home") + "/ramdisk/" + "restcommand.log");

			if(!file.canWrite())
			{
				// if file doesn't exists, then create it
				if (!file.exists()) {
					file.createNewFile();

				}
				// if file exist but it is busy by node.js
				else
				{
					// Try n times to write to file if not throw exception
					if(TryTimeToResetConductorFile >= Integer.parseInt(config.getProperty("TryTimeToResetConductorCommandFile"))){

						throw new Exception(Constants._ERR_CouldnotAccessSystemFile);

					}

					TryTimeToResetConductorFile++;
					Thread.sleep(100);
					restConductorCommandFile();
				}
			}



			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(content);
			bw.close();

			DebugMessage.ShowInfo("Debug: Srver Command file reset done.",true);


		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

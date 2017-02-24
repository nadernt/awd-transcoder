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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import com.SQL.SQL;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.s3.AmazonS3;
import com.authentication.Authentication;
import com.debuger.Debuger;
import com.restcommands.RestCommands;

import java.sql.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.net.httpserver.HttpServer;

public class Conductor {

	private static AmazonEC2 ec2;
	private static AmazonS3 s3;
	
	protected static Properties config;
	protected static String DefaultSettingsPath;
	
	private static boolean blPauseConductorEngineTemporary = false;

	//protected static boolean debugModeEnable;
	private static Scanner scan = new Scanner(System.in);
	private static RestCommands restcommands;
	private static String UriRestServer;
	private static HttpServer server;
	private static ScheduledExecutorService FleecastMainScheduler = Executors.newSingleThreadScheduledExecutor();
	private static ScheduledExecutorService HealthCheck = Executors.newSingleThreadScheduledExecutor();
	public static Debuger DebugMessage;

	// Program constants
	interface Constants {
		public static final String _ERR_SqlQueryWasntSuccess = "SQL Query Was Not Success";
		public static final String _ERR_CannotAccessZones = "Cannot access zones";
		public static final String _ERR_CannotRunInstance = "Cannot run instance";
		public static final String _ERR_GeneralError = "General Error";
		public static final String _ERR_ErrorLogServersInDB = "Error log servers in DB";
		public static final String _ERR_ErrorAddEC2Instance = "Error add EC2 instance";
		public static final String _ERR_ErrorAddEC2InstanceInConductor = "Error add EC2 instances in Conductor";
		public static final String _ERR_FatalError = "Fatal Erro";
		public static final String _ERR_DBAccessError = "DB access error";
		public static final String _ERR_NetworkAccessError = "Network access error";
		public static final String _ERR_JerseyServerAccessError = "Sever Internal Error";

		public static final String _System_DATE = new SimpleDateFormat(
				"dd/MM/yyyy").format(new Date());
		public static final String _System_DATE_AND_HOURS = new SimpleDateFormat(
				"dd/MM/yyyy HH:mm:ss").format(new Date());
		// public static final int MY_BDATE = avail();
		public static final boolean SillyPlatform = true;
	}

	
	/**
	 * Conductor class Main method. 
	 * 
	 * @param args
	 * @throws Exception
	 */
	
	public static void main(String[] args) throws Exception {
		
		try {
			
			System.out.println("===========================================");
	        System.out.println("Conductor v1.0");
	        System.out.println ("OS Info: " + System.getProperty("os.name") + System.getProperty("os.version") + System.getProperty("os.arch"));
	        System.out.println("===========================================");

			
			init();
			
			System.out.println();
			DebugMessage.ShowInfo("Run Fleecast conductor Daemon", true);
			FleecastConductor();

			
			System.out.println();
			DebugMessage.ShowInfo("Run health check service thread", true);
			HealthCheck();
			
			}
		    catch (Exception e){
		    	System.out.println("Exception in main function: " + e.getMessage());
		    	System.exit(1);
		    }
			
		}
		  
		
	/**
	 * Initialization of program "connect to DB, load configurations, connect to Amazon, run minimum encode servers we need etc".
	 */
	private static void init() {

  
		/**********************************************************************
		 ***************************** EC2 INITIALS ***************************
		 **********************************************************************/
		

		try {
    		
		//	System.out.println("Working Directory = " + System.getProperty("user.dir"));
			
    		/*********************************************************************
			 * *******************************************************************
			 * Loading Program config information.
			 * *******************************************************************
			 *********************************************************************/

			DefaultSettingsPath =  System.getProperty("user.dir") + "/settings/";
			
			config = new Properties();
			
			config.load(new FileInputStream(DefaultSettingsPath + "conductor_initial_config.properties"));
			
			System.out.println("Program config info loaded: Ok.");
			
			//debugModeEnable = Boolean.parseBoolean(config.getProperty("DebugModeEnable"));			

			DebugMessage = new Debuger(Boolean.parseBoolean(config.getProperty("DebugModeEnable")));

			/**********************************************************************
			 *********************** JERSEY SERVER INITIALS **********************************
			 **********************************************************************/
			Authentication.getInstance().setAuthenticationInitials(config.getProperty("AdminUserName"),config.getProperty("AdminPassword"), config.getProperty("AdminSessionValidTimeInMinut"));
			
			UriRestServer = config.getProperty("UriRestServer");
			
			System.out.println(UriRestServer);
		    
			server = HttpServerFactory.create(UriRestServer);
            
		    server.start();
            
            DebugMessage.ShowInfo("Http Server started: Ok.",true);
    		
            boolean blNeedSoftBoot=checkForSoftBoot();
           
            /*##################################*/ blNeedSoftBoot =true;
            
            
			/**********************************************************************
			 *********************** DB INITIALS *********************************
			 **********************************************************************/

            
			if (!SQL.connect(config.getProperty("PostgraSQLServerAddress")
					+ ":" + config.getProperty("PostgraSQLServerPort")
					+ "/", config.getProperty("PostgraSQLUserName"),
					config.getProperty("PostgraSQLPassWord"),
					config.getProperty("MySqlDataBase"))) {

				throw new Exception(Constants._ERR_DBAccessError);
				
			}

			if(!blNeedSoftBoot){
				SQL.query("DROP TABLE IF EXISTS " + config.getProperty("TableUploadSessions") + ", " + config.getProperty("TableInstanceServers"));

				 
				SQL.query(config.getProperty("CreatTableUploadSessions"));

				SQL.query(config.getProperty("CreatTableInstanceServers"));

				DebugMessage.ShowInfo("Connect to db: Ok.",true);
			}

			


			//AWSCredentials credentials = new PropertiesCredentials(Conductor.class.getResourceAsStream("settings/"+ "AwsCredentials.properties"));
			AWSCredentials credentials = new PropertiesCredentials(Conductor.class.getResourceAsStream("AwsCredentials.properties"));

			ec2 = new AmazonEC2Client(credentials);

			// / Check available zones.
			if (GetCurrentAvailableZones().getAvailabilityZones().size() > 0) {

				DebugMessage.ShowInfo("Available zones access: Ok.",true);
				DebugMessage.ShowInfo("Number of zones: " + GetCurrentAvailableZones().getAvailabilityZones().size(),true);

				int countAvailableZones=0;
				for (int i = 0; i < GetCurrentAvailableZones().getAvailabilityZones().size(); i++)
				{
					//countAvailableZones
					DebugMessage.ShowInfo("Zone "+ "1: " + GetCurrentAvailableZones().getAvailabilityZones().get(i),true);

				
					// I do not know this algorithm is correct or not (lack of fucking AWS documents!)
					if(GetCurrentAvailableZones().getAvailabilityZones().get(i).getRegionName().contains(config.getProperty("StringEndPointRegion")))
					{	
						if(GetCurrentAvailableZones().getAvailabilityZones().get(i).getState().contentEquals("available"))
						{	
							countAvailableZones++;
						}
					}
					
	
				}
				
				
				if(countAvailableZones!=GetCurrentAvailableZones().getAvailabilityZones().size())
					throw new Exception(Constants._ERR_CannotAccessZones);

				// Get number of running instances.
				int numberofrunninginstance = getNumberofInstances(0);

				DebugMessage.ShowInfo("You have " + numberofrunninginstance
						+ " Amazon EC2 instance(s) running.",true);

				System.out.println("===========================================");

				ec2.setEndpoint(config.getProperty("SetEndPointName"));
				
				
				System.out.println(getProcessJobsStatistic("UnderProcess"));
				System.out.println(getProcessJobsStatistic("Finished"));
				System.out.println(getProcessJobsStatistic("Waiting"));
				System.out.println(getProcessJobsStatistic("Unsuccess"));
				System.out.println(getProcessJobsStatistic("WaitingAndUnderProcess"));
	        	//System.in.read();
	           server.stop(0);
				System.exit(0);
	        				
				
				
				/* 
				 * There is not server ready or number of them is not
				 * enough for initialization of system. So run some.
				 */
				if ((numberofrunninginstance < Integer.parseInt(config.getProperty("MinimumEssentialTiers"))) && (blNeedSoftBoot!=false))
				{

					/*
					 *  Calculating number of servers needed to be run as minimum
					 *  number transcoders in the initialization.
					 */
					int _numOfSrvNeedToRun = Integer.parseInt(config.getProperty("MinimumEssentialTiers")) - numberofrunninginstance;

					DebugMessage.ShowInfo("Number of needed transcoder instancess to run for initial stage: " + _numOfSrvNeedToRun,true);

					// CREATE EC2 INSTANCES
					RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
							.withInstanceType(config.getProperty("InstanceType"))
							.withImageId(config.getProperty("InstanceAmiToRun"))
							.withMinCount(_numOfSrvNeedToRun)
							.withMaxCount(_numOfSrvNeedToRun)
							.withMonitoring(false)
							.withKeyName(config.getProperty("InstanceAmiToRunSecurityKey"))
							.withSecurityGroupIds(config.getProperty("InstanceAmiSecurityGroup"));

					RunInstancesResult runInstances = ec2.runInstances(runInstancesRequest);

					/*
					 * Now waiting for 120s to servers to be run. Except after
					 * timeout something is wrong.
					 */
					int counter = 0;

					boolean isRunning = true;
					int i;

					System.out.println("===========================================");

					while (isRunning) // the loop
					{
						i = waitInitialInstancesTobeReady();

						System.out.print("Number of current running servers in the waiting loop: " + i);

						// waits until all the servers be in running state.
						if (i == Integer.parseInt(config.getProperty("MinimumEssentialTiers"))) {
							System.out.println("");
							System.out.println("Minimum transcoding servers added and finished. Continue ....");
							isRunning = false;
							break;
						}

						counter++;

						// 24*5000 = 120 Second
						if (counter == Integer.parseInt(config.getProperty("TimeWaitInstanceBeOprational"))) {
							isRunning = false;
							throw new Exception(Constants._ERR_CannotRunInstance);
						}
						
						DebugMessage.ShowInfo("\r" + (counter * Integer.parseInt(config.getProperty("TickTimeTierBeReady")))/1000 + " second... ",true);
						
						Thread.sleep(Integer.parseInt(config.getProperty("TickTimeTierBeReady"))); // the timing mechanism
					}
					
				} else {

					DebugMessage.ShowInfo("No need to add any instance in initialization. Continue ....",true);
				}

			
				
					/*
					 * Put recently added transcoding servers in the database as
					 * pool of current available servers.
					 */
					//if (!LogTheServersInTheDB(0))
					//	throw new Exception(Constants._ERR_DBAccessError);

			} else {
				throw new Exception(Constants._ERR_CannotAccessZones);
			}

		}

		catch (Exception e) {
			
			
			e.printStackTrace();
			
			String reflectErr;
			
			switch (e.getMessage()) {
			
			case Constants._ERR_CannotAccessZones:

				reflectErr = DebugMessage.ShowErrors("init()", e.getMessage(), "Can not access any available zone",true);
				
				Log_Report(reflectErr);
				
				System.exit(1);
				
				break;
				
			case Constants._ERR_DBAccessError:

				System.out.println("-> Err Critical: Can not connect to db error.");
				
				reflectErr = DebugMessage.ShowErrors("init()", e.getMessage(), "Can not connect to db error",true);

				Log_Report(reflectErr);

				System.exit(1);
				
				break;
				
			case Constants._ERR_CannotRunInstance:

				reflectErr = DebugMessage.ShowErrors("init()", e.getMessage(), "Can not run instaces.",true);
				
				Log_Report(reflectErr);
				
				System.exit(1);
				
		/*		sendemail(
						config.getProperty("EmailSenderUsername"),
						config.getProperty("EmailSenderPassword"),
						config.getProperty("EmailSender"),
						config.getProperty("EmailRecipient"),
						"Conductor Can not run instaces in initial stage!",
						"Error High Critical:\nCan not run instaces in initial stage! In init function.");*/
				break;

			default:
				
				reflectErr = DebugMessage.ShowErrors("init()", e.getMessage(), "Uncategorized error",true);
				
				Log_Report(reflectErr);

				System.exit(1);

				break;
			}

		}

		 
	}
	
	/**
	 * Main daemon thread. Calculates and controls the total encoding servers throughput and adds the shortages capacity.  
	 */
	private static void FleecastConductor() {

			FleecastMainScheduler.scheduleAtFixedRate(new Runnable() {
			public void run() {

			try{

				/*	
				  if (FatalErrorThreshold == Integer.parseInt(config.getProperty("FatalErrorThreshold")))	{
						t1.stop();
						throw new Exception(Constants._ERR_ErrorAddEC2InstanceInConductor);
					}
				 */

					if(blPauseConductorEngineTemporary)
					{
						DebugMessage.Debug("Engine paused temporary", "FleecastConductor()",true, 0);
						return;
					}
													
					// Calculating how many process slot we have shortage.
					int numberOfShortageServers = CalculateCapacityWeNeed(config.getProperty("TolerancePercent"));
					
					DebugMessage.Debug("Number of shortage servers-> " + numberOfShortageServers, "FleecastConductor()",true, 0);
					
					if (numberOfShortageServers < 0) {
						throw new Exception(Constants._ERR_DBAccessError);
					}
					else if (numberOfShortageServers > 0) {
						
						if(!add_ServersToAddTroughput(numberOfShortageServers))
							throw new Exception(Constants._ERR_ErrorAddEC2InstanceInConductor);
					}
					else{
						return;
					}

				
			}catch (Exception e){

				
				String relectErr = DebugMessage.ShowErrors("FleecastConductor()", e.getMessage(),"", true);

				sendemail(
				config.getProperty("EmailSenderUsername"),
				config.getProperty("EmailSenderPassword"),
				config.getProperty("EmailSender"),
				config.getProperty("EmailRecipient"),
				"Error High Critical: In main Fleecast thread!",
				relectErr);
				
				Log_Report(relectErr);

		System.exit(1);
			}


			}
		}, 0, Integer.parseInt(config.getProperty("MainConductorSchedulerLoopTick")), TimeUnit.MILLISECONDS);
}
	
/**
 * Health Check Thread. Checks encoding servers health by http ping and remove stopped servers. 
 */
private static void  HealthCheck(){
	

	HealthCheck.scheduleAtFixedRate(new Runnable() {
	public void run() {

	try{

		doHealthCheck();
		
	}catch (Exception e){

		DebugMessage.ShowErrors("HealthCheck()", e.getMessage(), "Terminate program", true);	
		System.exit(1);
	}


	}
}, 0, Integer.parseInt(config.getProperty("MainHealthCheckSchedulerLoopTick")), TimeUnit.SECONDS);
	
}

/**
 * Health check method. Health Check Thread. Checks encoding servers health by http ping and remove stopped servers.
 * 
 */
private static void doHealthCheck() throws Exception{


	/*
	 * the below query means we have still some not ready instances. 
	 * when all of instances are ready we have a query result equals zero.
	 */
	String Sqlquery = "select * from " + config.getProperty("TableInstanceServers") + " where ready_to_work=1;";

	ResultSet result = SQL.select(Sqlquery);

	if (!SQL.queryWasSuccess())
		throw new Exception(Constants._ERR_DBAccessError);

	if (result != null) {


		String PrivateIP="";
		String instanceId="";
		String HHTP_ProtocoleMode=config.getProperty("HttpOrHttpsPRotocolModeForHealthCheck");
		int i =0;
		int CriticalError=0;
		// These variables can be any type of security tokens in the future.
		String []strNamesToPost = {""};
		String []strValsToPost = {""};

		while (result.next()) {

			PrivateIP=result.getString("private_ip");
			instanceId=result.getString("instance_id");

			i = RestCommands.HTTPCommandsFullResponse(HHTP_ProtocoleMode + PrivateIP,strNamesToPost,strValsToPost,3000);

			DebugMessage.Debug("Server id " + instanceId + " Machine response->" + i,"doHealthCheck()",true,0);


			if(i!=200)
			{
				CriticalError++;

				//Remove the server name from instance_servers table
				SQL.query("DELETE FROM " + config.getProperty("TableInstanceServers") + " WHERE instance_id = '"+instanceId+"'");

				if (!SQL.queryWasSuccess())
					throw new Exception(Constants._ERR_DBAccessError);
				
				// Update all of related jobs for this instance to serverr state in order to report the service problem to the user.
				SQL.query("UPDATE " + config.getProperty("TableUploadSessions") + " SET current_process=-2,progress_report = 'serverr' WHERE instance_id = '" + instanceId + "' AND (current_process=1 OR current_process=2);");

				if (!SQL.queryWasSuccess())
					throw new Exception(Constants._ERR_DBAccessError);
				
				// Shoutdown the Instance in EC2
				List<String> instancesToTerminate = new ArrayList<String>();

				instancesToTerminate.add(instanceId);
				TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest();
				terminateRequest.setInstanceIds(instancesToTerminate);
				ec2.terminateInstances(terminateRequest); 
			}

		}
		
		DebugMessage.Debug("Row numeber " + result.getRow() + ", CriticalError count->" + CriticalError,"doHealthCheck()",true,0);

		// Check if we have all of servers off it means network has outage.
		result.last();
			
		if(CriticalError==result.getRow() && CriticalError > 0)
			throw new Exception(Constants._ERR_NetworkAccessError);

	}



}	 


/**
 * Stops threads when program is going to be terminated.
 *  
 */
private void stopThreadsWhenGoShutdown(){
	
}

/**
 * Wait for initial servers to be operative and the run main Daemon.
 * 
 * @return
 * @throws Exception
 */
private static int waitInitialInstancesTobeReady() throws Exception{
		
		/*
		 * the below query means we have still some not ready instances. 
		 * when all of instances are ready we have a query result equals zero.
		 */
			String Sqlquery = "select count(ready_to_work) as WaitState from " + config.getProperty("TableInstanceServers") + " where ready_to_work=1;";
			
			ResultSet result = SQL.select(Sqlquery);
	
			if (!SQL.queryWasSuccess())
				throw new Exception(Constants._ERR_DBAccessError);
	
						// Skip first record which is contained of count title.
						result.next();
						int WaitState = result.getInt("WaitState");
						
						DebugMessage.Debug("WaitState-> " + WaitState,"waitInitialInstancesTobeReady()",true,0);
						
						return WaitState;
}

	/**
	 * PeakPredictorFunction The factor we calculate in one AI algorithm to predict the grows of 
	 * traffic over the time and add some more tiers to calculated throughput
	 * 
	 * @return
	 */
	private static int PeakPredictorFunction() {

		/**
		 * - In advances version of Fleecast Conductor this function should
		 * return additional numbers of servers based on the function of time
		 * and one AI algorithm (e.g Neural Network). For example there are some
		 * moments which the peak of uploads by users gets tremendously high so
		 * Conductor can predict the high amount of requests in a short time and
		 * add some servers to improve transcoding throughput.
		 * 
		 * - I found one technique as Holt-Winters Forecasting. The related
		 * article are here: http://static.usenix.org/events/lisa00/brutlag.html
		 * http://en.wikipedia.org/wiki/Exponential_smoothing.
		 */

		//return Integer.parseInt(config.getProperty("SupportStandbyServers"));
		
		return 0;
	}
	
	/**
	 * <p>We calculate here how many servers we need to process the jobs. It has a <b>tolerance factor</b> 
	 * which lets us to put for a more long time remained waiting jobs and do not add one more 
	 * server and save money. This plan for expensive tiers with high number of CPU and memory is 
	 * a great money saver.(Fuck your explanation Nader!)</p>
	 * 
	 * 
	 * @param tolerancePercent A percent number (double) between <b>0.0%</b> up to <b>100.0%</b>. 
	 * 							It says up to which number after floating point we are authorized 
	 * 							to add one tier more to handle all remained jobs. It means we can 
	 * 							add one tier to handle a little bit jobs which are not to be 
	 * 							contained in the other tiers.
	 *
	 * @return
	 */
		 private static int CalculateCapacityWeNeed(String tolerancePercent) throws Exception
		    {

			 String Sqlquery = "select count(current_process) as WatingAndUnderProcessJobsAndCurrentSrvers from " + config.getProperty("TableUploadSessions") + " where current_process=0 " +
			 		"UNION ALL select count(current_process) from " + config.getProperty("TableUploadSessions") + " where current_process>0 AND current_process <3;";

			 /*String Sqlquery = "select count(current_process) as WatingAndUnderProcessJobsAndCurrentSrvers from " + config.getProperty("TableUploadSessions") + " where current_process=0 " +
			 		"UNION ALL select count(current_process) from " + config.getProperty("TableUploadSessions") + " where current_process>0 AND current_process <3 UNION ALL " +
			 		"select count(instance_id) from " + config.getProperty("TableInstanceServers") + " WHERE ready_to_work <> -1;";*/

			 	// System.out.println(Sqlquery);
			 
				ResultSet result = SQL.select(Sqlquery);

				if (!SQL.queryWasSuccess())
					throw new Exception(Constants._ERR_DBAccessError);

				int Slots=Integer.parseInt(config.getProperty( "MaxNumberOfJobsSlotPerServer"));
				int MaximumNumberOfServers=Integer.parseInt(config.getProperty( "MaximumAccessableTiersFromAmazon"));
				int MinimumNumberOfServers=Integer.parseInt(config.getProperty( "MinimumEssentialTiers"));
				
				if (result != null) {

					// Skip first record which is contained of sum title.
					result.next();
					int numberOfWaitingJobs = result.getInt("WatingAndUnderProcessJobsAndCurrentSrvers");
					
					result.next();
					int numberOfUnderProcess = result.getInt("WatingAndUnderProcessJobsAndCurrentSrvers");					
					
					result.next();
					int numberOfRuningPendingServers = getNumberOfPendingAndRunnedInstances(config.getProperty("InstanceAmiSecurityGroup"));

					int numberOfRegionAllRuningPendingServers = getNumberOfPendingAndRunnedInstances("");
					
					DebugMessage.Debug("Waiting jobs-> " + numberOfWaitingJobs + ", Under process jobs-> " + 
					numberOfUnderProcess + ", Number of servers-> " + numberOfRuningPendingServers,"CalculateCapacityWeNeed()",true,0);
					
					
					if(numberOfRuningPendingServers==0)
						return -2;
					
					int totoalReadyForUseCapacity =  (numberOfRuningPendingServers * Slots) -  numberOfUnderProcess;
					
					DebugMessage.Debug("Total ready for use capacity-> " + totoalReadyForUseCapacity, "CalculateCapacityWeNeed()",true, 0);
					
					// We need to more capacity.
					if(numberOfWaitingJobs > totoalReadyForUseCapacity)
					{
						
						// To avoid get negative number for next calculations.  
						if(totoalReadyForUseCapacity < 0)
							totoalReadyForUseCapacity=0;

						double calculatedAdditionTiers =  (numberOfWaitingJobs - totoalReadyForUseCapacity) / (double) Slots;
						 
						 tolerancePercent = tolerancePercent.replaceAll("%", "");

						 // Get capacity before float point (decimal section).
						 int getDecimalSection =  (int) calculatedAdditionTiers;


						 String strNumberWithFloat = String.valueOf(calculatedAdditionTiers);
						 	
					        int i = strNumberWithFloat.indexOf(".");
							 // Get capacity after float point.				        
					        double tmpNumber = Double.parseDouble("0." + strNumberWithFloat.substring(i+1));
					        
					        // Calculates after floating point of calculatedAdditionTiers how much is the 1 (1.0).
					        double numOfPrcent = (tmpNumber / 1) * 100;
					        
					        
					        int allOfCalculatedResults=0;

					        if(numOfPrcent>Double.parseDouble(tolerancePercent))
					        {
					        	allOfCalculatedResults = getDecimalSection + 1 + PeakPredictorFunction();
					        
					        }
					        // The number had no number over 0 after floating point  
					        else
					        {
					        	allOfCalculatedResults= getDecimalSection + PeakPredictorFunction();
					        }
					        
					       
					    	/***********************************************************
				        	 **********************IMPORTANT SECTION********************
				        	 * Check if the calculated number for extra servers is over
				        	 * the maximum offered servers by the Amazon policy reduce 
				        	 * the calculated to the max available.
				        	 ***********************************************************
				        	 ***********************************************************/
				        	
				        	if((allOfCalculatedResults + numberOfRegionAllRuningPendingServers) >= MaximumNumberOfServers)
				        		allOfCalculatedResults = MaximumNumberOfServers- numberOfRegionAllRuningPendingServers;

					        DebugMessage.Debug("Number of Instances in the region-> "  + numberOfRegionAllRuningPendingServers, "CalculateCapacityWeNeed()",true, 0);
				        	 
				        	/*
				        	* The minimum level of predefined server by us has 
				        	* priority to this calculation so we return zero if
				        	* the calculation is under the minimum server numbers.
				        	* the extra servers will be removed from the total 
				        	* jobs throughput by themselves (please refer to
				        	* auto sleep function in the instance code).
				        	*/
				        	if(allOfCalculatedResults < MinimumNumberOfServers)
				        		allOfCalculatedResults = 0;
				        	
					        DebugMessage.Debug("All of calculated results-> " + allOfCalculatedResults, "CalculateCapacityWeNeed()",true, 0);

				        	
				        	return allOfCalculatedResults;
					        
					}
					else{
						 return 0;
					}
					
				} else {
					return -1;
				}
				
			
		       
		    }
		 
/**
 * Get Amazon available zones.
 * 
 * @return DescribeAvailabilityZonesResult (available zones)
 */
private static DescribeAvailabilityZonesResult GetCurrentAvailableZones() {

	DescribeAvailabilityZonesResult availabilityZonesResult = ec2
			.describeAvailabilityZones();

	return availabilityZonesResult;


}
		 
		
/**
 * Gets number of current running and pending instances in a security group.
 * 
 *  
 * @param inTheSecurityGroup if we put null for this argument it gives all of a region instances.
 * @return
 */
		 private static int getNumberOfPendingAndRunnedInstances(String inTheSecurityGroup) {

				try {
					
					List<Filter> ls = new ArrayList<Filter>();

					ls.add(new Filter("instance-state-name").withValues("running").withValues("pending"));

					// Get the instances in the security group.
					if(inTheSecurityGroup.length()>0)
					ls.add(new Filter("group-name").withValues(inTheSecurityGroup));

					DescribeInstancesResult describeInstancesRequest = ec2.describeInstances(new DescribeInstancesRequest().withFilters(ls));

					// instances.getPlacement().getAvailabilityZone();

					List<Reservation> reservations = describeInstancesRequest.getReservations();
					
					Set<Instance> instances = new HashSet<Instance>();

					for (Reservation reservation : reservations) {
						instances.addAll(reservation.getInstances());
					//	System.out.println(reservation.getInstances());
					}

					return instances.size();
				} catch (Exception e) {

					DebugMessage.ShowErrors("getNumberOfPendingAndRunnedInstances()", e.getMessage(), "", true);
					return -1;
				}

			}
		 
//	private static boolean LogTheServersInTheDB(int logMode) {
//
//		try {
//
//			List<Filter> ls = new ArrayList<Filter>();
//
//			/**
//			 * We need after adding more instances add them in the DB in pending
//			 * stage and not as run servers. Because if we don't do this
//			 * Conductor will looses the correct calculation in the loop and it
//			 * will try to show unpredictable behavior.
//			 **/
//			if (logMode == 0)
//				ls.add(new Filter("instance-state-name").withValues("running"));
//			else
//				ls.add(new Filter("instance-state-name").withValues("running")
//						.withValues("pending"));
//
//			ls.add(new Filter("group-name").withValues(config
//					.getProperty("InstanceAmiSecurityGroup")));
//
//			DescribeInstancesResult describeInstancesRequest = ec2
//					.describeInstances(new DescribeInstancesRequest()
//							.withFilters(ls));
//
//			List<Reservation> reservations = describeInstancesRequest
//					.getReservations();
//			Set<Instance> instances = new HashSet<Instance>();
//
//			for (Reservation reservation : reservations) {
//				instances.addAll(reservation.getInstances());
//				
//		        DebugMessage.Debug(reservation.getInstances().toString(), "LogTheServersInTheDB()",true, 0);
//
//
//				for (int i = 0; i < instances.size(); i++) {
//				
//					// if(reservation.getInstances().get(i).getPrivateIpAddress()!=
//					// null)
//					// {
//
//					/**
//					 * Add instances to the DB. The below query checks to be
//					 * sure the instance ID doesn't exist at the moment in the
//					 * DB (the select section of query). this is important in
//					 * order to avoid double name in the instances list in the
//					 * DB. Every instance after pending stage and become ready
//					 * first and foremost will write its private IP address in
//					 * the same its EC2 ID record.
//					 **/
//
//					SQL.query("INSERT INTO " + config.getProperty("TableInstanceServers") + " (instance_id, private_ip) SELECT '"
//							+ reservation.getInstances().get(i).getInstanceId()
//							+ "', '"
//							+ "0.0.0.0"
//							+ "' WHERE NOT EXISTS (SELECT instance_id, private_ip FROM " + config.getProperty("TableInstanceServers") + " WHERE instance_id='"
//							+ reservation.getInstances().get(i).getInstanceId()
//							+ "')");
//
//					// }
//
//				}
//				instances = null;
//				instances = new HashSet<Instance>();
//			}
//
//			return true;
//		} catch (Exception e) {
//			
//			DebugMessage.ShowErrors("LogTheServersInTheDB()", e.getMessage(), "", true);
//
//			return false;
//		}
//
//	}

		 /**
		  * Get number of running,pending,stopped,terminated,stopping,shutting-down servers in one security group.
		  * @param typeOfReport
		  * @return
		  */
	private static int getNumberofInstances(int typeOfReport) {

		try {
			
			List<Filter> ls = new ArrayList<Filter>();

			if (typeOfReport == 0) {
				ls.add(new Filter("instance-state-name").withValues("running"));
			} else if (typeOfReport == 1) {
				ls.add(new Filter("instance-state-name").withValues("pending"));
			} else if (typeOfReport == 2) {
				ls.add(new Filter("instance-state-name").withValues("stopped"));
			} else if (typeOfReport == 3) {
				ls.add(new Filter("instance-state-name").withValues("terminated"));
			} else if (typeOfReport == 4) {
				ls.add(new Filter("instance-state-name").withValues("stopping"));
			} else if (typeOfReport == 5) {
				ls.add(new Filter("instance-state-name").withValues("shutting-down"));
			} else if (typeOfReport == 6) {
				ls.add(new Filter("instance-state-name").withValues("running").withValues("pending"));
			} else {
				ls.add(new Filter("instance-state-name").withValues("pending"));
				ls.add(new Filter("instance-state-name").withValues("running"));
			}

			// Get the instances in the security group.
			ls.add(new Filter("group-name").withValues(config
					.getProperty("InstanceAmiSecurityGroup")));

			DescribeInstancesResult describeInstancesRequest = ec2
					.describeInstances(new DescribeInstancesRequest()
							.withFilters(ls));

			// instances.getPlacement().getAvailabilityZone();

			List<Reservation> reservations = describeInstancesRequest
					.getReservations();
			Set<Instance> instances = new HashSet<Instance>();

			for (Reservation reservation : reservations) {
				instances.addAll(reservation.getInstances());
			}

			return instances.size();
		} catch (Exception e) {
			
			DebugMessage.ShowErrors("getNumberofInstances()", e.getMessage(), "", true);
			
			return -1;
		}

	}

/**
 * Add new encoding servers to ec2 and a region from an optional AMI 
 * 
 * @param number_OfServersToBeAdded
 * @return
 */
private static boolean add_EC2Instances(int number_OfServersToBeAdded) {

		try {

			ec2.setEndpoint("ec2.us-east-1.amazonaws.com");

			// CREATE EC2 INSTANCES
			RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
					.withInstanceType(config.getProperty("InstanceType"))
					.withImageId(config.getProperty("InstanceAmiToRun"))
					.withMinCount(number_OfServersToBeAdded)
					.withMaxCount(number_OfServersToBeAdded)
					.withMonitoring(false)
					.withKeyName(config.getProperty("InstanceAmiToRunSecurityKey"))
					.withSecurityGroupIds(
							config.getProperty("InstanceAmiSecurityGroup"));

			RunInstancesResult runInstances = ec2
					.runInstances(runInstancesRequest);

			List<Instance> instances = runInstances.getReservation()
					.getInstances();

			for (Instance instance : instances) {
				// System.out.print(instance.getInstanceId() + ", ");
				SQL.query("INSERT INTO " + config.getProperty("TableInstanceServers") + " (instance_id, private_ip) SELECT '"
						+ instance.getInstanceId()
						+ "', '"
						+ "0.0.0.0"
						+ "' WHERE NOT EXISTS (SELECT instance_id, private_ip FROM " + config.getProperty("TableInstanceServers") + " WHERE instance_id='"
						+ instance.getInstanceId() + "')");
				
				if (!SQL.queryWasSuccess())
					throw new Exception(Constants._ERR_ErrorLogServersInDB);

			}

			
			
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
	        DebugMessage.Debug("Added instances at " + dateFormat.format(date)	+ ": " + number_OfServersToBeAdded, "add_EC2Instances()",false, 0);
			
			return true;
			
		} catch (Exception e) {

			DebugMessage.ShowErrors("add_EC2Instances()", e.getMessage(), "", true);
			return false;
		}

	}

/**
 * Calls the add_EC2Instances() method and adds the calculated shortage encoding servers. 
 * 
 * @param numberOfShortageServers
 * @return
 */
	private static boolean add_ServersToAddTroughput(int numberOfShortageServers) {

		try {

			/*
			 * Pause Conductor loop for a while to improve the throughput and
			 * after all we return it back to life
			 */
			// Finally add servers
			if (!add_EC2Instances(numberOfShortageServers))
				throw new Exception(Constants._ERR_ErrorAddEC2Instance);

			return true;

		} catch (Exception e) {

			DebugMessage.ShowErrors("add_ServersToAddTroughput()", e.getMessage(), "", true);
			return false;
		}

	}
	
	
	
	/**
	 * Checks if there is a file with the name of boot.ini do not drop the database <br /> 
	 * entries and add any instances in the initial of Conductor program. 
	 * @return
	 */
	private static boolean checkForSoftBoot(){
		
		File file = new File(System.getProperty("user.dir")
				+ "/boot.ini");

		// If true means we should do soft boot.
		if (file.exists()) {
			
			file.delete();
			return true;
		}
		else{
			
			return false;
		}

		
	}	
	
	/**
	 * Reboots UBUNTU and daemon
	 * @param softreboot
	 */
	private static void RebootMachine(boolean softreboot) {

		try {
			
			File file = new File(System.getProperty("user.dir")
					+ "/boot.ini");

			if (file.exists()) {
				file.delete();
			}			
			
			if(softreboot)
			{
				file.createNewFile();
			
			
				FileWriter fileWritter = new FileWriter(file, true);
				BufferedWriter bufferWritter = new BufferedWriter(fileWritter);

				bufferWritter.write("softboot=true");
				bufferWritter.close();
			
			}
			
			FleecastMainScheduler.shutdownNow();
			HealthCheck.shutdownNow();
			server.stop(0);
			
			ProcessBuilder proc = new ProcessBuilder("sudo", "reboot");
			proc.start();
			
			System.exit(0);
			
		} catch (Exception e) {
			e.printStackTrace();

		}

	}

/**
 * Log the data in a file in the log folder and adds time stamp to each line.
 * 		
 * @param Logdata
 */
	private static void Log_Report(String Logdata) {

		try {
			DateFormat df = new SimpleDateFormat("dd_MM_yyyy");
			String formattedDate = df.format(new Date());

			// Add time stamp to log file
			File file = new File(System.getProperty("user.dir")
					+ "/logs/conductorLog_" + formattedDate + ".log");

			// if file doesn't exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}

			df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
			formattedDate = df.format(new Date());

			FileWriter fileWritter = new FileWriter(file, true);
			BufferedWriter bufferWritter = new BufferedWriter(fileWritter);

			bufferWritter.write(df.format(new Date()).toString() + ":-> "
					+ Logdata + System.getProperty("line.separator")
					+ System.getProperty("line.separator"));
			bufferWritter.close();
		} catch (Exception e) {
			e.printStackTrace();

		}

	}

	/**
	 * Sends email to Admin for errors and different reports.<br/><br/>
	 * <b>IMPOTRANT NOTE:</b> If you didn't let to gmail this
	 * DNS IP the Gmail get the emails from this method as atack
	 * to the account. You should add the permission to Gmail
	 * for accessing by the IP address of daemon.
	 *  
	 * @param strUsername
	 * @param strPassword
	 * @param strSender
	 * @param strRecipient
	 * @param strSubject
	 * @param strBody
	 */
	private static void sendemail(final String strUsername,
			final String strPassword, String strSender, String strRecipient,
			String strSubject, String strBody) {
		// final String strUsername = "nadernt@gmail.com";
		// final String strPassword = "totemkhamon";

		Properties props = new Properties();
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", "smtp.gmail.com");
		props.put("mail.smtp.port", "587");

		Session session = Session.getInstance(props,
				new javax.mail.Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(strUsername,
								strPassword);
					}
				});

		try {

			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(strSender));
			message.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(strRecipient));
			message.setSubject(strSubject);
			message.setText(strBody);

			Transport.send(message);

			System.out.println("Email Error Report Done!");

		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}

	}

	
/*********************************************************************
 * *******************************************************************
 * ************************** REST REPORT ****************************
 * *******************************************************************
 ********************************************************************/
/**
 * Returns a security group or in a region running and pending servers.
 * If inTheSecurityGroup = "" then it returns all of servers (running and pending) in a region.
 * 	
 * @param inTheSecurityGroup
 * @return
 */
public static String restGetServersInfo(String inTheSecurityGroup) {
	
	try {
		
		List<Filter> ls = new ArrayList<Filter>();

		ls.add(new Filter("instance-state-name").withValues("running").withValues("pending"));

		// Get the instances in the security group if .
		if(inTheSecurityGroup.length()>0)
			ls.add(new Filter("group-name").withValues(inTheSecurityGroup));
			
		DescribeInstancesResult describeInstancesRequest = ec2.describeInstances(new DescribeInstancesRequest().withFilters(ls));

		List<Reservation> reservations = describeInstancesRequest.getReservations();
		
		Set<Instance> instances = new HashSet<Instance>();
		String strResulttoReturn = null;
		for (Reservation reservation : reservations) {
			instances.addAll(reservation.getInstances());
		//	System.out.println(reservation.getInstances().toString());
			strResulttoReturn += reservation.getInstances().toString();
		}
		
		return strResulttoReturn;
		
	} catch (Exception e) {

		DebugMessage.ShowErrors("restGetServersInfo()", e.getMessage(), "", true);
		return "";
	}
	
		

}



/**
 * Stops the Conductor daemon.
 */
public static void service_shutdown()
{
	FleecastMainScheduler.shutdownNow();
	HealthCheck.shutdownNow();
	server.stop(0);
	System.exit(0);
}


/**
 * Pause Conductor temporary.
 * 
 * @param PauseState : True/False stops or continue the Conductor engine.  
 */
public static void PauseConductorEngineTemporary(boolean PauseState){
	
	blPauseConductorEngineTemporary= PauseState;
}

private static JSONObject postCommands(String [] IPIDs,	String []strNamesToPost,String []strValsToPost){
	
	JSONObject jsonObject = new JSONObject();

	String HHTP_ProtocoleMode = config.getProperty("HttpOrHttpsPRotocolModeForHealthCheck");

	String server_result;
	for(int i=0; i< IPIDs.length; i+=2)
	{
		RestCommands.HTTPCommandsFullResponse(HHTP_ProtocoleMode + IPIDs[i] + "/rest" ,strNamesToPost,strValsToPost,3000);
		
		server_result = RestCommands.ServerResult();
		
		try {jsonObject.put(IPIDs[i],server_result);} catch (Exception e) {}
		
		DebugMessage.Debug("Response from instance IP: " + IPIDs[i]+ " ID: " + IPIDs[i+1] + "-> " + server_result, "propagatePauseEncodeServers()", false, 1);
	}
	
	return jsonObject;
}

/**
 *  Returns the servers IP and instance ID.
 *  
 * @param ExcludeSeniorEncoder : The option to exclude senior encode machine from the command.
 * @return List <String>
 */
public static List <String> getPrivateIPs(boolean ExcludeSeniorEncoder){
	
try{
	
	String Sqlquery = "select * from " + config.getProperty("TableInstanceServers") + " where ready_to_work=1 ORDER BY id ASC;";

	ResultSet result = SQL.select(Sqlquery);

	if (!SQL.queryWasSuccess())
		throw new Exception(Constants._ERR_DBAccessError);

	if (result != null) {

		List<String> strIP_ID_Vals = new ArrayList<String>();
		
		boolean SkipFirstItemIfShouldExcludeSenior=false;
		
		if(ExcludeSeniorEncoder)
		{
			SkipFirstItemIfShouldExcludeSenior=true;
		}
		
		while (result.next()) {
			
			
			if(!SkipFirstItemIfShouldExcludeSenior){

				System.out.println(result.getString("id") + " " + result.getString("instance_id"));
				strIP_ID_Vals.add(result.getString("private_ip"));
				strIP_ID_Vals.add(result.getString("instance_id"));
				
			}
			else{
				
				SkipFirstItemIfShouldExcludeSenior=false;
			}
			
		}
		
		return strIP_ID_Vals;
	}
	
	return null;
	
	}catch (Exception e){
		System.out.println(e.getMessage());
		return null;
	}
}

/**
 * Propagate a command to all of encode servers. The commands are <em>Pause, Resume, Shutdown and Reset the log file</em> in the encoders. 
 *    
 * @param optionCommand : The command we want to send to servers. PropagatePauseToEncoders, PropagateResumeToEncoders, PropagateShutdownToEncoders, PropagateResetTheLogFileToAllEncoders
 * @param includeSeniorEncoder : The option to exclude senior encode machine from the command.
 * @return JSONObject
 */
public static JSONObject propagateCommandToEncodeServers(String optionCommand,boolean includeSeniorEncoder)
{
	List<String> IPIDs =getPrivateIPs(includeSeniorEncoder);
	
	JSONObject jsonobject = new JSONObject();
	
	// if there is no server.
	if(IPIDs.size()==0)
	{
		try {jsonobject.put("null", "no server found");} catch (Exception e) {	}
		return jsonobject;
	}
		

	String []strNamesToPost = {"restcmd"}; 
	String []strValsToPost = new String[1];
	
	if(optionCommand.equals("PropagatePauseToEncoders"))
	{
		strValsToPost[0] = "100";
	}
	else if(optionCommand.equals("PropagateResumeToEncoders"))
	{
		strValsToPost[0] = "101";
		
	}
	else if(optionCommand.equals("PropagateShutdownToEncoders"))
	{
		strValsToPost[0] = "451";
	}
	
	else if(optionCommand.equals("PropagateResetTheLogFileToAllEncoders"))
	{
		strValsToPost[0] = "reset";
	}
	else{
		
		try {jsonobject.put("null", "unrecognized command");} catch (Exception e) {	}
		return jsonobject;
	}
	
	return postCommands(IPIDs.toArray(new String[IPIDs.size()]),strNamesToPost,strValsToPost);
	
}

/**
 * Propagate a command to an encode server.
 * 
 * @param instanceID : The ID of command's target encoder machine 
 * @param optionCommand : The command we want to send to servers. PropagatePauseToAnEncoder, PropagateResumeToAnEncoder, PropagateShutdownToAnEncoder, PropagateForceShutdownToAnEncoder
 * @return JSONObject
 */
public static JSONObject propagateCommandToAnEncodeServer(String instanceID,String optionCommand)
{
	
	List<String> IPIDs=new ArrayList<String>();
	
	IPIDs.add(instanceID);

	String []strNamesToPost = {"restcmd"}; 
	String []strValsToPost = new String[1];
	
	if(optionCommand.equals("PropagatePauseToAnEncoder"))
	{
		strValsToPost[0] = "100";
	}
	else if(optionCommand.equals("PropagateResumeToAnEncoder"))
	{
		strValsToPost[0] = "101";
		
	}
	else if(optionCommand.equals("PropagateShutdownToAnEncoder"))
	{
		strValsToPost[0] = "451";
	}
	else if(optionCommand.equals("PropagateForceShutdownToAnEncoder"))
	{
		// Shoutdown the Instance in EC2
		List<String> instancesToTerminate = new ArrayList<String>();
	
		instancesToTerminate.add(instanceID);
		TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest();
		terminateRequest.setInstanceIds(instancesToTerminate);
		ec2.terminateInstances(terminateRequest); 
		
		JSONObject jsonObject = new JSONObject();

		try {jsonObject.put(instanceID,"force_shutdown");} catch (Exception e) {}
			
		return jsonObject; 
	}
	else if(optionCommand.equals("PropagateResetTheLogFileToAnEncoder"))
	{
		strValsToPost[0] = "reset";
	}
	else
	{
		JSONObject jsonobject = new JSONObject();
		
		try {jsonobject.put("null", "unrecognized command");} catch (Exception e) {	}
		
		return jsonobject;
	}
	
	return postCommands(IPIDs.toArray(new String[IPIDs.size()]),strNamesToPost,strValsToPost);

}

/**
 * Query about database encoding jobs.
 * 
 * @param option : Option for query <em><b>UnderProcess, Finished, Unsuccess, Waiting, WaitingAndUnderProcess</b></em>.
 * @return Integer as query result. <br/><b>Errors:</b> <b>-1</b> query was not successful <b>-2</b> general error.
 */
public static int getProcessJobsStatistic(String option)
{	
try {
	
	/**
	 * Flag for current process stage and success and fails for internal usage
	 * of servers. The values are as follow: 
	 * if = -1 -> Error had found in the encoding file.
	 * if = 0  -> The process is in the queue list to be picked up by one of servers for process. 
	 * if = 1  -> Queuing the file and waiting for encoding to be start.
	 * if = 2  -> Encode is progressing.
	 * if = 3  -> Encoding finished successfully.
	 **/
	
	String query="";
	
	if(option.equals("UnderProcess"))
	{
		query = "select count(current_process) as StatisticsReport from " + config.getProperty("TableUploadSessions") + " where current_process IN (1,2);";
	}
	else if(option.equals("Finished"))
	{
		query = "select count(current_process) as StatisticsReport from " + config.getProperty("TableUploadSessions") + " where current_process=3;";
	}
	else if(option.equals("Unsuccess"))
	{
		query = "select count(current_process) as StatisticsReport from " + config.getProperty("TableUploadSessions") + " where current_process=-1;";
	}
	else if(option.equals("Waiting"))
	{
		query = "select count(current_process) as StatisticsReport from " + config.getProperty("TableUploadSessions") + " where current_process=0;";
	}
	else if(option.equals("WaitingAndUnderProcess"))
	{
		query = "select count(current_process) as StatisticsReport from " + config.getProperty("TableUploadSessions") + " where current_process IN (1,2,0);";
	}
	
	ResultSet result = SQL.select(query);

		if (!SQL.queryWasSuccess())
			throw new Exception(Constants._ERR_DBAccessError);

		if (result != null) {
			result.next();
			return result.getInt("StatisticsReport");
		}

		return	-1;
		
} catch (Exception e) {
return -2;
}

}
/**************************************************************************
 * ******************************OLD SHITS!********************************
 *************************************************************************/

}

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

/*
 * This class is the Encoder thread and its responsibility is to 
 * convert the files to mp3 format based on user financial plan.
 * It has a pool (ExecutorPoll class) which guarantees the running
 * each encoding slot safly and separated from the other slots.
 *  
 */
import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.restcommands.RestCommands;

public class EncoderThread extends Encoder implements Callable<String> {
	
	private boolean bl_terminateProcess = false;
	private int user_id;
	private int station_id;
	private String file_name;
	private RestCommands restcommands;
 
	/**
	 * Flag for current process stage and success and fails for internal usage
	 * of servers. The values are as follow: 
	 * if = -1 -> Error had found in the encoding file.
	 * if = 0  -> The process is in the queue list to be picked up by one of servers for process. 
	 * if = 1  -> Queuing the file and waiting for encoding to be start.
	 * if = 2  -> Encode is progressing.
	 * if = 3  -> Encoding finished successfully.
	 **/
	
	//private int current_process;
	private String progress_report;
	private String old_progress_report;
	private int customer_policy;
	//private Config config;
	private int PostgersDBID;
	//private boolean debugModeEnable;
	private Process process;
	private String strOutputFile;
	private String strToken;
	private ProcessBuilder processBuilder;
	private int CoversionReportSquenceInSeconds;
	private boolean AllowSuccessJobReport;
    //private ConductorMsgDelegator semaphoremsg = new SemaphoreMsg(); 
 //   private String pathtoRamDisk;
  //  private String outputFileSwapRepFile;

	/** 
	 * We do not update the percentage of conversion except old value + dbRequestSkipFactor be >= than new number
	 * in order to avoiding the DB overload requests by the conversion threads. 
	 */
	private int dbRequestSkipFactor;
	private List<String> TranscodingParameters = new ArrayList<String>();
	private ScheduledExecutorService progressReportThread = Executors.newSingleThreadScheduledExecutor();
	
	interface EncoerThreadConsts {
		public static final String _ERR_CouldNotParseDuration = "could not parse duration audio file";
		public static final String _ERR_FileHasSomeInternaError = "Audio file has internal problem";
		public static final String _ERR_MoveFileToBucket = "move to bucket error";
		public static final String _ERR_DropTmpFile = "error dorop temp file";
		public static final String _ERR_CanNotAccessSwapFile = "can not access swap file";
	
		/**
		 * Flag for current process stage and success and fails for internal usage
		 * of servers. The values are as follow: 
		 * if = -2 -> Error in service to serve the user (e.g. suspension command by Conductor server). 
		 * if = -1 -> Error had found in the encoding file.
		 * if = 0  -> The process is in the queue list to be picked up by one of servers for process. 
		 * if = 1  -> Queuing the file and waiting for encoding to be start.
		 * if = 2  -> Encode is progressing.
		 * if = 3  -> Encoding finished successfully.
		 **/
		
		public static final int EncStage_ServiceIsnotAvailable =-2;
		public static final int EncStage_ErrFoundInTheFile =-1;
		public static final int EncStage_InQueueToPickUp =0;
		public static final int EncStage_QueuingAndWaitEncodeStart =1;
		public static final int EncStage_EncodeIsProgressing =2;
		public static final int EncStage_EncodeFinishedSuccess =3;
		
	}
	
	/**
	 * Class Constructor
	 * 
	 * @param id
	 */
public EncoderThread(String [] arrEncodeJob) {
		try {
		
			dbRequestSkipFactor= Integer.parseInt(config.getProperty("dbRequestSkipFactorByThread"));
			
			CoversionReportSquenceInSeconds = Integer.parseInt(config.getProperty("CoversionReportSquenceInSeconds"));
			
			AllowSuccessJobReport = Boolean.parseBoolean(config.getProperty("AllowSuccessJobReport"));
	    	
			
			// file name
			file_name = arrEncodeJob[4];

			// customer_policy
			customer_policy = Integer.parseInt(arrEncodeJob[8]);
			
			strToken = arrEncodeJob[1];
		
			// db id
			PostgersDBID = Integer.parseInt(arrEncodeJob[0]);
			
		//	outputFileSwapRepFile = System.getProperty("user.home") + "/ramdisk/" + strToken + ".swp";
			
			progress_report = "queue";

			reportJobProgressMethod(progress_report,EncoerThreadConsts.EncStage_QueuingAndWaitEncodeStart);
				

			//SQL.query("update upload_sessions set progress_report='queue', current_process='1' where id = '"
			//		+ PostgersDBID + "';");

			
    		/***************************************
			 * Making FFMPEG encoding options
			 * 
			 * Example of complete arguments to ffmpeg:
			 * userubuntu:$ ffmpeg -i http://mydotcom-to-encode.s3.amazonaws.com/c6xbedf411gbt7ffco3twm79.wav -y -ab 128k -ar 44100 -acodec libmp3lame /var/www/temp_conversions/c6xbedf411gbt7ffco3twm79NAC6796C.mp3
			 *  
			 **************************************/
				TranscodingParameters.add(config
						.getProperty("FFMPEG_PRODUC_ExecPATH"));

			TranscodingParameters.add("-i");

			// / the input file name
			String strInputFile = "http://" + config.getProperty("InputBucket")
					+ ".s3.amazonaws.com/" + file_name;
			
			TranscodingParameters.add(strInputFile);

			TranscodingParameters.add("-y");
			TranscodingParameters.add("-ab");

			if (customer_policy == 0)
				TranscodingParameters.add("128k");
			else if (customer_policy == 1)
				TranscodingParameters.add("192k");
			else if (customer_policy == 2)
				TranscodingParameters.add("256k");
			else if (customer_policy == 3)
				TranscodingParameters.add("320k");
			
			TranscodingParameters.add("-ar");
			TranscodingParameters.add("44100");
			TranscodingParameters.add("-acodec");
			TranscodingParameters.add("libmp3lame");
			
						strOutputFile = config.getProperty("TempEncodingFolderProductionPath") + 
						"/"	+ config.getProperty("OutPutFileNamePrefix") +
						FilenameUtils.removeExtension(file_name) + generateToken(8) + ".mp3";

				TranscodingParameters.add(strOutputFile);

		} catch (Exception e) {

			bl_terminateProcess = true;
			DebugMessage.ShowErrors("EncoderThread()", e.getMessage(), "",true);
			return;
		}

	}

	/**
	 * Encoding and conversion thread.
	 *  
	 */
	public String call()
	{
		
		if(bl_terminateProcess)
			return "NIL_SYSTEM_ERROR";

		// Starting progress report thread. 
		ProgressReport();

		try {
			
			long lStartTime = System.currentTimeMillis();
					
			processBuilder = new ProcessBuilder(TranscodingParameters);
			process = processBuilder.start();

			Scanner sc = new Scanner(process.getErrorStream());
			
			// Find duration
			Pattern durPattern = Pattern.compile("(?<=Duration: )[^,]*");
			String dur = sc.findWithinHorizon(durPattern, 0);
			
			if (dur == null){
				
				process.destroy();
				
				// Put and report the error of encoding in the db.				
				progress_report = "encoderr";

				DebugMessage.Debug("Err: in Call thread of EncoderThread-> " + EncoerThreadConsts._ERR_CouldNotParseDuration,"Call() thread",false,0);

				reportJobProgressMethod(progress_report,EncoerThreadConsts.EncStage_ErrFoundInTheFile);

				UnfinishUnsuccessJobReport(strToken,false);
			

				return strToken;
				
			}

			String[] hms = dur.split(":");
			double totalSecs = Integer.parseInt(hms[0]) * 3600
					+ Integer.parseInt(hms[1]) * 60
					+ Double.parseDouble(hms[2]);
			
			DebugMessage.Debug("Total duration: " + totalSecs + " seconds.","Call() thread",false,0);

			
			String strReturnedConsoleText;
			String strPrecentageOfDecoding;
			String strTmp;
			
			double progress;
			boolean blEncodeWasSuccess = true;
			int semaphoreState=0;
			
			while (sc.hasNextLine()) {
				
				semaphoreState = ConductorMsgDelegator.getInstance().getConductorMsg();
				
				if (semaphoreState > 0) 
				{
					
					DebugMessage.Debug("Shutdown the thread for " + strToken + " token. Semaphore value is " + semaphoreState,"Call() thread",false,0);
					
					process.destroy();
					
					blEncodeWasSuccess=false;

					// Stopping report timer
					progressReportThread.shutdownNow();			
					
					if(!DropTempEncodingFile())
					{
						throw new RuntimeException(EncoerThreadConsts._ERR_DropTmpFile);
						
					}

					
				
					return "STOP_ENCODE_" + strToken;
				}	

				strReturnedConsoleText = sc.nextLine();

				if (strReturnedConsoleText.matches(".*\\bError\\b.*")) {
				
					blEncodeWasSuccess=false;
					
					process.destroy();
					
					StopTimerThread();
					

					if(!DropTempEncodingFile())
						throw new RuntimeException(EncoerThreadConsts._ERR_DropTmpFile);

					// Put error in the db
					progress_report = "encoderr";
					
					DebugMessage.Debug("Err: in Call thread of EncoderThread-> " + EncoerThreadConsts._ERR_CouldNotParseDuration,"Call() thread",false,0);

					reportJobProgressMethod(progress_report,EncoerThreadConsts.EncStage_ErrFoundInTheFile);

					UnfinishUnsuccessJobReport(strToken,false);
					
					return strToken;
					
					//break;
				}

				
				strPrecentageOfDecoding = ProgressPercentage(strReturnedConsoleText);
				
				if (strPrecentageOfDecoding != null) {
					progress = Double.parseDouble(strPrecentageOfDecoding)
							/ totalSecs;

					strTmp = new Double(progress * 100).toString();

					// Pass the encoding progress to the variable in order to
					// use it in the timer.
					progress_report = strTmp.substring(0, strTmp.indexOf('.'));

					DebugMessage.Debug("Percent token "+  strToken + " -> " + progress_report + "%\r","Call() thread",false,0);
					
				}
				
			}

			if (blEncodeWasSuccess) {

				// Stop timer thread in ProgressReport() function.
				StopTimerThread();
				
				/************************************************************ 
				 * Send some report to the report server.
				 * 
				 * Length of audio file in millisecond, length of file size 
				 * on the disk in kilobyte, Total process time in ec2 in 
				 * millisecond.
				 * 
				 ************************************************************/
				if(AllowSuccessJobReport)
				{

					long lEndTime = System.currentTimeMillis();
				 
					long difference = lEndTime - lStartTime;
				 
					File file = new File(strOutputFile);
					
					double bytes = file.length();
					double kilobytes = (bytes / 1024);
					
					FinishSuccessJobReport(",#," + String.valueOf(totalSecs) + "," + String.valueOf(kilobytes) + "," + String.valueOf(difference));
				}			

				// Move file to S3 "mydotcom-encoded" bucket.
				if(!MoveFileS3Bucket())
					throw new RuntimeException(EncoerThreadConsts._ERR_MoveFileToBucket);

				// Put and report the finishing of encoding in the DB.
				progress_report = "finished";
			
				if(!reportJobProgressMethod(progress_report,EncoerThreadConsts.EncStage_EncodeFinishedSuccess))
					throw new RuntimeException(EncoerThreadConsts._ERR_CanNotAccessSwapFile);
				
				return strToken;
			}
			else{

        		// Informs Encoder program an error happened.
				UnfinishUnsuccessJobReport(strToken,false);
				return "NIL_ENCODE_" + strToken;
				
			}

		} catch (Exception e) {
			StopTimerThread();
			
			DebugMessage.ShowErrors("EncoderThread()", e.getMessage(), "",true);

			
			if((e.getMessage()==EncoerThreadConsts._ERR_MoveFileToBucket) || (e.getMessage()==EncoerThreadConsts._ERR_DropTmpFile))
			{
				  // Informs Encoder program an error happened.
				UnfinishUnsuccessJobReport(strToken,true);
    	    	return "NIL_SYSTEM_ERROR";	
			}
			else if(e.getMessage()==EncoerThreadConsts._ERR_CouldNotParseDuration)
			{
				// Informs Encoder program an error happened.
				UnfinishUnsuccessJobReport(strToken,false);
				return "NIL_ENCODE_" + strToken;
			}		
			
			return "NIL_SYSTEM_ERROR";
		}
		

	}

	/**
	 * We update all progress of encoding job in a file with the name of token.
	 * 
	 * 
	 * @param StageOfProgress
	 * @return
	 */
	private boolean reportJobProgressMethod(String StageOfProgress,int iCurrent_Process){
		try{
			EncodingThreadsReport.getInstance().setEncodeJobProgressReport(strToken, String.valueOf(PostgersDBID), StageOfProgress, String.valueOf(iCurrent_Process));
			return true; 
		} catch(Exception e) {
		
			return false;
		}

	}

	/**
	 * Checks if we had 10% more in progress update the database (This is for avoiding the overload on database by a lot of requests by the threads).
	 * 
	 * @param String LastProgress
	 * @param String NowProgress
	 * @return boolean (true if over 10 percent false if not)
	 */
		private boolean CompareLastAndNowProgress(String LastProgress,String NowProgress){
			
			/*
			 * Note: Nader check the below code in production environment and see the result. If it has overhead drop it. 
			 */
			// if conversion passed the 95%
			//if(Integer.parseInt(NowProgress) >= 95)
			//	return true;
			
			if((Integer.parseInt(LastProgress) + dbRequestSkipFactor) >= Integer.parseInt(NowProgress))
				return true;
			else
				return false;
		}
		
		/**
		 * Progress report thread to user.
		 */
		private void ProgressReport() {

			progressReportThread.scheduleAtFixedRate(
					new Runnable() {
						public void run() {
							
							old_progress_report = progress_report;
					
							// Check if error happened in the conversion process (the report in not digit).
							if(!progress_report.matches("[0-9]+"))
								return;
							
							/**
							 *  Checks if we had 10% more in progress update the database 
							 *  (This is for avoiding the overload on database by a lot of requests by the threads).
							 */
							if(CompareLastAndNowProgress(old_progress_report,progress_report))
							{

								reportJobProgressMethod(progress_report, EncoerThreadConsts.EncStage_EncodeIsProgressing);
									
							}

						}
					}, 0, CoversionReportSquenceInSeconds,TimeUnit.SECONDS);

		}
		
	/**
	 * 	Move converted file from temp encoding 
	 * 	folder in this machine to s3 bucket.
	 * 
	 * @return
	 */
	private boolean MoveFileS3Bucket() {
		
		try{
			
			File file = new File(strOutputFile);
			String key =  file.getName();
			
			s3.putObject(new PutObjectRequest(config.getProperty("OutPutBucket"), key, file));

			if(!DropTempEncodingFile())
				throw new RuntimeException(EncoerThreadConsts._ERR_CouldNotParseDuration);

			return true;
		}
		catch (Exception e)
		{
			DebugMessage.ShowErrors("MoveFileS3Bucket()", e.getMessage(), "",true);
			return false;
		}
		
	}

	/**
	 * Stop the thread of this class (progress report thread).
	 * 
	 */
	private void StopTimerThread() {
		if (!progressReportThread.isShutdown()) {
			progressReportThread.shutdownNow();
		}
	}

	/**
	 * Drop the temp encoded file from the temporary 
	 * folder of this machine (Garbage collection).
	 * @return
	 */
	private boolean DropTempEncodingFile() {

		try{
			
			DebugMessage.Debug("Droping file: " + strOutputFile,"DropTempEncodingFile",false,0);
		
		File fileTemp = new File(strOutputFile);
		
		if (fileTemp.exists()) {
			fileTemp.delete();
		}
		
		return true;
		
		}
		
		catch (Exception e){
			
			DebugMessage.ShowErrors("DropTempEncodingFile()", e.getMessage(), "",true);
			
			return false;
		}

	}

	/**
	 * Make the string of progress report from the percentage of grows.
	 * 
	 * @param str
	 * @return
	 */
	public static String ProgressPercentage(String str) {
		Pattern patt = Pattern.compile("(?<=time=)[\\d.]*");
		Matcher m = patt.matcher(str);
		boolean blFind = m.find();
		if (blFind)
			return m.group();
		else
			return null;
	}
	
	/**
	 * Generate a token for adding to the output file name for the test 
	 * (in order to avoid name conflict in the output) in the production
	 * it is useless.
	 *  
	 * @param len
	 * @return
	 */
	public static String generateToken(int len) {

		String ALPHA_NUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

		StringBuffer sb = new StringBuffer(len);
		for (int i = 0; i < len; i++) {
		int ndx = (int) (Math.random() * ALPHA_NUM.length());
		sb.append(ALPHA_NUM.charAt(ndx));
		}

		return sb.toString();

	}
}
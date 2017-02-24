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
/**
 * This is a SINGLETON class to report progresses and errors from encoding threads.
 * It has just one instance in the memory. 
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class EncodingThreadsReport {

	  private HashMap <String,String[]> EncodeJobListToReport = new HashMap<String,String[]>();

	  private static EncodingThreadsReport instance = null;
	  
	  /**
	   * Constructor
	   *  
	   */
	  private void EncodingThreadsReport(){

	  }
	  
	  /**
	   * Get the instance of singleton class.
	   *  
	   * @return instance of class.
	   */
	  public static EncodingThreadsReport getInstance(){
	    if(instance==null){
	       instance = new EncodingThreadsReport();
	      }
	      return instance;
	  }
	  
	  /**
	   * Create report structure in a hash map.
	   *  
	   * @param strToken
	   * @param PostgersDBID
	   * @param StageOfProgress
	   * @param iCurrent_Process
	   */
	  public void setEncodeJobProgressReport(String strToken, String PostgersDBID, String StageOfProgress, String iCurrent_Process){

		  String [] arrReportStruct = {strToken, PostgersDBID, StageOfProgress, iCurrent_Process};
		  EncodeJobListToReport.put(strToken,arrReportStruct);
	  }

	  /**
	   * Gets all of threads jobs report and convert them to a sql query string for reporting to the database. 
	   * All of reports here become consolidated as one query to avoid overhead and save the DB server bandwidth.
	   *   
	   * @return
	   */
	  
	  public String getEncodeJobProgressReport(boolean blEmergencyUpdate){

		  //Example of structure to be made and send as sql query
		  
 /*
    UPDATE categories
		   
    SET display_order = CASE id
        WHEN 1 THEN 3
        WHEN 2 THEN 4
        WHEN 3 THEN 5
    END,
    title = CASE id
        WHEN 1 THEN 'New Title 1'
        WHEN 2 THEN 'New Title 2'
        WHEN 3 THEN 'New Title 3'
    END
WHERE id IN (1,2,3)
*/
try{	
	  if(EncodeJobListToReport.isEmpty())
		  {
			  return null;
		  }  
		  
			Map <String,String[]>  map = EncodeJobListToReport;

			String strProgress_report = " progress_report = CASE id ";

			String strCurrent_process = " current_process = CASE id ";
			
			String arrIDs="";
			
			ArrayList<String> FinishedJobsToRemoveForEver = new ArrayList<String>();

			for (String[] value : map.values()) {

				if(!blEmergencyUpdate)
				{
				/*
				 * Building SQL query 
				 */
					strProgress_report = strProgress_report + "WHEN " + value[1] + " THEN '" + value[2] + "' "; 
					strCurrent_process  = strCurrent_process + "WHEN " + value[1] + " THEN " + value[3] + " ";
					arrIDs = arrIDs + value[1] + ",";
					
					
					/*
					 *  Check if the job is finished add it to garbage collection list to remove it from EncodeJobListToReport (main list in EncodingThreadsReport class).
					 *  Because after many conversion jobs the list occupy memory in vain.
					 *  
					 */
					if(value[2]=="finished" || value[2]=="encoderr")
					 {
						//System.out.println("Add some garbage collection in EncodingThreadsReport class for: " + value[2] );
						FinishedJobsToRemoveForEver.add(value[0]);
					 }
					
				}else{

					/*
					 * Building SQL query 
					 */
						strProgress_report = strProgress_report + "WHEN " + value[1] + " THEN 'serverr' "; 
						strCurrent_process  = strCurrent_process + "WHEN " + value[1] + " THEN -2 ";
						arrIDs = arrIDs + value[1] + ",";
				}
					
			}
			
			String strSqlQuery= "update upload_sessions set ";  
			
			strSqlQuery = strSqlQuery + strProgress_report + "END,";

			strSqlQuery = strSqlQuery + strCurrent_process + "END";

			// removing the last occurrence "," like ""1,2,3,4," character from the id array to have "1,2,3,4" .
			arrIDs = arrIDs.substring(0, arrIDs.length()-1);
			arrIDs = " WHERE id IN (" + arrIDs + ");";		
			
			strSqlQuery = strSqlQuery + arrIDs;

			// if is not emergency
			if(!blEmergencyUpdate)
			{
				//Remove garbage keys.			
				if(FinishedJobsToRemoveForEver.size()>0)
				{	
					for(int i=0 ; i < FinishedJobsToRemoveForEver.size(); i++)
						EncodeJobListToReport.remove(FinishedJobsToRemoveForEver.get(i));
				}

			}else{
				
				EncodeJobListToReport.clear();
				
			}
			return strSqlQuery;
					

	  }catch (Exception e){
		  
		  System.out.println(e.getLocalizedMessage());
		  return null;
	  }
	  
	  }
}
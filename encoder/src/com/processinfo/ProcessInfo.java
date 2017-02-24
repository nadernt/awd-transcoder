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
 * Prepares some information from the operating system for encoder program.
 * The type of operating system and version. The number of specific process 
 * in the operating system and
 *  
 */
package com.processinfo;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessInfo {

	public enum Platform {
		Windows,
		Mac,
		Unix,
		Solaris,
		unsupported
	}
	
	private static Platform m_os = null;
	 
	/**
	 * Get the operating information and version.
	 *   
	 * @return
	 */
	public static Platform getOS() {
		if(m_os == null) {
			String os = System.getProperty("os.name").toLowerCase();
 
			 m_os = Platform.unsupported;
			if(os.indexOf("win")   >= 0) m_os = Platform.Windows;		// Windows
			if(os.indexOf("mac")   >= 0) m_os = Platform.Mac;			// Mac
			if(os.indexOf("nux")   >= 0) m_os = Platform.Unix;			// Linux
			if(os.indexOf("nix")   >= 0) m_os = Platform.Unix;			// Unix
			if(os.indexOf("sunos") >= 0) m_os = Platform.Solaris;		// Solaris
		}
 
		return m_os;
	}
	
	public static boolean isWindows() {
		return (getOS() == Platform.Windows);
	}
	
 	public static boolean isMac() {
		return (getOS() == Platform.Mac);
	}
 	public static boolean isUnix() {
		return (getOS() == Platform.Unix);
	}
 	public static boolean isSolaris() {
		return (getOS() == Platform.Solaris);
	}

 	/**
 	 * Get number of running instances of an executable in the OS (e.g FFMPEG).
 	 * 
 	 * @param ExecutableProgramName
 	 * @return
 	 */
 	public int GetProgramRunningInstances(String ExecutableProgramName){
 		
 		String strCmdOutPut=null;  
 		String [] strCmsToShell;

 		if(isWindows())
 		{
 			strCmdOutPut  = ExcuteCommand(new String []{"tasklist" , "/fi", "\"imagename eq " + ExecutableProgramName + ".exe\""});
 		}
 		else{
 			strCmdOutPut  = ExcuteCommand(new String []{"/bin/sh","-c", "ps cax | grep " + ExecutableProgramName});
 		}
 		
 		if(strCmdOutPut==null)
 			return -1;
 		else
 			return NumberOfOccurences(strCmdOutPut,ExecutableProgramName);
 	}

/**
 * converts the number of process in the returned stream by the OS to GetProgramRunningInstances() method to an integer. 	
 * @param strContent
 * @param strFindInContent
 * @return
 */
private  int NumberOfOccurences(String strContent, String strFindInContent ){

 		int lastIndex = 0;
 		int count =0;

 		while(lastIndex != -1){

 		       lastIndex = strContent.indexOf(strFindInContent,lastIndex);

 		       if( lastIndex != -1){
 		             count ++;
 		             lastIndex+=strFindInContent.length();
 		      }
 		}
 	
 		return count; 		
}

 	
 	/**
 	 * Runs an executable in OS.
 	 * 
 	 * @param CommandToRun
 	 * @return
 	 */
 	private String ExcuteCommand(String [] CommandToRun){
		try {
			
	 	    java.lang.Runtime rt = java.lang.Runtime.getRuntime();
	 	    java.lang.Process p;

	 	    p = rt.exec(CommandToRun);
	 	    p.waitFor();
//p.
	 	    java.io.InputStream is = p.getInputStream();
	 	   
	        java.io.BufferedReader reader = new java.io.BufferedReader(new InputStreamReader(is));
	        
	        String s = null;
	        String strReturn = "";
	       
	        while ((s = reader.readLine()) != null) {
	        	strReturn +=s;
	        }
	        
	        is.close();
	        
	        return strReturn;
	        
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
 	}
 	
}

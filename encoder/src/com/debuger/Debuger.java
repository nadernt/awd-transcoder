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
package com.debuger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Debuger {

	private static boolean debugenable;
	private static boolean globalTimeStampForErrors=false;
	private static boolean globalTimeStampForDebugs=false;
	private static boolean globalTimeStampForInfos=false;
	
	public Debuger(boolean debugenable){
		this.debugenable= debugenable;
	}
	
	public void SetGlobalTimeStampForErrors(boolean state){
		globalTimeStampForErrors =state;
	}

	public void SetGlobalTimeStampDebugMsgs(boolean state){
		globalTimeStampForDebugs=state;
	}

	public void SetGlobalTimeStampForInfoMsg(boolean state){
		globalTimeStampForInfos = state;
	}

	/**
	 * Debug informations
	 * @param message String message to be printed.
	 * @param debuglevel Level of debug info <br/>
	 * 			0 print if <b>debug mode enabled </b> is enable.<br/>
	 * 			1 print if <b>debug mode enabled</b> is enable or disable (in any case print).
	 */
	public void Debug(String message,String methodName,boolean addtimestamp, int debuglevel){
		
		
		if(methodName.length()>0)
			methodName = " In '" + methodName + "' method.";
		
		String TimeStamp="";
		
		if(addtimestamp || globalTimeStampForDebugs)
		{
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			TimeStamp = dateFormat.format(date);
			TimeStamp = "[" + TimeStamp + " ] ";
			
		}

		
		String outMessage= "Debug-> " + message + "." + methodName; 
		
		if(debuglevel==0 && debugenable==true){
			System.out.println(TimeStamp + outMessage);
		}
		else if(debuglevel==0 && debugenable==false){
			;
		}
		else if (debuglevel==1){
			System.out.println(TimeStamp + outMessage);
		}
		else{
			System.out.println("Your arguments for debuger mmethod are not correct.");
		}
		
	}
	
	/**
	 * 
	 * @param InfoToShow
	 * @param addtimestamp
	 */
	public void ShowInfo(String InfoToShow , boolean addtimestamp){

		String TimeStamp="";
		
		if(addtimestamp || globalTimeStampForInfos)
		{
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			TimeStamp = dateFormat.format(date);
			TimeStamp = "[" + TimeStamp + " ] ";
			
		}
		
		System.out.println(TimeStamp + "Info-> " + InfoToShow);
	}
	
	public String ShowErrors(String methodName, String exceptionErrorMessage, String message, boolean addtimestamp){
		
		String TimeStamp="";
		
		if(addtimestamp || globalTimeStampForErrors)
		{
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			TimeStamp = dateFormat.format(date);
			TimeStamp = "[" + TimeStamp + " ] ";
			
		}

		if(methodName.length()>0)
			methodName = "In " + methodName + ". ";
		
		if(exceptionErrorMessage.length()>0)
			exceptionErrorMessage = "Reason: " + exceptionErrorMessage + ". ";
		 
		String outMessage= "Error-> " + methodName + exceptionErrorMessage + message; 
			System.out.println(TimeStamp + outMessage);	
		return outMessage;
	}
	
	public boolean GetDebugModeIsEnable(){
		
		return debugenable;
	}
}

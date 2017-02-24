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
package com.authentication;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.Timestamp;

import com.sha256.SHA_256;

/**
 * 
 * @author nader
 *
 */
public class Authentication {
	private static SecureRandom random = new SecureRandom();

	private static int CurrentAuthenticationStstus=0;

	private static String strAminUserName;
	private static String strAdminPasswordHash;
	private static boolean Session;
	private static String strAdminSessionValidInMiunutes;
	private static String AuthToken;
	private static Timestamp userLoginTime;
	private static Authentication instance = null;


	private Authentication(){}

	/**
	 * Initialize authentication class.
	 * 
	 * @param AdminUserName
	 * @param AdminPasswordHash
	 * @param AdminSessionValidTimeInMinut
	 */
	public void setAuthenticationInitials(String AdminUserName,String AdminPasswordHash,String AdminSessionValidTimeInMinut){

		this.strAminUserName=AdminUserName;
		this.strAdminPasswordHash= AdminPasswordHash;
		this.strAdminSessionValidInMiunutes = AdminSessionValidTimeInMinut;
	}

	/**
	 * Single tone class runner function. Examines class has loaded or not.  
	 * 
	 * 
	 * @param strUserName
	 * @param strPassword
	 * @param strSessionValidInMiunutes
	 * @return
	 */
	public static Authentication getInstance(){
		if(instance==null){
			instance = new Authentication();
		}
		return instance;
	}

	/**
	 * User login to Authentication class.
	 * 	
	 * @param strUserName
	 * @param strPassword
	 * @return
	 */
	public String LogIn(String strUserName, String strPassword){

		try {

			if(strUserName.length()==0 || strPassword.length()==0)
			{	userLoginTime=null;
			Session=false;
			AuthToken="";
			return "wrong_userpass";
			}

			Session=false;
			AuthToken="";
			userLoginTime=null;

			if(strAminUserName.equals(strUserName) && strAdminPasswordHash.equals(new SHA_256().get_SHA(strPassword)))
			{
				java.util.Date date= new java.util.Date();

				userLoginTime = new Timestamp(date.getTime());

				AuthToken = generateToken();
				Session=true;
				return AuthToken;
			}
			else
			{
				userLoginTime=null;
				Session=false;
				AuthToken="";
				return "wrong_userpass";
			}

		} catch (Exception e) {
			System.out.println(e.getMessage());
			return "-1";
		}

	}	

	/**
	 * User logout from Authentication class.
	 * 
	 * @return
	 */
	public int LogOut(){
		userLoginTime=null;

		Session=false;
		AuthToken="";
		return 0;
	}

	/**
	 * REturns current authentication state (login or logout)
	 * 
	 * @param strToken
	 * @return
	 */
	public boolean getAuthentication(String strToken){

		if (strToken.length()==0)
		{
			LogOut();
			return false;	
		}
		if(AuthToken.equals(strToken) && getSessionTime(userLoginTime))
		{
			java.util.Date date= new java.util.Date();
			userLoginTime = new Timestamp(date.getTime());
			Session=true;
			return true;
		}
		else 
		{
			LogOut();
			return false;
		}

	}

	/**
	 * Check current session for login user.
	 * 
	 * @param tStampLogin
	 * @return
	 */
	private boolean getSessionTime(Timestamp tStampLogin){

		java.util.Date date= new java.util.Date();

		Timestamp tStampNow = new Timestamp(date.getTime());
		
		long diff = (tStampNow.getTime() - tStampLogin.getTime());
		
		//System.out.println((diff / (1000 * 60)) + strAdminSessionValidInMiunutes);
		
		if((diff / (1000 * 60))>Long.parseLong(strAdminSessionValidInMiunutes))
			return false;
		else
			return true;
	}


	/**
	 * Generate String token for security hash.
	 * 
	 * 
	 * @return
	 */
	private static String generateToken() {

		String strToken = new BigInteger(130, random).toString(32);
		return strToken;

	}

}

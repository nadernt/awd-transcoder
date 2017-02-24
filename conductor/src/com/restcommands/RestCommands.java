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
package com.restcommands;

import java.math.BigInteger;

import javax.swing.text.View;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;

import com.SQL.SQL;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import java.security.SecureRandom;

public class RestCommands {


	private static ClientConfig RestfulConfig = new DefaultClientConfig();	
	private static SecureRandom random = new SecureRandom();
	private static String ServerResultAnswer=null;
	private static int ServerHeaderAnswer=0;

	public static String generateToken() {

		try{
			String strToken = new BigInteger(130, random).toString(32);

			return strToken;
		}
		catch(Exception e)
		{
			System.out.println(e.getMessage());
			return null;
		}
		//	return null;
	}

	/**
	 * Returning list:
	 * -2 : Error-> token generation was not success.
	 * -1 : Error-> connection refused by the target url.
	 *  1 : OK-> target answered with 200 OK header.
	 */
	public static int restCommands(String strURL, String [] strPostElement,String [] strPostValue,boolean blIsSendToken){

		try{

			ServerResultAnswer=null;
			ServerHeaderAnswer=0;

			String strToken=null; 

			// if needed token create one.
			if(blIsSendToken)
			{
				strToken = generateToken();
				// Generating token was not successful. Most of the times db access is problem!
				if(strToken==null)
					return -2;
			}

			Client client = Client.create(RestfulConfig);
			
			WebResource webResource = client.resource(UriBuilder.fromUri(strURL).build());
			
			MultivaluedMap formData = new MultivaluedMapImpl();

			for(int i=0; i < strPostElement.length ; i++)
			{
				formData.add(strPostElement[i], strPostValue[i]);
			}

			if(blIsSendToken)
				formData.add("token", strToken);

			ClientResponse response = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).post(ClientResponse.class, formData);

			ServerResultAnswer = response.getEntity(String.class);
			ServerHeaderAnswer=response.getStatus();

			/* This trick makes our work simple and we do not need to call another method to check the header was 200 ok 
		 	except this function does its normal work. */
			if(ServerHeaderAnswer==200)
				return 200;

			// If result from the server is ok but it is another thing (please read above comment). 
			return 1;
		}
		catch (Exception e){
			System.out.println(e.getMessage());
			return -1;

		}
		//if(response.getStatus()!=200)

		//	System.out.println("Response " + response.getEntity(String.class) + "=== " + response.getStatus());		

	}

	/**
	 * Sends a post to specific url and get the result.
	 *  
	 * @param strURL
	 * @param strPostElement
	 * @param strPostValue
	 * @return
	 */
	public static int HTTPCommandsFullResponse(String strURL, String [] strPostElement,String [] strPostValue,int intTimeOut){

		ServerResultAnswer=null;
		ServerHeaderAnswer=0;

		try{


			Client client = Client.create(RestfulConfig);

			// If intTimeOut = -1 then we have a value for the timeout
			if(intTimeOut!=-1)
				client.setConnectTimeout(intTimeOut);

			WebResource webResource = client.resource(UriBuilder.fromUri(strURL).build());
			MultivaluedMap formData = new MultivaluedMapImpl();

			for(int i=0; i < strPostElement.length ; i++)
			{
				formData.add(strPostElement[i], strPostValue[i]);
			}


			ClientResponse response = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).post(ClientResponse.class, formData);

			ServerHeaderAnswer=response.getStatus();

			ServerResultAnswer = response.getEntity(String.class);

			return ServerHeaderAnswer;
		}
		catch (Exception e){

			return ServerHeaderAnswer;

		}
		//if(response.getStatus()!=200)

		//	System.out.println("Response " + response.getEntity(String.class) + "=== " + response.getStatus());		

	}

	/**
	 * Response http header from remote address.
	 * 
	 * @return
	 */
	public static int ServerHeader(){
		return ServerHeaderAnswer;
	}

	/**
	 * Response http body result from remote address.
	 * 
	 * @return
	 */
	public static String ServerResult(){

		return ServerResultAnswer;
	}

}

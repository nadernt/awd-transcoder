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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
//import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
//import javax.ws.rs.core.Response.ResponseBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import com.authentication.Authentication;

@Path("/restreport")
public class RestReport extends Conductor {

/**
 * Rest command API parser.
 *  
 * @param REST_API : name of api from client side.
 * @param FROM_CLIEN_OPTIONS : option sent from client for api
 * @return
 */
private JSONObject api_analyzer(String REST_API,String FROM_CLIEN_OPTIONS){
	
	JSONObject jsonObject = new JSONObject();
	
	try{
	
		if(REST_API.equals("conductor_status"))
		{
			jsonObject.put("error", false);
			jsonObject.put("message", "alive");
			jsonObject.put("code", 200);
		}
		else if (REST_API.equals("service_shutdown")){
			jsonObject.put("error", false);
			jsonObject.put("message", "");
			jsonObject.put("code", 200);
		}
		else if (REST_API.equals("service_status")){
			jsonObject.put("error", false);
			jsonObject.put("message", "");
			jsonObject.put("code", 200);
		}
		else if (REST_API.equals("servers_in_region")){
			System.out.println("I am in servers_in_region");
			jsonObject.put("error", false);
			jsonObject.put("message", restGetServersInfo(""));
			jsonObject.put("code", 200);
		}
		else if (REST_API.equals("servers_in_securitygroup")){
			System.out.println("I am in servers_in_securitygroup");
			jsonObject.put("error", false);
			jsonObject.put("message",restGetServersInfo(FROM_CLIEN_OPTIONS));
			jsonObject.put("code", 200);
		}
		else
		{
			jsonObject.put("error", true);
			jsonObject.put("message", "Unrecognize request");
			jsonObject.put("code", 214);
		}
	
	}
	catch (Exception e){
		
	}

	return jsonObject;
}
 
/**
 * Gets the post command and answers in JSON format.
 * 
 * @param token
 * @param REST_API
 * @param FROM_CLIEN_OPTIONS
 * @return
 * @throws Exception
 */
    @POST
    @Path("/api_post")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response api_post(@FormParam("token") String token,@FormParam("rest_api") String REST_API,@FormParam("from_client_options") String FROM_CLIEN_OPTIONS) throws Exception {
       	
    	DebugMessage.Debug("token->" + token + ", rest_api->" + REST_API + ", from_client_options->" + FROM_CLIEN_OPTIONS, "api_post()", false,0);
    	
    	JSONObject jsonObject = new JSONObject();
    	
    	if(Authentication.getInstance().getAuthentication(token))
    	{
    		jsonObject=	api_analyzer(REST_API,FROM_CLIEN_OPTIONS);
    		return Response.status(Response.Status.OK.getStatusCode()).entity(jsonObject.toString()).build();
    	}
    	else
    	{
    		jsonObject.put("error", true);
    		jsonObject.put("message", "Bad Authentication data");
    		jsonObject.put("code", 215);
    		
        	return Response.status(Response.Status.UNAUTHORIZED.getStatusCode()).entity(jsonObject.toString()).build();
    		
    	}
    	
    	
    }

    /**
     * Login to Admin's rest panel.
     *  
     * @param username
     * @param password
     * @return
     * @throws Exception
     */
    
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(@FormParam("username") String username, @FormParam("password") String password ) throws Exception{
    	
    	System.out.println(username + " " + password);
    	
    	String strAuthToken = Authentication.getInstance().LogIn(username, password);

    	JSONObject jsonObject = new JSONObject();
    	

		    	if(!strAuthToken.equals("wrong_userpass") && !strAuthToken.equals("-1"))
		    	{
		    		jsonObject.put("error", false);
		    		jsonObject.put("message", "Username and Password Acepted");
		    		jsonObject.put("token", strAuthToken);
		    		jsonObject.put("code", 200);
		    		return Response.status(Response.Status.OK.getStatusCode()).entity(jsonObject.toString()).build();
		    	}
		    	else
		    	{
		    		jsonObject.put("error", true);
		    		jsonObject.put("message", "Bad Authentication data");
		    		jsonObject.put("code", 215);
		    		
		        	return Response.status(Response.Status.UNAUTHORIZED.getStatusCode()).entity(jsonObject.toString()).build();
		    	}
    	
   }
    
    /**
     * Logout from Admin's panel.
     *   
     * @return
     * @throws JSONException
     */
    
    @POST
    @Path("/logout")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces("application/json")
    public Response logout() throws JSONException{

    	Authentication.getInstance().LogOut();

    	JSONObject jsonObject = new JSONObject();

    	jsonObject.put("error", false);
   		jsonObject.put("message", "logout");
   		jsonObject.put("code", 201);
   		
   		return Response.status(Response.Status.OK.getStatusCode()).entity(jsonObject.toString()).build();
		
    }
  
    /**
     * Load a html template from web_template directory by the name of template
     * 
     * 
     * @param view_template
     * @param token
     * @return
     * @throws Exception
     */
    @GET
    @Path("/view/{view_template}")
    @Produces(MediaType.TEXT_HTML)
    public String testajax(@PathParam("view_template") String view_template,@PathParam("token") String token) throws Exception {
    	
    	Charset encoding=StandardCharsets.UTF_8;
    			
    	
			String pathViewTemplate  =  System.getProperty("user.dir") + "/web_template/" + view_template;
			byte[] encoded;
			encoded = Files.readAllBytes(Paths.get(pathViewTemplate));
						return encoding.decode(ByteBuffer.wrap(encoded)).toString();
    }

/*	public RestReport(){
	
	System.out.println("Trig");
     
//	Authentication.getInstance().getAuthentication(strAuthToken);
        
	JSONObject jsonObject = new JSONObject();
try {
    	  
		jsonObject.put("status", "alive");	
		jsonObject.put("status", "alive");
		
		ResponseBuilder builder = null;
        String response = "Custom message";
        builder = Response.status(Response.Status.UNAUTHORIZED).entity(response);
//	      String result = "@Produces(\"application/json\") Output: \n\nF to C Converter Output: \n\n" + jsonObject;
///		      Response.status(200).entity(result).build();	

        throw new WebApplicationException(builder.build());

	} catch (JSONException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}

      }*/    
}
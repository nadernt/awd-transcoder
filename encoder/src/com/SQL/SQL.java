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
package com.SQL;

import java.sql.*;

public class SQL {

	private static Connection conn = null;

	private static String driver = "org.postgresql.Driver";
	private static int lng_mysql_num_rows=0;
	private static boolean bl_QueryWasSuccess=true;
	
	// out connect method
	// in this method we basically connect to the db
	public static boolean connect(String host, String username, String password, String dbName) {
		
		boolean isConnect = false;
		
		// connection method
		try {
			Class.forName(driver);
			// this method will connect to db
			conn = (Connection) DriverManager.getConnection("jdbc:postgresql://"
					+ host, username, password);
			isConnect = true;
		} catch (Exception e) {
			System.out.println("Can not connect to database error: " + e.getMessage());
		}

		return isConnect;
	}

	// this method will fetch the result from db
	public static ResultSet select(String query) {
		
		set_queryWasSuccess(true);
		
		ResultSet result = null;

		lng_mysql_num_rows=0;
				
		try {
			// first we create the statement
			Statement s = (Statement) conn.createStatement (
					ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY) ;
			
			result = s.executeQuery(query);
			
			
			
			//ResultSetMetaData rsmd = (ResultSetMetaData) result.getMetaData();
			result.last();
			
			lng_mysql_num_rows = result.getRow(); //rsmd.getColumnCount();
			
			result.beforeFirst();
			

		} catch (Exception e) {
			System.out.println(e.getMessage() + ". In SQL calss Select function.");
			set_queryWasSuccess(false);
		}
		return result;
	}

	
	private static void set_queryWasSuccess(boolean blState){
	
		bl_QueryWasSuccess =blState;
	}
	
	
	public static boolean queryWasSuccess()
	{
		return bl_QueryWasSuccess;
	}
	
	// this method will display the result of select query in tabuler form
	public static void showSelect(ResultSet result) {
		if (result != null) {
			try {
				// this class will contain the all the basic information of
				// result
				ResultSetMetaData rsmd = (ResultSetMetaData) result
						.getMetaData();
				int noColumns = rsmd.getColumnCount();
				//we need some format
				for (int i = 0; i < noColumns; i++) {
					System.out.print(rsmd.getColumnName(i + 1) + "\t");
				}
				System.out.println();
				// now we display the results here
				while (result.next()) {
					//displaying the result
					for (int i = 0; i < noColumns; i++)
						System.out.print(result.getString(i+1) + "\t");
					System.out.println();
				}
			} catch (Exception e) {
				System.out.println("Boba");
			}
		}
	}
	
	public static int mysql_num_rows()
	{
		return lng_mysql_num_rows;
	
	}
	
	//for any qyery qhich is not select
	//ie alter, create, insert, update
	public static int query(String query) {
		
		set_queryWasSuccess(true);
		
		int result = -1;
		
		try {
		
			//first ew create the statement
		Statement s = (Statement) conn.createStatement();
		
		//result will be the no. rows that will be updates because of query
		result = s.executeUpdate(query);
		
		} catch (Exception e) {

			System.out.println(e.getMessage());
			set_queryWasSuccess(false);
	
		}
		
		return result;
	}
	
	// method for disconnect purpose
	// only call if we are connected
	public static void disconnect() {
		try {
			conn.close();
		} catch (Exception e) {
		}
	}
}

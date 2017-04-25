//General java modules
import java.io.FileWriter;

import java.io.IOException;
import java.io.File;
import java.util.Date;
import java.util.Calendar;
import java.util.regex.*;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;			//  C2393

//db related modules
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSetMetaData;
import java.sql.Time;
import java.sql.Timestamp;
//XML related modules
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.*;
import org.xml.sax.*;
//Custom modules
import lib.lenovo.XMLErrorHandler;
import lib.lenovo.SubReport;   //  C2393

import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.*;

/**
* <b>Application:</b> Lenovo Service Reporting Extract
*
* <p><b>Version: 1.2 </b>
*
* <p><b>Description:</b>
* <br>
* This application exports data from a database into CSV files for reporting.  One of the command
* line arguments for this application is an XML file name.  This XML file is used to determine
* the dataset from the database to pull as well as the file name that the report will be saved in.
* This application has a few features that will optionally allow date formatting and or the
* creation of DB2 column headers.
*
* <p><b>Java Ver:	1.4</b>
*
* <p><b>JDBC Driver:</b>
* <br>
* DB2 Universal JDBC Driver packaged with DB2 8.1.  This driver is currently setup with
* a Type 4 Architecture.  To connect using the	Type 4 Architecture use the following
* sample connection URL which has the host name and db2 port number.  This Architecture
* will be slower because it uses TCP/IP but will be necessary if this application
* is not hosted on the same machine as DB2.
* <br><br>
* Sample Type 4 DB2 8.1 driver
* <br>
* private static final String CONNECTION_URL = "jdbc:db2:/hostname/:50000/LENOVO ";
* <br>
* DB2 Universal JDBC Driver packaged with DB2 7.2.  This driver is currently setup with
* a Type 2 Architecture to take advantage of local connectivity without using TCP/IP.  This
* is the minimum db2 datbase and driver that will work with this application.  Use the
* connection URL below.  To use this driver you must manually execute -> usejdbc2.bat in
* the SQLLIB\java12  directory on the db2 server
* <br><br>
*
* private static final String CONNECTION_DRIVER = "COM.ibm.db2.jdbc.app.DB2Driver";
* <br>
*
* <p><b>Author:</b> Victor Monaco  January, 2006 - Millennium IT
* <br><b>Author:</b> Ka-lai Wong January, 2006 - IBM
*
* <p><b>Notes:</b>
* <br>
* This application has been tested with DB2 version 7.x and 8.x.  The only thing that needs
* to be changed is the JDBC driver URL as mentioned above
* <br>
* <br>---------------------------------------------------------------------------------------
* <br><b>Modification History</b>
* <br>---------------------------------------------------------------------------------------
* <br>January 2006 - First Release 1.0
*
* <br>March 2006 - Post UAT Change Request C2246
*
* <br>April 2006 - C2309 - Added a carriage return to end of report trailer in generateFooter routine
* <br>June 2006 - C2393 - Added ability to create a single report which can contain one or more subreports.
* <br>Oct 2006 - CRXXX LSR2 Move to GSA Server:  Added new connection driver Type 4 and properties
*     file to load application parameters.
* <br>Jun 2008 - C3425 - Add "Totals:" to Summary report  
* <br>Mar 2009 - C3771 - Changed KEY value to RSEQ in SQL statement    
* <br>July 07 2009 MM - C3905 - Change LSRExtract.java custom DB2 header
* <br>2009-03-18 | MM | Version 1.5 - C4108 
* <br>	- added one arguemnt to specify the database it connects to
* <br>	- added code read database connection information from connections.xml	
* <br> 2011-11-16 Mark Ma C4438 added Excel support, fileType can be csv (default) or xls 	
* <br> 2013-04-26 Sakthivel Palanisamy C4785 remove credential when executing application
* <br> 2016-06-15 Vijayadurga N Samy C5150,C5152 Upgrade LSRExtract program to be compatible with Java 6 and Modify LSRExtract program to allow connection MSSQL database							
*/

public class LSRExtract {
	private static final String PROGRAM = "Lenovo Service Reporting Extract";
	private static final String VERSION = "1.6";    // C3771 C4108 C4785

	//  OS Independent new line character
	private static final String NEWLINE = System.getProperty("line.separator");

	//  Working directory of the application
	private static final String EXPORT_DIR = System.getProperty("user.dir") + "/file_io/lsr_extract";
	//  Directory where XML report files are stored
	private static final String XML_REPORTS_DIR = System.getProperty("user.dir") + "/file_io/lsr_extract/xml_reports";

	//  Default date, time  and timestamp format strings
	private static final String DATE_EXPORT_FORMAT = "yyyyMMdd";
	private static final String TIME_EXPORT_FORMAT = "H.m.s";
	private static final String TIMESTAMP_EXPORT_FORMAT = "yyyy-MM-dd-HH.mm.ss";

	//  Custom report header default number formatting strings
	//  C2246 Changed from "000000" to "0000000"
	private static final String REPORT_HDR_NUMBER_FORMAT = "0000000";
	private static final String REPORT_TRL_NUMBER_FORMAT = "0000000";

	//  Java DB2 connection driver for DB2 8.x  (Use one or the other 8.x or 7.2)
	//private static final String CONNECTION_DRIVER = "com.ibm.db2.jcc.DB2Driver";
	//  Java DB2 connection driver for DB2 7.2 and above (Use one or the other 8.x or 7.2)
	//  CRXXX LSR2 private static final String CONNECTION_DRIVER = "COM.ibm.db2.jdbc.app.DB2Driver";

	//  This connection URL can be used for 7.2 and 8.x
	//  CRXXX LSR2 private static final String CONNECTION_URL = "jdbc:db2:";

	//  CSV formatting parameters
	private static final String DEFAULT_SEPERATOR = ",";
	private static final String DOUBLE_QUOTE = "\"";

	//  XML nodes
	private static final String REPORT = "report";
	private static final String FILE_NAME = "fileName";
	private static final String FILE_TYPE = "fileType";
	private static final String DATE_FORMAT = "dateFormat";
	private static final String TIME_FORMAT = "timeFormat";
	private static final String SQL = "SQL";
	//C4630
	private static final String DELIMITER = "delimiter";
	// C2393 Begin
	private static final String MULTI_REPORT = "multiReport";
	private static final String SUB_REPORT = "subReport";
	// C2393 End
	
	private static final String EXPORTCSV = "csv";
	private static final String TITLE = "title";
	//  XML attributes
	private static final String INCLUDE_HEADER = "includeHeader";
	private static final String INCLUDE_TOTALS = "includeTotals";   //  C2393
	private static final String TEXT = "text";


	//  XML Header Values
	private static final String HEADER_NONE = "None";
	private static final String HEADER_DB2 = "Db2Header";
	private static final String HEADER_CUSTOM = "Custom";
	
	// C4108 
	private static String connection_driver = "";
	private static String server_host = "";
	private static String db2_port = "";
	private static String database_name = "";
	private static String server_type = "";
	/***************************************************************************************************
	*  NAME:  main
	*
	*  PURPOSE:  This is main program for the Lenovo Service Reporting Extract.  This application parses
	*  an XML report file.  For each Report node in the XML file, this application will create
	*  a corresponding CSV file(s) based on the SQL text in each report node.  Other XML options include
	*  the used of DB2 column header labels and or Date, Time, TimeStamp formatting.
	*
	*  INPUTS:  When running the main program, 3 command line arguments are required.  See
	*  validateCommandArgs for a full description of command line arguments.
	*
	*  OUTPUTS:  This program will produce 1 report in CSV format for every report node in the XML
	*  input file.  The file name of the report is also specified in the XML report node.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public static void main(String[] args) {
		Connection db2Conn;
		File xmlFile;
		String xmlPath;
		String resultString = "";
		int exitCode =0;
		//Properties sysProperties = new Properties();  //  CRXXX LSR2
		//  Validate any command line arguments that are needed to run this application
		if (validateCommandArgs(args)) {
			database_name = args[0];
			resultString = getDBinfo(database_name);
			if(resultString.equals(""))
			 {						
				String connection_url = "jdbc:" + server_type + "://" + server_host + ":" + db2_port + "/" + database_name;
				System.out.println("Connection URL: " + connection_url);
				//  Once the command line args are validated we try to establish a connection the database
				//db2Conn = connectDb(args[0], args[1], args[2]);
				//System.out.println("dbname:"+args[0]+",user:"+args[1]+", password:"+args[2]+", xml:"+args[3]+"."); //C4785
				db2Conn = connectDb(connection_driver, connection_url, args[1], args[2]);
				//  CRXXX LSR2 End
				System.out.println(PROGRAM + " [" + VERSION + "]" + NEWLINE);

				//  If the connection the the databaes is successful
				if (db2Conn != null) {
					System.out.println("Database Connection Established.");
					//  Pick up the XML file name from the command line arguments
					xmlPath = XML_REPORTS_DIR + "/" + args[3];   //  CRXXX LSR2
					//  Test to see if the file exists.
					xmlFile = new File(xmlPath);
					if (xmlFile.isFile()) {
						//  Parse the XML file for report nodes and process any reports found
						parseXMLReportFile(db2Conn, xmlPath);
					} else {
						System.out.println("Application Error: Report XML file not found: [" + xmlPath + "]");
						exitCode = 1;
					}

					//  Everything is done so close connections.
					closeDb(db2Conn);
					System.out.println("Database Connection Closed.");
				}else{
					exitCode = 1;
					
				}
			//  CRXXX LSR2 Begin
			}  else {				
				System.out.println("Application Error: There is a problem when parse connections.xml file.");
				System.out.println(resultString);
				exitCode = 1;
			}
			//  CRXXX LSR2 End
		}else{
			exitCode = 1;
		}
		System.exit(exitCode);
	}  //  Main Program End



	/***************************************************************************************************
	*  NAME:  validateCommandArgs
	*
	*  PURPOSE:  This routine is used to validate the command line parameters that are needed to run
	*  this application.  Currently, this application requires 3 parameters which are used to establish
	*  a database connection and to provide the path to the report XML configuration file.
	*
	*  INPUTS:  1.  String[] args - This routine receives the command line arguments in the string array
	*				args.  Three non null parameters are expected.  User ID, Password, and XML
	*				configuration File Name.
	*
	*  OUTPUTS:  True if exactly four non null parameters are input, False otherwise.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*  
	*  2009-03-18 | MM | C4108 add extra argument dbname: so there are 4 arguments in totlal:
	*  			dbname, userid, password, xmlfilename
	*
	***************************************************************************************************/
	private static boolean validateCommandArgs(String[] args) {
		boolean result;
		result = false;
		//  CRXXX LSR2 Begin
		//  Check is there are 4 non null parameters input in array args
		if (args.length == 4) {
			if (!args[0].equals("") && !args[1].equals("") && !args[2].equals("") && !args[3].equals("")) {
				 result = true;
			}
		}

		//  If the check above fails then print the required command line parameters to screen with
		//  an example.
		if (result == false) {
			System.out.println("Required Command Line Parameters");
			System.out.println("{databasename} {User ID} {Password} {Report XML File Name}" + NEWLINE);
			System.out.println("Example:  java -classpath db2jcc.jar;db2jcc_license_cu.jar LSRExtract.class LSRPROD SMITH ssd34fg reports.xml" + NEWLINE);
		}
		//  CRXXX LSR2 End

		return(result);
	}

	//C4108: we are using connections.xml, so the following methods are no longer used.
	
	//  CRXXX LSR2  New Method
	/***************************************************************************************************
	*  NAME:  loadSystemProperties
	*
	*  PURPOSE:  This routine is used to load application properties from a java properties file
	*			 located in the current directory.  The file it is expecting is called LSR.properties
	*			 Any system wide properties can be placed in this file.
	*
	*  INPUTS:  1.  Properties sysProperties - A new blank properties object
	*
	*  OUTPUTS:  The input properties object is loaded via file.
	*
	*  CREATED BY:  Victor Monaco  October, 2006 - Millennium IT
	*
	***************************************************************************************************/
	/*
	private static boolean loadSystemProperties(Properties sysProperties) {
		boolean result = true;

		try {
		 	sysProperties.load(new FileInputStream(SYSTEM_PROPERTIES));
	    } catch (IOException e) {
			result = false;
	    }

		return (result);
	}
	*/


	//  CRXXX LSR2  New Method
	/***************************************************************************************************
	*  NAME:  getSystemProperty
	*
	*  PURPOSE:  This is a simple utility routine to quickly return a value from the system properties
	*			 object that is loaded from file when the application starts.  Given a key name, return
	*			 the String value associated with that key.  If the key does not exist "" is returned.
	*
	*  INPUTS:  1.  Properties sysProperties - A properties object that is loaded with key value pairs
	*			2.  String prop - a key name
	*
	*  OUTPUTS:  The String value associated with prop
	*
	*  CREATED BY:  Victor Monaco  October, 2006 - Millennium IT
	*
	***************************************************************************************************/
	/*
	private static String getSystemProperty(Properties sysProperties, String prop) {

		String result = sysProperties.getProperty(prop, "");

		return result;
	}
	*/


	/***************************************************************************************************
	*  NAME:  connectDb
	*
	*  PURPOSE:  This routine is used to establish a database connection.  If the connection is not
	*  successful then null is returned as the connection object.  Connection exceptions are caught,
	*  packaged and displayed to the screen immediately.
	*
	*  N.B.  setReadOnly is set to True
	*
	*  INPUTS:  1.  String connection_driver -  JDBC Driver to be used
	*           2.  String connection_url - database connection url path
	*			3.  String user - User ID
	*			4.  String psw - User ID Password   {not encrypted}
	*
	*
	*  OUTPUTS:  Valid Java connection object or Null if the connection was not successful.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	//  CRXXX LSR2 private static Connection connectDb(String database, String user, String psw) {
	private static Connection connectDb(String connection_driver, String connection_url, String user, String psw) {

		Connection resultConn;
		String errMsg;

		resultConn = null;
		errMsg = "";

		//  Try to establish a connection using the input parameters
	    try {
			//  CRXXX LSR2 Begin
        	Class.forName(connection_driver);
        	//resultConn = DriverManager.getConnection(CONNECTION_URL + database, user,psw);
        	resultConn = DriverManager.getConnection(connection_url, user,psw);
        	//  CRXXX LSR2 End
        	resultConn.setReadOnly(true);
		}
		//  This will catch missing database drivers
		catch (ClassNotFoundException cnfe) {
			errMsg = errMsg + "Exception Message:  " + cnfe.getMessage() + NEWLINE;
			//  CRXXX LSR2 Begin
			errMsg = errMsg + "    Connection Driver -> " + connection_driver + NEWLINE;
			errMsg = errMsg + "    Connection URL    -> " + connection_url + NEWLINE;
			//  CRXXX LSR2 End
		}
		//  This will catch invalid connection parameters
		catch (SQLException sqle) {
			errMsg = errMsg + "Exception Message:  " + sqle.getMessage() + NEWLINE;
			//  CRXXX LSR2 Begin
			errMsg = errMsg + "    Connection Driver -> " + connection_driver + NEWLINE;
			errMsg = errMsg + "    Connection URL    -> " + connection_url + NEWLINE;
			//  CRXXX LSR2 End
		}

		//  If the connection was not established then print msg to screen
		if (resultConn == null) {
			System.out.println("Application Error:  Connection to database can not be established");
			if (!errMsg.equals("")) {
				System.out.println(errMsg);
			}
		}

		return(resultConn);
	}



	/***************************************************************************************************
	*  NAME:  closeDb
	*
	*  PURPOSE:  This routine simply closes the database connection.  If an exception is encountered
	*  then the message is output to screen.
	*
	*  INPUTS:  1.  Connection con - The connection that will be closed.
	*
	*  OUTPUTS:  True if the database was successfully closed, False otherwise.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	private static boolean closeDb(Connection con) {
		boolean result;

		result = true;

	    try {
        	con.close();
		}
		catch (SQLException sqle) {
			System.out.println("Application Error:  Database connection can not be Closed");
			System.out.println("Exception Message:  " + sqle.getMessage());
			result = false;
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  createCSVReport
	*
	*  PURPOSE:  This method is responsible for the creation of the CSV report.  It receives all of the
	*  information that is required to create the report.  It begins by executing the SQL statement and
	*  storing the results in the result set.  The next step is to create the optional header for the
	*  report.  The result set is parsed row by row and column by column.  Since we are creating CSV
	*  files, we need to ensure that the report conforms to CSV format.  This requires parsing each
	*  column and escaping any special characters.  It also requires placing quotes around certain types
	*  of data and placing commas between columns.  There are 14 different types of db2 data.  Some of
	*  them require quotes and some do not.  In general, numbers and dates do not require quotes and all
	*  other types do.  Each column in the result set is examined for it's DB2 type and quotations are
	*  placed around data elements if required.  Once we have traversed the entire result set, the CSV
	*  report is written to file.  All exceptions are caught and written to screen.
	*
	*  INPUTS:  1.  Connection db2Conn - Active connection to a database.
	*			2.  String filePath - This is the file path that the report will be stored in.
	*			3.  String inDateFormat - This is an optional date formatting parameter
	*			4.  String inTimeFormat - This is an optional time formatting parameter
	*			5.  String inTimeStampFormat - This is an optional time stamp formatting parameter
	*			6.  String sqlStatement - This is the SQL statement that will be executed on the database
	*			7.  String header - This is the optional header (values are HEADER_NONE, HEADER_DB2, HEADER_CUSTOM)
	*			8.  Constants:  HEADER_NONE, HEADER_DB2, HEADER_CUSTOM, DOUBLE_QUOTE, DEFAULT_SEPERATOR
	*
	*  OUTPUTS:  	1.  Returns an error string if one occurred.
	*				2.  CSF formatted report file
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*  HISTORY:
	*  2012-07-09 | MM | C4630 added delimiter parameter
	*
	***************************************************************************************************/
	private static String createCSVReport(Connection db2Conn, String filePath, String inDateFormat, String inTimeFormat, String inTimeStampFormat, String sqlStatement, String header,String delimiter) {
		String result,  CSVValue;
		FileWriter outCSV;
		String[] row;
		Date aDate;
		Time aTime;
		Timestamp aTimestamp;
		SimpleDateFormat formatter;
		ResultSetMetaData rsmd;
		Statement stmt;
		ResultSet rs;
		int numColumns, countRows, reportNumber;
		//C4630
		String seperator ;
		if(delimiter != null & !delimiter.equalsIgnoreCase("") & !delimiter.equalsIgnoreCase(",")){
			seperator = delimiter;
		}else{
			seperator = DEFAULT_SEPERATOR;
		}
		//  Initialize some variables
		rs = null; stmt = null; result = "";  countRows = 0;


		try {
			//  Execute the SQL statement on the database and pick up the result set
			stmt = db2Conn.createStatement();
			rs = stmt.executeQuery(sqlStatement);
			//  Pick up the result set meta data (column names and data types etc...)
			rsmd = rs.getMetaData();
			numColumns = rsmd.getColumnCount();

			//  Prepare the CSV file and a new row
			outCSV = new FileWriter(filePath);
			row = new String[numColumns];

			//  Generate the optional header and write it to the CSV file.
			if (header.equalsIgnoreCase(HEADER_DB2)) {
				if(delimiter != null & !delimiter.equalsIgnoreCase("") & !delimiter.equalsIgnoreCase(","))
					outCSV.write(generateDB2Header(rsmd).replaceAll(",", delimiter));
				else
					outCSV.write(generateDB2Header(rsmd));
			} else if (header.equalsIgnoreCase(HEADER_CUSTOM)) {
				reportNumber = generateUniqueReportNumber(db2Conn);
				outCSV.write(generateCustomHeader(filePath, reportNumber));
			}

			//  Go through every row in the result set
			while (rs.next()) {
				countRows++;
				//  Go though each column and examine the data and the DB2 data type.
				for (int i=1; i<numColumns+1; i++) {
					String columnType = rsmd.getColumnTypeName(i);

					//  This block of code examines every possible DB2 data type and places
					//  quotations around data values depending on the type.  Items marked
					//  Tested have been confirmed to require quotations around data values
					//  when creating a CSV file.
					if (rs.getString(i) != null) {
						CSVValue = "";
						if (columnType.equalsIgnoreCase("BIGINT")){
							CSVValue = rs.getString(i);
							CSVValue = escapeCharacters(CSVValue);
							CSVValue = CSVValue + seperator;
						}
						if (columnType.equalsIgnoreCase("BLOB")){
							CSVValue = rs.getString(i);
							CSVValue = escapeCharacters(CSVValue);
							CSVValue = DOUBLE_QUOTE + CSVValue + DOUBLE_QUOTE + seperator;
						}
						//if (columnType.toUpperCase().startsWith("CHAR")){ 				//C5150
						if (columnType.toUpperCase().startsWith("CHAR") || columnType.toUpperCase().startsWith("NCHAR") || columnType.toUpperCase().startsWith("NTEXT")){
							CSVValue = rs.getString(i);
							CSVValue = escapeCharacters(CSVValue);
							CSVValue = DOUBLE_QUOTE + CSVValue + DOUBLE_QUOTE + seperator;
						}
						if (columnType.equalsIgnoreCase("CLOB")){
							CSVValue = rs.getString(i);
							CSVValue = escapeCharacters(CSVValue);
							CSVValue = DOUBLE_QUOTE + CSVValue + DOUBLE_QUOTE + seperator;

						}
						if (columnType.equalsIgnoreCase("DATE")){		 				
							aDate = rs.getDate(i);
							formatter = new SimpleDateFormat(inDateFormat);
							CSVValue = formatter.format(aDate) + seperator;
						}
						//if (columnType.equalsIgnoreCase("DECIMAL")){ 					//C5150
						if (columnType.equalsIgnoreCase("DECIMAL") || columnType.equalsIgnoreCase("MONEY")){	
						CSVValue = rs.getString(i);
							CSVValue = escapeCharacters(CSVValue);
							CSVValue = CSVValue + seperator;
						}
						//if (columnType.equalsIgnoreCase("DOUBLE")){						//C5150
						if (columnType.equalsIgnoreCase("DOUBLE") || columnType.equalsIgnoreCase("FLOAT") || columnType.equalsIgnoreCase("NUMERIC")){
							CSVValue = rs.getString(i);
							CSVValue = escapeCharacters(CSVValue);
							CSVValue = CSVValue + seperator;
						}
						//if (columnType.equalsIgnoreCase("INTEGER")){ 					//C5150
						if (columnType.equalsIgnoreCase("INTEGER") || columnType.equalsIgnoreCase("INT")){	
						CSVValue = rs.getString(i);
							CSVValue = escapeCharacters(CSVValue);
							CSVValue = CSVValue + seperator;
						}
						if (columnType.equalsIgnoreCase("LONG VARCHAR")){
							CSVValue = rs.getString(i);
							CSVValue = escapeCharacters(CSVValue);
							CSVValue = DOUBLE_QUOTE + CSVValue + DOUBLE_QUOTE + seperator;
						}
						if (columnType.equalsIgnoreCase("REAL")){
							CSVValue = rs.getString(i);
							CSVValue = escapeCharacters(CSVValue);
							CSVValue = CSVValue + seperator;
						}
						//if (columnType.equalsIgnoreCase("SMALLINT")){	 				// C5150
						if (columnType.equalsIgnoreCase("SMALLINT") || columnType.equalsIgnoreCase("TINYINT")){	
							CSVValue = rs.getString(i);
							CSVValue = escapeCharacters(CSVValue);
							CSVValue = CSVValue + seperator;
						}
						if (columnType.equalsIgnoreCase("TIME")){ 						//Tested
							aTime = rs.getTime(i);
							formatter = new SimpleDateFormat(inTimeFormat);
							CSVValue = DOUBLE_QUOTE + formatter.format(aTime) + DOUBLE_QUOTE + seperator;
						}
						//if (columnType.equalsIgnoreCase("TIMESTAMP")){ 					//C5150
						if (columnType.equalsIgnoreCase("TIMESTAMP") || columnType.equalsIgnoreCase("DATETIME") || columnType.equalsIgnoreCase("DATETIME2") || columnType.equalsIgnoreCase("SMALLDATETIME")){	
							aTimestamp = rs.getTimestamp(i);
							formatter = new SimpleDateFormat(inTimeStampFormat);
							CSVValue = DOUBLE_QUOTE + formatter.format(aTimestamp) + DOUBLE_QUOTE + seperator;
						}
						//if (columnType.equalsIgnoreCase("VARCHAR")){ 					//C5150
						if (columnType.equalsIgnoreCase("VARCHAR") || columnType.equalsIgnoreCase("NVARCHAR") || columnType.equalsIgnoreCase("TEXT") || columnType.equalsIgnoreCase("UNIQUEIDENTIFIER")  || columnType.startsWith("VARCHAR") || columnType.startsWith("varchar") ){
	
							CSVValue = rs.getString(i);
							CSVValue = escapeCharacters(CSVValue);
							CSVValue = DOUBLE_QUOTE + CSVValue + DOUBLE_QUOTE + seperator;
						}

					//  If the data value in the column is null then we just add a comma separator without any quotes
					} else {
						CSVValue = seperator;
					}
					//  Add the newly parsed column to the row array
					row[i-1] = CSVValue;
				}
				//  Remove trailing comma and add new line
				row[numColumns-1] = row[numColumns-1].substring(0, row[numColumns-1].length() -1) + NEWLINE ;

				//  Write the row to the file.
				writeArrayToFile(outCSV, row);
			}

			//  If custom header option was chosen then we add this custom footer as well.
			if (header.equalsIgnoreCase(HEADER_CUSTOM)) {
				outCSV.write(generateFooter(countRows));
			}

			//  We are done so close the file.
			outCSV.close();


		//  Catch any exceptions during the process and set them up in the result error string.
		} catch (SQLException sqlEx) {
			result = "SQL Error:  " + sqlEx.getMessage() + NEWLINE;
			result = result + "SQL Statement:  " + sqlStatement + NEWLINE;
		} catch (IOException ioe) {
			result = "Application Error: Problem creating report file: [" + filePath + "]";

		//  Make sure we close all resources before exiting the routine.
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
					} catch (SQLException sqlEx) {
					stmt = null;
				}
			}
			if (rs != null) {
				try {
					rs.close();
					} catch (SQLException sqlEx) {
					rs = null;
				}
			}
		}

		return (result);
	}

	// Added for Excel export support
	/***************************************************************************************************
	*  NAME:  createExcelReport
	*
	*  PURPOSE:  This method is responsible for the creation of the CSV report.  It receives all of the
	*  information that is required to create the report.  It begins by executing the SQL statement and
	*  storing the results in the result set.  The next step is to create the optional header for the
	*  report.  The result set is parsed row by row and column by column.  Since we are creating CSV
	*  files, we need to ensure that the report conforms to CSV format.  This requires parsing each
	*  column and escaping any special characters.  It also requires placing quotes around certain types
	*  of data and placing commas between columns.  There are 14 different types of db2 data.  Some of
	*  them require quotes and some do not.  In general, numbers and dates do not require quotes and all
	*  other types do.  Each column in the result set is examined for it's DB2 type and quotations are
	*  placed around data elements if required.  Once we have traversed the entire result set, the CSV
	*  report is written to file.  All exceptions are caught and written to screen.
	*
	*  INPUTS:  1.  Connection db2Conn - Active connection to a database.
	*			2.  String filePath - This is the file path that the report will be stored in.
	*			3.  String inDateFormat - This is an optional date formatting parameter
	*			4.  String inTimeFormat - This is an optional time formatting parameter
	*			5.  String inTimeStampFormat - This is an optional time stamp formatting parameter
	*			6.  String sqlStatement - This is the SQL statement that will be executed on the database
	*			7.  String header - This is the optional header (values are HEADER_NONE, HEADER_DB2, HEADER_CUSTOM)
	*			8.  Constants:  HEADER_NONE, HEADER_DB2, HEADER_CUSTOM, DOUBLE_QUOTE, DEFAULT_SEPERATOR
	*
	*  OUTPUTS:  	1.  Returns an error string if one occurred.
	*				2.  CSF formatted report file
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	private static String createExcelReport(Connection db2Conn, String filePath,String title, String inDateFormat, String inTimeFormat, String inTimeStampFormat, String sqlStatement, String header) {
		String result;
		WritableWorkbook workBook;
		WritableSheet  sheet;
		WritableCell cell;
		Date aDate;
		Time aTime;
		Timestamp aTimestamp;
		ResultSetMetaData rsmd;
		Statement stmt;
		ResultSet rs;
		int numColumns, countRows, reportNumber;

		//  Initialize some variables
		rs = null; stmt = null; result = ""; countRows = 0;


		try {
			//  Execute the SQL statement on the database and pick up the result set
			stmt = db2Conn.createStatement();
			rs = stmt.executeQuery(sqlStatement);
			//  Pick up the result set meta data (column names and data types etc...)
			rsmd = rs.getMetaData();
			numColumns = rsmd.getColumnCount();

			//  Prepare the CSV file and a new row
			File exportFile = new File(filePath);
			if(!exportFile.exists())
				exportFile.createNewFile();
			WorkbookSettings wbSetting = new WorkbookSettings();  
            wbSetting.setUseTemporaryFileDuringWrite(true);  
			workBook = Workbook.createWorkbook(exportFile,wbSetting);
			sheet = workBook.createSheet(title, 0);			

			//  Generate the optional header and write it to the Excel file.
			if (header.equalsIgnoreCase(HEADER_DB2)) {
				WriteHeaderFromString(sheet,0,generateDB2Header(rsmd));
				//countRows = 1;
			} else if (header.equalsIgnoreCase(HEADER_CUSTOM)) {
				reportNumber = generateUniqueReportNumber(db2Conn);
				WriteHeaderFromString(sheet,0,generateCustomHeader(filePath, reportNumber));
				//countRows = 1;
			}else{
				countRows = 0;
			}
			//  Go through every row in the result set
			DateFormat customDateFormat = new DateFormat (inDateFormat);
			DateFormat customTimeFormat = new DateFormat (inTimeFormat);
			DateFormat customTimestampFormat = new DateFormat (inTimeStampFormat);
			while (rs.next()) {
				countRows++;
				//  Go though each column and examine the data and the DB2 data type.
				for (int i=1; i<numColumns+1; i++) {
					String columnType = rsmd.getColumnTypeName(i);

					//  This block of code examines every possible DB2 data type and places
					//  quotations around data values depending on the type.  Items marked
					//  Tested have been confirmed to require quotations around data values
					//  when creating a CSV file.
					if (rs.getString(i) != null) {
						cell = null;
						if (columnType.equalsIgnoreCase("BIGINT")){
							cell = new jxl.write.Number(i-1,countRows,rs.getDouble(i));
			
						}
						if (columnType.equalsIgnoreCase("BLOB")){
							cell = new jxl.write.Label(i-1,countRows, escapeCharacters(rs.getString(i)));
						}
						
						//if (columnType.toUpperCase().startsWith("CHAR")){ 				//C5150
						if (columnType.toUpperCase().startsWith("CHAR") || columnType.toUpperCase().startsWith("NCHAR") || columnType.toUpperCase().startsWith("NTEXT")){	
							cell = new jxl.write.Label(i-1,countRows, escapeCharacters(rs.getString(i)));
						}
						
						if (columnType.equalsIgnoreCase("CLOB")){
							cell = new jxl.write.Label(i-1,countRows, escapeCharacters(rs.getString(i)));
						}
						
						if (columnType.equalsIgnoreCase("DATE")){		 				//Tested
							//DateFormat customDateFormat = new DateFormat (inDateFormat);
							WritableCellFormat dateFormat = new WritableCellFormat (customDateFormat); 
							aDate = rs.getDate(i);
							cell = new jxl.write.DateTime(i-1,countRows,aDate,dateFormat);
						}
						//if (columnType.equalsIgnoreCase("DECIMAL")){ 					//C5150
						if (columnType.equalsIgnoreCase("DECIMAL") || columnType.equalsIgnoreCase("MONEY")){	
							cell = new jxl.write.Number(i-1,countRows,rs.getFloat(i));						
						}
						
						//if (columnType.equalsIgnoreCase("DOUBLE")){						//C5150
						if (columnType.equalsIgnoreCase("DOUBLE") || columnType.equalsIgnoreCase("FLOAT") || columnType.equalsIgnoreCase("NUMERIC")){	
							cell = new jxl.write.Number(i-1,countRows,rs.getDouble(i));	
						}
						
						//if (columnType.equalsIgnoreCase("INTEGER")){ 					//C5150
						if (columnType.equalsIgnoreCase("INTEGER") || columnType.equalsIgnoreCase("INT")){	
							cell = new jxl.write.Number(i-1,countRows,rs.getInt(i));	
						}
						if (columnType.equalsIgnoreCase("LONG VARCHAR")){
							cell = new jxl.write.Label(i-1,countRows, escapeCharacters(rs.getString(i)));
						}
						if (columnType.equalsIgnoreCase("REAL")){
							cell = new jxl.write.Number(i-1,countRows,rs.getFloat(i));	
						}
						
						//if (columnType.equalsIgnoreCase("SMALLINT")){	 				//C5150
						if (columnType.equalsIgnoreCase("SMALLINT") || columnType.equalsIgnoreCase("TINYINT")){	
							cell = new jxl.write.Number(i-1,countRows,rs.getInt(i));
						}
						
						if (columnType.equalsIgnoreCase("TIME")){ 						//Tested
							aTime = rs.getTime(i);							
							WritableCellFormat dateFormat = new WritableCellFormat (customTimeFormat); 
							cell = new jxl.write.DateTime(i-1,countRows,aTime,dateFormat);
						}
						//if (columnType.equalsIgnoreCase("TIMESTAMP")){ 					//C5150
						if (columnType.equalsIgnoreCase("TIMESTAMP") || columnType.equalsIgnoreCase("DATETIME") || columnType.equalsIgnoreCase("DATETIME2") || columnType.equalsIgnoreCase("SMALLDATETIME")){	
							aTimestamp = rs.getTimestamp(i);
							WritableCellFormat dateFormat = new WritableCellFormat (customTimestampFormat); 
							cell = new jxl.write.DateTime(i-1,countRows,aTimestamp,dateFormat);					
						}
						//if (columnType.equalsIgnoreCase("VARCHAR")){ 					//C5150
						if (columnType.equalsIgnoreCase("VARCHAR") || columnType.equalsIgnoreCase("NVARCHAR") || columnType.equalsIgnoreCase("TEXT") || columnType.equalsIgnoreCase("UNIQUEIDENTIFIER")  || columnType.startsWith("VARCHAR") || columnType.startsWith("varchar") ){	
							cell = new jxl.write.Label(i-1,countRows, escapeCharacters(rs.getString(i)));
						}

					//  If the data value in the column is null then we just add a comma separator without any quotes
					} else {
						cell = new jxl.write.Label(i-1,countRows, "");
					}
					//  Add the newly parsed column to the row array
					sheet.addCell(cell);
				}
			}
				
			

			//  If custom header option was chosen then we add this custom footer as well.
			if (header.equalsIgnoreCase(HEADER_CUSTOM)) {
				WriteHeaderFromString(sheet,countRows+1,generateFooter(countRows));
			}

			//  We are done so close the file.
			workBook.write();
			workBook.close();

		//  Catch any exceptions during the process and set them up in the result error string.
		} catch (SQLException sqlEx) {
			result = "SQL Error:  " + sqlEx.getMessage() + NEWLINE;
			result = result + "SQL Statement:  " + sqlStatement + NEWLINE;
		} catch (IOException ioe) {
			result = "Application Error: Problem creating report file: [" + filePath + "]";
		} catch (Exception ex){
			result = "Application Error: Problem creating report file: [" + filePath + "]" + " -- " + ex.getMessage();
		//  Make sure we close all resources before exiting the routine.
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
					} catch (SQLException sqlEx) {
					stmt = null;
				}
			}
			if (rs != null) {
				try {
					rs.close();
					} catch (SQLException sqlEx) {
					rs = null;
				}
			}
		}

		return (result);
	}
	// fill up header into Excel by comma separated string
	private static void WriteHeaderFromString(WritableSheet sheet,int row,String header){
		Label label;
		String[] arr = commaSeparatedStringToStringArray(header);
		try{
			for(int i=0;i<arr.length;i++){
				label = new Label(i, row, arr[i]);
				sheet.addCell(label);
			}
		}catch(Exception ex){
			System.out.println(ex.getMessage());
		}
	}
	/*  C5150 - Replacing entire method 
	private static String[] commaSeparatedStringToStringArray(String aString){
	    String[] splittArray = null;
	    if (aString != null || !aString.equalsIgnoreCase("")){
	         splittArray = aString.split(",");
	         System.out.println(aString + " " + splittArray);
	    }
	    return splittArray;
	}
	*/
	private static String[] commaSeparatedStringToStringArray(String aString){
	    String[] splittArray = null;
	    if (aString != null) {
	    	if (!aString.equalsIgnoreCase("")) {
	         splittArray = aString.split(",");
	         System.out.println(aString + " " + splittArray);
	    	}
	    }
	    return splittArray;
	}
	
	//Excel support end
	
	/***************************************************************************************************
	*  NAME:  escapeCharacters
	*
	*  PURPOSE:  This routine takes an input string and formats it to comply with CSV format.
	*  Some characters in a string may need to be escaped with the \ character  [" and \]
	*
	*  INPUTS:  1.  String str - any string
	*
	*  OUTPUTS:  Return the same string formatted for CSV
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	private static String escapeCharacters(String str) {
		String result;
		StringBuffer sb = new StringBuffer();  //  Use stringbuffer for speed

		result = str;

		//  If the string is not blank or null then search for " and \ and escape them
		if ((!str.equals("")) && (str != null)) {
			//  Loop through all the characters in the string

			for (int j = 0; j < str.length(); j++) {
				char nextChar = str.charAt(j);

				//  If the current character is "
				if (nextChar == '"') {
					sb.append('\\').append(nextChar);
				//  If the current character is \
				} else if (nextChar == '\\') {
					sb.append('\\').append(nextChar);
				//  Character ok
				} else {
					sb.append(nextChar);
				}
			}
		result = sb.toString();
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  writeArrayToFile
	*
	*  PURPOSE:  This method is used to write an array of strings to file as one line
	*
	*  INPUTS:  1.  FileWriter outFile - The file the string array will be written to
	*			2.  String[] line - This is an array of strings
	*
	*  OUTPUTS:  If an error occurred then result will contain the msg.  Result will be empty if no
	*  error occurred.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	private static String writeArrayToFile(FileWriter outFile, String[] line) {
		String result;

		result = "";

		//  Go through each element in the array and write it out to file.
		try {
			for (int i = 0; i < line.length; i++) {
				outFile.write(line[i]);
			}
		//  catch any exception and package it in the result string
		} catch (IOException ioe) {
			result = ioe.getMessage();
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  generateCustomHeader
	*
	*  PURPOSE:  This routine generates a custom header for the CSV report with the following sample
	*  format:
	*				*HDR 000008 filename_20060115.csv 20060115
	*
	*			The parts of this header are
	*				*HDR
	*				{reportNumber formatted with REPORT_HDR_NUMBER_FORMAT}
	*				{file name}_{date formatted by DATE_EXPORT_FORMAT}.{file extension}
	*				{date formatted by DATE_EXPORT_FORMAT}.{file extension}
	*
	*
	*  INPUTS:  1.  String filePath - A file path the the output CSV file
	*			2.  int reportNumber - A Report Number
	*			3.  Constants: DATE_EXPORT_FORMAT, REPORT_HDR_NUMBER_FORMAT
	*
	*  OUTPUTS:  Returns a custom string header described above.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	private static String generateCustomHeader(String filePath, int reportNumber) {
		String result;
		File fileObj;
		SimpleDateFormat formatter;
		DecimalFormat numFormatter;

		//  Prepare the current date to be formatted usgin DATE_EXPORT_FORMAT
		formatter = new SimpleDateFormat(DATE_EXPORT_FORMAT);
		//  Put the filePath in an object to extract the file name easily
		fileObj = new File(filePath);
		//  Prepare the Report Number formatted usging REPORT_HDR_NUMBER_FORMAT
		numFormatter = new DecimalFormat(REPORT_HDR_NUMBER_FORMAT);

		//  Return the header
		result = "*HDR " + numFormatter.format(reportNumber) + " " + fileObj.getName() + " " + formatter.format(new Date()) + NEWLINE;

		return(result);
	}

	/***************************************************************************************************
	*  NAME:  generateDB2Header
	*
	*  PURPOSE:  This routine generates a DB2 Column Name header for the CSV report.  Column names are
	*  obtained from DB2, more specifically the result set that was executed on DB2.  Once a query has
	*  been run on the database the resultset metadata will contain the column names.
	*
	*  INPUTS:  1.  ResultSetMetaData rsmd - This is the metadata object after the running a query on the
	*  database.
	*
	*  OUTPUTS:  A single comma separated header line
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	private static String generateDB2Header(ResultSetMetaData rsmd) {
		String result;
		int numColumns;

		result = ""; numColumns = 0;
		//  Loop through all of the columns in the meta data object and extract the name
		//  We will surround the names in quotes and separate them with commas.
		try {
			numColumns = rsmd.getColumnCount();
			for (int i=1; i<numColumns+1; i++) {
				result = result  + rsmd.getColumnName(i) + ",";
			}

			//  Remove trailing comma and add new line
			result = result.substring(0, result.length() -1) + NEWLINE ;

		//  If there is a problem, then do nothing.  Just return an empty result header.
		} catch (SQLException sqlEx) {

		}


		return(result);
	}


	/***************************************************************************************************
	*  NAME:  generateFooter
	*
	*  PURPOSE:  This routine  generates a footer in the following format:
	*				*TRL 0000000
	*				*TRL {last row is a number representing the last row of the result set}
	*
	*  INPUTS:  1.  int lastRow - last row is a number representing the recordset count.  LastRow is
	*  				padded by REPORT_TRL_NUMBER_FORMAT
	*				Constants:  REPORT_TRL_NUMBER_FORMAT   (pad zeros)
	*
	*  OUTPUTS:  A string trailer formatted as above.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	private static String generateFooter(int lastRow) {
		String result;

		//  pad lastRow with zeros
		DecimalFormat formatter = new DecimalFormat(REPORT_TRL_NUMBER_FORMAT);

		// C2309 Added NEWLINE to end of report trailer.
		result = "*TRL " + "" + formatter.format(lastRow) + NEWLINE;

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  generateFileName
	*
	*  PURPOSE:  This method is used to generate a file name that includes the current date as part
	*  of the name eg.  filename_20060115.csv.  The _date portion is generated in this routine.  The
	*  date is formatted based on DATE_EXPORT_FORMAT.
	*
	*  INPUTS:  1.  String fileName - a file name
	*			2.  Constants:  DATE_EXPORT_FORMAT
	*
	*  OUTPUTS:  A new file name with the date appended to the end of the file name
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*  
	*  2011-11-16 Mark Ma C4438 added Excel support, fileType can be csv (default) or xls 
	*
	***************************************************************************************************/
	private static String generateFileName(String fileType,String fileName) {
		String result;
		SimpleDateFormat formatter;
		int pos;


		formatter = new SimpleDateFormat(DATE_EXPORT_FORMAT);

		//  If the file name has an extension then we place the date at the end of the file name and
		//  before the extension.  (filename_20060115.csv)
		pos = fileName.lastIndexOf(".");
		if (pos > 0) {
			result = fileName.substring(0,fileName.lastIndexOf("."));
			result = result + "_" + formatter.format(new Date()) + "." + fileType;

		//  If the file name does not have an extension then we place the date at the end of the
		//  file name (filename_20060115)

		} else {
			result = fileName + "_" + formatter.format(new Date()) + "." + fileType;
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  generateUniqueReportNumber
	*
	*  PURPOSE:  This is a very specific hard coded routine used with the Lenovo database only.  The
	*  purpose of this routine is to generate a report number that will be used in a report header.
	*  The report number is based on a count of the number of months that have elapsed since a certain
	*  date found in the database.   There is a key table in the Lenovo database that contains the
	*  following:  KEY, DATE, NUMBER.  We are interested in looking up the KEY:  RSEQ
	*  This key contains a DATE and a NUMBER.  The date value will hold a start date.  The number
	*  value will hold a start number.  The report number that we generate here will be:
	*    (The number of elapsed months from start date to today) + (the start number)
	*  Every month the report number will be one more than the previous month.
	*
	*  INPUTS:  Connection con - Active connection to the Lenovo database
	*
	*  OUTPUTS:  A generated report number
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	private static int generateUniqueReportNumber(Connection con) {
		String sqlStatement;
		Statement stmt;
		ResultSet rs;
		Date startDate;
		int elapsedMonths, result, startNumber;
		Calendar startCal, todayCal;
		//C3905 start
		String sqlGetPeriodEndDate;
		Date minPeriodEndDate;
		//C3905 end

		//  Initialize some variables
		rs = null; stmt = null; result = 0; startNumber = 0; elapsedMonths = 0;

		//  Hard coded SQL statement for use with the Lenovo database only
		sqlStatement = "SELECT DATE_VALUE, INT_VALUE FROM LENOVO.KEYS WHERE KEY = 'RSEQ'";
		//C3905
		sqlGetPeriodEndDate = "SELECT MIN(PERIOD_END_DTE) AS PERIOD_END_DTE FROM LENOVO.PERIOD_DATES WHERE LEN_DATA_REC = 'N'";
		//C3905 end
		try {
			//  Execute the SQL statement and move to the first row
			stmt = con.createStatement();
			rs = stmt.executeQuery(sqlStatement);
			rs.next();
			//  Pick up the start date and the start number
			startDate = rs.getDate("DATE_VALUE");
			startNumber = rs.getInt("INT_VALUE");
			rs.close();
			//C3905
			rs = stmt.executeQuery(sqlGetPeriodEndDate);
			rs.next();
			minPeriodEndDate = rs.getDate("PERIOD_END_DTE");
			//C3905 end			
			//  Set the java start date
			startCal = Calendar.getInstance();
			startCal.setTime(startDate);
			
			//  Set the java todays date
			//C3905 start 
			todayCal = Calendar.getInstance();
			todayCal.setTime(minPeriodEndDate);
			//C3395 end
			//  Calculate the number of elapsed months from start date to today
			//  N.B.  We add 1 to the Month because java counts months beginning from 0.  January is month zero
			if (todayCal.after(startCal)) {
				while ( ((startCal.get(Calendar.MONTH) + 1) != (todayCal.get(Calendar.MONTH) + 1)) || (startCal.get(Calendar.YEAR) != todayCal.get(Calendar.YEAR))  ){
					startCal.add(Calendar.MONTH, 1);
					elapsedMonths++;
		        }

			}
			//  Report Number is the number of elapsed months + the Start number found in the db
			result = elapsedMonths + startNumber +1;


		//  If there is an exception then we don't care.  Just return 0 as the report number.
		} catch (SQLException sqlEx) {
			//  Do nothing
			System.out.println(sqlEx.getMessage());

		//  This will make sure that we clean up resources
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
					} catch (SQLException sqlEx) {
					stmt = null;
				}
			}
			if (rs != null) {
				try {
					rs.close();
					} catch (SQLException sqlEx) {
					rs = null;
				}
			}
		}


		return(result);
	}

	/***************************************************************************************************
	*  NAME:  parseXMLReportFile
	*
	*  PURPOSE:  This routine is responsible for parsing the command line input XML file.  The XML file
	*  is used to setup this application to produce CSV reports.  The XML file contains a set of report
	*  nodes.  Each report node contains the following required information used to create the report:
	*
	*	<report>
	*     <fileName text="testing.csv" />
	*     <SQL text="
	*	  		SELECT 	*
	*			FROM LENOVO.BF_BILLABLE_V
	*			WHERE A = B
	*     " />
	*   </report>
	*
	*  Basically the minimum amount of information needed to create a report is the output CSV file name
	*  and the SQL statement that will be used to generate the result set.
	*
	*  This example demonstrates some of the optional parameter that can be used when generating reports
	*
	*	<report includeHeader="Custom">
	*     <fileName text="testing.csv" />
	*     <dateFormat text="YYYY/MM/DD" />
	*     <timeFormat text="HH:MM:SS" />
	*     <SQL text="
	*	  		SELECT 	*
	*			FROM LENOVO.BF_BILLABLE_V
	*			WHERE A = B
	*     " />
	*  </report>
	*
	*  - The includeHeader option is used to indicate if the report should have a header.  The following
	*  are accepted values:
	*						"None" - No Header will be created
	*   					"Custom" - Custom Lenovo Header will be created  (See generateCustomHeader)
	*						"Db2Header" - DB2 Column names will be used for the header
	*
	*  - The dateFormat option is used to allow you specify a custom date format on the report
	*  - The timeFormat option is used to allow you specify a custom time format on the report
	*
	*  INPUTS:  1.  Connection db2Conn - Active database connection
	*			2.  String xmlPath - This is the path to the XML reports file
	*
	*  OUTPUTS:  This routine does not return any output to the calling module.
	*  Output is in the form of CSV reports.  One report CSV file for every report node in the XML file.
	*
	*  CREATED BY:  Ka-Lai Wong January, 2006 - IBM
	*
	***************************************************************************************************/
	private static void parseXMLReportFile(Connection db2Conn, String xmlPath) {
		String result;
		Matcher m;
		Document document;
		//  C2393 Begin  Added k variables
		NodeList nodes_i, nodes_j, nodes_k;
		Node node_i, node_j, node_k;
		Element element_i, element_j, element_k;
		String elementTag_i, elementTag_j, elementTag_k;
		String includeHeader, fileType,title, fileName, sql, dateFormat, timeFormat, errorMsg, includeTotals;
		// C5150 - Removal - ArrayList subReports = new ArrayList();
		ArrayList<SubReport> subReports = new ArrayList<SubReport>();
		//  C2393 End
		//C4630
		String delimiter = "";
		//  Initialize some variables
		result = ""; errorMsg = "";
		document = null; includeHeader = null; fileType = EXPORTCSV;fileName = null; sql = null; dateFormat = null; timeFormat = null;
		title = "";
		// Create DOM document for xml file
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// turn on XML validation
		factory.setValidating(true);
		// create errorHandler to handle any XML parse error
		XMLErrorHandler errorHandler = new XMLErrorHandler();

		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			// register errorHandler to builder
			builder.setErrorHandler(errorHandler);
			// any parse warning / error will invoke errorHandler
			document = builder.parse(xmlPath);
		} catch (SAXParseException spe) {
			result = result + "Application Error: XML parse failed! \n" + spe.getMessage();

		} catch (SAXException sxe) {
		    // Error generated by this application (or a parser-initialization error)
			result = result + "Application Error: Incorrect XML format! ";

		} catch (ParserConfigurationException pce) {
			// Parser with specified options can't be built
			result = result + "Application Error: Parser with specified options cannot be built! ";

		} catch (IOException ioe) {
		    // I/O error
			result = result + "Application Error: XML File not found -> " + xmlPath;
		}

		// if builder.parse above was successful, errorCount and warningCount should be 0
		if (result.equals("")) {
			if (errorHandler.getErrorCount() > 0 || errorHandler.getWarningCount() > 0) {
				SAXException se = errorHandler.getFirstException();
				result = result + "Application Error: CSV is NOT processed because " + errorHandler.getErrorCount() + " error(s), " + errorHandler.getWarningCount() + " warning(s) are found in XML" + NEWLINE;
				result = result + "First error is: " + se.getMessage();
			}
		}

		//  No XML parsing errors occurred and the XML file was successfully validated against the DTD
		if (result.equals("")) {
			// load xml elements from DOM document
			nodes_i = document.getDocumentElement().getChildNodes();
			//  Loop through all of the nodes in the XML file
			for (int i = 0; i < nodes_i.getLength(); i++) {
				node_i = nodes_i.item(i);
				if (node_i.getNodeType() == Node.ELEMENT_NODE) {
					element_i = (Element) node_i;
					elementTag_i = element_i.getTagName();

					//  **********************************************************************************
					//  * The following section is used for parsing a report node.  The report node
					//  * contains a single SQL statement, the results of which will be output to file
					//  **********************************************************************************
					if (elementTag_i.equalsIgnoreCase(REPORT)) {
						includeHeader = element_i.getAttribute(INCLUDE_HEADER);
						nodes_j = element_i.getChildNodes();

						// Initialize attribute variables
						fileName = ""; sql = ""; dateFormat = DATE_EXPORT_FORMAT; timeFormat = TIME_EXPORT_FORMAT;
						fileType = "csv";
						//  Loop through all elements in the report node
						for (int j = 0; j < nodes_j.getLength(); j++) {
							node_j = nodes_j.item(j);
							if (node_j.getNodeType() == Node.ELEMENT_NODE) {
								element_j = (Element) node_j;
								elementTag_j = element_j.getTagName();
							//  Pick up the report file type
								if (elementTag_j.equalsIgnoreCase(FILE_TYPE)) {
									fileType = element_j.getAttribute(TEXT);
									title = element_j.getAttribute(TITLE);
								}
								//  Pick up the report file name
								if (elementTag_j.equalsIgnoreCase(FILE_NAME)) {
									fileName = generateFileName(fileType,element_j.getAttribute(TEXT));
									//C4630 added dilimiter
									delimiter = element_j.getAttribute(DELIMITER);
								}
								//  Pick up optional date formatting tag and set the text capitalization to
								//  what the java date formatter expects
								if (elementTag_j.equalsIgnoreCase(DATE_FORMAT)) {
									dateFormat = element_j.getAttribute(TEXT);
									//set up date format string from XML with correct case (lowercase y, d and uppercase M)
									m = Pattern.compile("Y").matcher(dateFormat);
									dateFormat = m.replaceAll("y");
									m = Pattern.compile("m").matcher(dateFormat);
									dateFormat = m.replaceAll("M");
									m = Pattern.compile("D").matcher(dateFormat);
									dateFormat = m.replaceAll("d");
								}
								//  Pick up optional time formatting tag and set the text capitalization to
								//  what the java time formatter expects
								if (elementTag_j.equalsIgnoreCase(TIME_FORMAT)) {
									timeFormat = element_j.getAttribute(TEXT);
									//set up time format string from XML with correct case (lowercase m, s and uppercase H)
									m = Pattern.compile("h").matcher(timeFormat);
									timeFormat = m.replaceAll("H");
									m = Pattern.compile("M").matcher(timeFormat);
									timeFormat = m.replaceAll("m");
									m = Pattern.compile("S").matcher(timeFormat);
									timeFormat = m.replaceAll("s");
								}
								//  Pick up the SQL statement that will be used to query the database
								if (elementTag_j.equalsIgnoreCase(SQL)) {
									sql = element_j.getAttribute(TEXT).trim();
								}
							}
						}

						//  We should have everything we need to create a CSV report.  If theris a problem
						//  with any of the values that were picked up in report node then those errors
						//  will be returned in the string errorMsg
						if(fileType.equalsIgnoreCase(EXPORTCSV)){
							errorMsg = createCSVReport(db2Conn, EXPORT_DIR + "/" + fileName, dateFormat, timeFormat, TIMESTAMP_EXPORT_FORMAT, sql, includeHeader,delimiter);
						}else{
							errorMsg = createExcelReport(db2Conn, EXPORT_DIR + "/" + fileName,title, dateFormat, timeFormat, TIMESTAMP_EXPORT_FORMAT, sql, includeHeader);
						}
						if (errorMsg.equals("")) {
							System.out.println("  Created: " + fileName);
						} else {
							System.out.println(errorMsg);
						}

					}  // End report node

					//  C2393 Begin
					//  **********************************************************************************
					//  * The following section is used for parsing a multiReport node.  The multiReport
					//  * node contains one or more subReport nodes.  Each subReport node will contain an
					//  * SQL statement to generate a result set.  The data set for each subReport will be
					//  * appended to a single output file.  subReport nodes are executed in the order in
					//  * which they appear in the XML document.
					//  **********************************************************************************

					if (elementTag_i.equalsIgnoreCase(MULTI_REPORT)) {
						nodes_j = element_i.getChildNodes();

						// Initialize attribute variables
						fileName = ""; dateFormat = DATE_EXPORT_FORMAT; timeFormat = TIME_EXPORT_FORMAT;
						//  Loop through all elements in the report node
						for (int j = 0; j < nodes_j.getLength(); j++) {
							node_j = nodes_j.item(j);
							if (node_j.getNodeType() == Node.ELEMENT_NODE) {
								element_j = (Element) node_j;
								elementTag_j = element_j.getTagName();
							//  Pick up the report file type
								if (elementTag_j.equalsIgnoreCase(FILE_TYPE)) {
									fileType = element_j.getAttribute(TEXT);
									title = element_j.getAttribute(TITLE);
								}
								//  Pick up the report file name
								if (elementTag_j.equalsIgnoreCase(FILE_NAME)) {
									fileName = generateFileName(fileType,element_j.getAttribute(TEXT));
									//C4630 added dilimiter
									delimiter = element_j.getAttribute(DELIMITER);
								}
								//  Pick up optional date formatting tag and set the text capitalization to
								//  what the java date formatter expects
								if (elementTag_j.equalsIgnoreCase(DATE_FORMAT)) {
									dateFormat = element_j.getAttribute(TEXT);
									//set up date format string from XML with correct case (lowercase y, d and uppercase M)
									m = Pattern.compile("Y").matcher(dateFormat);
									dateFormat = m.replaceAll("y");
									m = Pattern.compile("m").matcher(dateFormat);
									dateFormat = m.replaceAll("M");
									m = Pattern.compile("D").matcher(dateFormat);
									dateFormat = m.replaceAll("d");
								}
								//  Pick up optional time formatting tag and set the text capitalization to
								//  what the java time formatter expects
								if (elementTag_j.equalsIgnoreCase(TIME_FORMAT)) {
									timeFormat = element_j.getAttribute(TEXT);
									//set up time format string from XML with correct case (lowercase m, s and uppercase H)
									m = Pattern.compile("h").matcher(timeFormat);
									timeFormat = m.replaceAll("H");
									m = Pattern.compile("M").matcher(timeFormat);
									timeFormat = m.replaceAll("m");
									m = Pattern.compile("S").matcher(timeFormat);
									timeFormat = m.replaceAll("s");
								}

								//  Pick up the sub report node to get all of the parameters needed to
								//  create the subreport.
								if (elementTag_j.equalsIgnoreCase(SUB_REPORT)) {
									sql = "";
									includeHeader = element_j.getAttribute(INCLUDE_HEADER);
									includeTotals = element_j.getAttribute(INCLUDE_TOTALS);
									nodes_k = element_j.getChildNodes();
									for (int k = 0; k < nodes_k.getLength(); k++) {
										node_k = nodes_k.item(k);
										if (node_k.getNodeType() == Node.ELEMENT_NODE) {
											element_k = (Element) node_k;
											elementTag_k = element_k.getTagName();

											//  Pick up the SQL statement that will be used to query the database
											if (elementTag_k.equalsIgnoreCase(SQL)) {
												sql = element_k.getAttribute(TEXT).trim();
											}
										}

									}
									subReports.add(new SubReport(sql, includeHeader, includeTotals));
								}


							}
						}

						//  We should have everything we need to create a CSV report.  If theris a problem
						//  with any of the values that were picked up in report node then those errors
						//  will be returned in the string errorMsg
						if(fileType.equalsIgnoreCase(EXPORTCSV)){
							errorMsg = createCSVMultiReport(db2Conn, EXPORT_DIR + "/" + fileName, dateFormat, timeFormat, TIMESTAMP_EXPORT_FORMAT, subReports,delimiter);
						}else{
							errorMsg = createExcelMultiReport(db2Conn, EXPORT_DIR + "/" + fileName,title, dateFormat, timeFormat, TIMESTAMP_EXPORT_FORMAT, subReports);
						}
						if (errorMsg.equals("")) {
							System.out.println("  Created: " + fileName);
						} else {
							System.out.println(errorMsg);
						}

					}  //  End multiReport node

					//  C2393 End





				}
			}
		} else {
			// alert operator that XML error was encountered
			System.out.println(result);
		}


	}



	//  C2393  Begin new function
	/***************************************************************************************************
	*  NAME:  createCSVMultiReport
	*
	*  PURPOSE:  This method is responsible for the creation of the CSV report.  It receives all of the
	*  information that is required to create the report.  It begins by executing the SQL statement and
	*  storing the results in the result set.  The next step is to create the optional header for the
	*  report.  The result set is parsed row by row and column by column.  Since we are creating CSV
	*  files, we need to ensure that the report conforms to CSV format.  This requires parsing each
	*  column and escaping any special characters.  It also requires placing quotes around certain types
	*  of data and placing commas between columns.  There are 14 different types of db2 data.  Some of
	*  them require quotes and some do not.  In general, numbers and dates do not require quotes and all
	*  other types do.  Each column in the result set is examined for it's DB2 type and quotations are
	*  placed around data elements if required.  Once we have traversed the entire result set, the CSV
	*  report is written to file.  All exceptions are caught and written to screen.
	*
	*  INPUTS:  1.  Connection db2Conn - Active connection to a database.
	*			2.  String filePath - This is the file path that the report will be stored in.
	*			3.  String inDateFormat - This is an optional date formatting parameter
	*			4.  String inTimeFormat - This is an optional time formatting parameter
	*			5.  String inTimeStampFormat - This is an optional time stamp formatting parameter
	*			6.  String sqlStatement - This is the SQL statement that will be executed on the database
	*			7.  String header - This is the optional header (values are HEADER_NONE, HEADER_DB2, HEADER_CUSTOM)
	*			8.  Constants:  HEADER_NONE, HEADER_DB2, HEADER_CUSTOM, DOUBLE_QUOTE, DEFAULT_SEPERATOR
	*
	*  OUTPUTS:  	1.  Returns an error string if one occurred.
	*				2.  CSF formatted report file
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	
	//private static String createCSVMultiReport(Connection db2Conn, String filePath, String inDateFormat, String inTimeFormat, String inTimeStampFormat, ArrayList subReports,String delimiter) {
	private static String createCSVMultiReport(Connection db2Conn, String filePath, String inDateFormat, String inTimeFormat, String inTimeStampFormat, ArrayList<SubReport> subReports,String delimiter) {	
		String result, CSVValue;
		FileWriter outCSV;
		String[] row;
		Date aDate;
		Time aTime;
		Timestamp aTimestamp;
		SimpleDateFormat formatter;
		ResultSetMetaData rsmd;
		Statement stmt;
		ResultSet rs;
		int numColumns;
		SubReport sub;
		String sqlStatement, header, totalsFlag;
		double[] totals;
        int tot = 0;          //C3425
		result = "";
		//C4630
		String seperator ;
		if(delimiter != null & !delimiter.equalsIgnoreCase("") & !delimiter.equalsIgnoreCase(",")){
			seperator = delimiter;
		}else{
			seperator = DEFAULT_SEPERATOR;
		}
		try {
			//  Prepare the CSV file for output
			outCSV = new FileWriter(filePath);

			for (int x = 0; x < subReports.size(); x++) {
				sub = (SubReport)subReports.get(x);

				sqlStatement = sub.getSQL();
				header = sub.getHeader();
				totalsFlag = sub.getTotalsFlag();

				//  Initialize some variables
				rs = null; stmt = null; row = null; totals = null;

				try {
					//  Execute the SQL statement on the database and pick up the result set
					stmt = db2Conn.createStatement();
					rs = stmt.executeQuery(sqlStatement);
					//  Pick up the result set meta data (column names and data types etc...)
					rsmd = rs.getMetaData();
					numColumns = rsmd.getColumnCount();

					row = new String[numColumns];
					totals = new double[numColumns];
					for (int y = 0; y < numColumns; y++) {
						totals[y] = 0.0;
				    }

					//  Generate the optional header and write it to the CSV file.
					if (header.equalsIgnoreCase(HEADER_DB2)) {
						if(delimiter != null & !delimiter.equalsIgnoreCase("") & !delimiter.equalsIgnoreCase(","))
							outCSV.write(generateDB2Header(rsmd).replaceAll(",", delimiter));
						else
							outCSV.write(generateDB2Header(rsmd));
					}

					//  Go through every row in the result set
					while (rs.next()) {
						//  Go though each column and examine the data and the DB2 data type.
						for (int i=1; i<numColumns+1; i++) {
							String columnType = rsmd.getColumnTypeName(i);

							//  This block of code examines every possible DB2 data type and places
							//  quotations around data values depending on the type.  Items marked
							//  Tested have been confirmed to require quotations around data values
							//  when creating a CSV file.
							if (rs.getString(i) != null) {
								CSVValue = "";
								if (columnType.equalsIgnoreCase("BIGINT")){
									CSVValue = rs.getString(i);
									CSVValue = escapeCharacters(CSVValue);
									CSVValue = CSVValue + seperator;
									totals[i-1] = totals[i-1] + Double.parseDouble(rs.getString(i));
								}
								
								if (columnType.equalsIgnoreCase("BLOB")){
									CSVValue = rs.getString(i);
									CSVValue = escapeCharacters(CSVValue);
									CSVValue = DOUBLE_QUOTE + CSVValue + DOUBLE_QUOTE + seperator;
								}
								
								//if (columnType.toUpperCase().startsWith("CHAR")){ 				//C5150
								if (columnType.toUpperCase().startsWith("CHAR") || columnType.toUpperCase().startsWith("NCHAR") || columnType.toUpperCase().startsWith("NTEXT")){	
								CSVValue = rs.getString(i);
									CSVValue = escapeCharacters(CSVValue);
									CSVValue = DOUBLE_QUOTE + CSVValue + DOUBLE_QUOTE + seperator;
								}
								
								if (columnType.equalsIgnoreCase("CLOB")){
									CSVValue = rs.getString(i);
									CSVValue = escapeCharacters(CSVValue);
									CSVValue = DOUBLE_QUOTE + CSVValue + DOUBLE_QUOTE + seperator;

								}
								if (columnType.equalsIgnoreCase("DATE")){		 				//Tested
									aDate = rs.getDate(i);
									formatter = new SimpleDateFormat(inDateFormat);
									CSVValue = formatter.format(aDate) + seperator;
								}
								
								//if (columnType.equalsIgnoreCase("DECIMAL")){ 					//C5150
								if (columnType.equalsIgnoreCase("DECIMAL") || columnType.equalsIgnoreCase("MONEY")){	
									CSVValue = rs.getString(i);
									CSVValue = escapeCharacters(CSVValue);
									CSVValue = CSVValue + seperator;
									totals[i-1] = totals[i-1] + Double.parseDouble(rs.getString(i));
								}
								
								//if (columnType.equalsIgnoreCase("DOUBLE")){				//C5150
								if (columnType.equalsIgnoreCase("DOUBLE") || columnType.equalsIgnoreCase("FLOAT") || columnType.equalsIgnoreCase("NUMERIC")){	
									CSVValue = rs.getString(i);
									CSVValue = escapeCharacters(CSVValue);
									CSVValue = CSVValue + seperator;
									totals[i-1] = totals[i-1] + Double.parseDouble(rs.getString(i));
								}
								
								//if (columnType.equalsIgnoreCase("INTEGER")){ 					//C5150
								if (columnType.equalsIgnoreCase("INTEGER") || columnType.equalsIgnoreCase("INT")){	
									CSVValue = rs.getString(i);
									CSVValue = escapeCharacters(CSVValue);
									CSVValue = CSVValue + seperator;
									totals[i-1] = totals[i-1] + Double.parseDouble(rs.getString(i));
								}
								
								if (columnType.equalsIgnoreCase("LONG VARCHAR")){
									CSVValue = rs.getString(i);
									CSVValue = escapeCharacters(CSVValue);
									CSVValue = DOUBLE_QUOTE + CSVValue + DOUBLE_QUOTE + seperator;
								}
								
								if (columnType.equalsIgnoreCase("REAL")){
									CSVValue = rs.getString(i);
									CSVValue = escapeCharacters(CSVValue);
									CSVValue = CSVValue + seperator;
									totals[i-1] = totals[i-1] + Double.parseDouble(rs.getString(i));
								}
								
								//if (columnType.equalsIgnoreCase("SMALLINT")){	 				//C5150
								if (columnType.equalsIgnoreCase("SMALLINT") || columnType.equalsIgnoreCase("TINYINT")){	
									CSVValue = rs.getString(i);
									CSVValue = escapeCharacters(CSVValue);
									CSVValue = CSVValue + seperator;
									totals[i-1] = totals[i-1] + Double.parseDouble(rs.getString(i));
								}
								
								if (columnType.equalsIgnoreCase("TIME")){ 						//Tested
									aTime = rs.getTime(i);
									formatter = new SimpleDateFormat(inTimeFormat);
									CSVValue = DOUBLE_QUOTE + formatter.format(aTime) + DOUBLE_QUOTE + seperator;
								}
								
								//if (columnType.equalsIgnoreCase("TIMESTAMP")){ 					//C5150
								if (columnType.equalsIgnoreCase("TIMESTAMP") || columnType.equalsIgnoreCase("DATETIME") || columnType.equalsIgnoreCase("DATETIME2") || columnType.equalsIgnoreCase("SMALLDATETIME")){	
									aTimestamp = rs.getTimestamp(i);
									formatter = new SimpleDateFormat(inTimeStampFormat);
									CSVValue = DOUBLE_QUOTE + formatter.format(aTimestamp) + DOUBLE_QUOTE + seperator;
								}
								
								//if (columnType.equalsIgnoreCase("VARCHAR")){ 					//C5150
								if (columnType.equalsIgnoreCase("VARCHAR") || columnType.equalsIgnoreCase("NVARCHAR") || columnType.equalsIgnoreCase("TEXT") || columnType.equalsIgnoreCase("UNIQUEIDENTIFIER")  || columnType.startsWith("VARCHAR") || columnType.startsWith("varchar") ){
									CSVValue = rs.getString(i);
									CSVValue = escapeCharacters(CSVValue);
									CSVValue = DOUBLE_QUOTE + CSVValue + DOUBLE_QUOTE + seperator;
								}

							//  If the data value in the column is null then we just add a comma separator without any quotes
							} else {
								CSVValue = seperator;
							}
							//  Add the newly parsed column to the row array
							row[i-1] = CSVValue;
						}
						//  Remove trailing comma and add new line
						row[numColumns-1] = row[numColumns-1].substring(0, row[numColumns-1].length() -1) + NEWLINE ;

						//  Write the row to the file.
						writeArrayToFile(outCSV, row);
					}
                       tot  = 0;    //  reset flag for multiple total lines in report C3425
                       if (totalsFlag.equalsIgnoreCase("Y")) {
					outCSV.write(NEWLINE);
					for (int y=1; y<numColumns+1; y++) {
						if (rsmd.getColumnTypeName(y).equalsIgnoreCase("BIGINT") |
							rsmd.getColumnTypeName(y).equalsIgnoreCase("INTEGER")|
							rsmd.getColumnTypeName(y).equalsIgnoreCase("DECIMAL") |
							rsmd.getColumnTypeName(y).equalsIgnoreCase("DOUBLE") |
							rsmd.getColumnTypeName(y).equalsIgnoreCase("REAL") |
							rsmd.getColumnTypeName(y).equalsIgnoreCase("SMALLINT")
							){
							outCSV.write(Double.toString(totals[y-1]));
							
							/* Start C3425                                          *
							* This code will insert a "Total:" in the CSV only if   *
							* at least one preceeding column is a string            *
							* otherwise no "Total:" will be written.                *
							*********************************************************/
							tot = 1;
						}
						if ((y != numColumns)&& (tot != 1)) {
							if (rsmd.getColumnTypeName(y+1).equalsIgnoreCase("BIGINT") |
							   rsmd.getColumnTypeName(y+1).equalsIgnoreCase("INTEGER")|
						       rsmd.getColumnTypeName(y+1).equalsIgnoreCase("DECIMAL") |
							   rsmd.getColumnTypeName(y+1).equalsIgnoreCase("DOUBLE") |
							   rsmd.getColumnTypeName(y+1).equalsIgnoreCase("REAL") |
							   rsmd.getColumnTypeName(y+1).equalsIgnoreCase("SMALLINT")
							   ){
								outCSV.write("Total:");
							    }
						}
						   // End C3425  
						outCSV.write(seperator);  
					}
					outCSV.write(NEWLINE);
				}
				outCSV.write(NEWLINE);

				//  Catch any exceptions during the process and set them up in the result error string.
				} catch (SQLException sqlEx) {
					result = "SQL Error:  " + sqlEx.getMessage() + NEWLINE;
					result = result + "SQL Statement:  " + sqlStatement + NEWLINE;
				} finally {
					if (stmt != null) {
						try {
							stmt.close();
							} catch (SQLException sqlEx) {
							stmt = null;
						}
					}
					if (rs != null) {
						try {
							rs.close();
							} catch (SQLException sqlEx) {
							rs = null;
						}
					}
				}
			}  //  end outer for loop

			//  We are done so close the file.
			outCSV.close();

		}


		catch (IOException ioe) {
			result = "Application Error: Problem creating report file: [" + filePath + "]";
		}


		return (result);
	}   //  C2393  End new function
	
	/***************************************************************************************************
	*  NAME:  createExcelMultiReport
	*
	*  PURPOSE:  This method is responsible for the creation of the Excel report.  It receives all of the
	*  information that is required to create the report.  It begins by executing the SQL statement and
	*  storing the results in the result set.  The next step is to create the optional header for the
	*  report.  The result set is parsed row by row and column by column.  Since we are creating CSV
	*  files, we need to ensure that the report conforms to CSV format.  This requires parsing each
	*  column and escaping any special characters.  It also requires placing quotes around certain types
	*  of data and placing commas between columns.  There are 14 different types of db2 data.  Some of
	*  them require quotes and some do not.  In general, numbers and dates do not require quotes and all
	*  other types do.  Each column in the result set is examined for it's DB2 type and quotations are
	*  placed around data elements if required.  Once we have traversed the entire result set, the CSV
	*  report is written to file.  All exceptions are caught and written to screen.
	*
	*  INPUTS:  1.  Connection db2Conn - Active connection to a database.
	*			2.  String filePath - This is the file path that the report will be stored in.
	*			3.  String inDateFormat - This is an optional date formatting parameter
	*			4.  String inTimeFormat - This is an optional time formatting parameter
	*			5.  String inTimeStampFormat - This is an optional time stamp formatting parameter
	*			6.  String sqlStatement - This is the SQL statement that will be executed on the database
	*			7.  String header - This is the optional header (values are HEADER_NONE, HEADER_DB2, HEADER_CUSTOM)
	*			8.  Constants:  HEADER_NONE, HEADER_DB2, HEADER_CUSTOM, DOUBLE_QUOTE, DEFAULT_SEPERATOR
	*
	*  OUTPUTS:  	1.  Returns an error string if one occurred.
	*				2.  Excel formatted report file
	*
	*  CREATED BY:  Mark Ma  Nov. 21, 2011 
	*
	***************************************************************************************************/
	//private static String createExcelMultiReport(Connection db2Conn, String filePath,String title, String inDateFormat, String inTimeFormat, String inTimeStampFormat, ArrayList subReports) {
	private static String createExcelMultiReport(Connection db2Conn, String filePath,String title, String inDateFormat, String inTimeFormat, String inTimeStampFormat, ArrayList<SubReport> subReports) {	
		String result;
		WritableWorkbook workBook;
		WritableSheet  sheet;
		WritableCell cell;
		Date aDate;
		Time aTime;
		Timestamp aTimestamp;
		ResultSetMetaData rsmd;
		Statement stmt;
		ResultSet rs;
		int numColumns;
		SubReport sub;
		String sqlStatement, header, totalsFlag;
		double[] totals;
        int tot = 0;          //C3425
		result = "";
		int countRows = 0;
		try {
			//  Prepare the Excel file for output
			File exportFile = new File(filePath);
			if(!exportFile.exists())
				exportFile.createNewFile();
			WorkbookSettings wbSetting = new WorkbookSettings();  
            wbSetting.setUseTemporaryFileDuringWrite(true);  
			workBook = Workbook.createWorkbook(exportFile,wbSetting);
			sheet = workBook.createSheet(title,0);	
			// sub report level loop
			for (int x = 0; x < subReports.size(); x++) {
				sub = (SubReport)subReports.get(x);
				sqlStatement = sub.getSQL();
				header = sub.getHeader();
				totalsFlag = sub.getTotalsFlag();
								
				//  Initialize some variables
				rs = null; stmt = null; totals = null;
				try {
					//  Execute the SQL statement on the database and pick up the result set
					stmt = db2Conn.createStatement();
					rs = stmt.executeQuery(sqlStatement);
					//  Pick up the result set meta data (column names and data types etc...)
					rsmd = rs.getMetaData();
					numColumns = rsmd.getColumnCount();
					totals = new double[numColumns];
					for (int y = 0; y < numColumns; y++) {
						totals[y] = 0.0;
				    }
										
				//  Generate the optional header and write it to the Excel file.
					if (header.equalsIgnoreCase(HEADER_DB2)) {
						WriteHeaderFromString(sheet,countRows,generateDB2Header(rsmd));
						countRows++;
					} 
					
					//  Go through every row in the result set
					while (rs.next()) {						
						//  Go though each column and examine the data and the DB2 data type.
						for (int i=1; i<numColumns+1; i++) {
							String columnType = rsmd.getColumnTypeName(i);

							//  This block of code examines every possible DB2 data type and places
							//  quotations around data values depending on the type.  Items marked
							//  Tested have been confirmed to require quotations around data values
							//  when creating a CSV file.
							if (rs.getString(i) != null) {
								cell = null;
								if (columnType.equalsIgnoreCase("BIGINT")){
									cell = new jxl.write.Number(i-1,countRows,rs.getDouble(i));
									totals[i-1] = totals[i-1] + Double.parseDouble(rs.getString(i));
								}
								if (columnType.equalsIgnoreCase("BLOB")){
									cell = new jxl.write.Label(i-1,countRows, escapeCharacters(rs.getString(i)));
								}
								//if (columnType.toUpperCase().startsWith("CHAR")){ 				//C5150
								if (columnType.toUpperCase().startsWith("CHAR") || columnType.toUpperCase().startsWith("NCHAR") || columnType.toUpperCase().startsWith("NTEXT")){	
								cell = new jxl.write.Label(i-1,countRows, escapeCharacters(rs.getString(i)));
								}
								if (columnType.equalsIgnoreCase("CLOB")){
									cell = new jxl.write.Label(i-1,countRows, escapeCharacters(rs.getString(i)));
								}
								if (columnType.equalsIgnoreCase("DATE")){		 				//Tested
									DateFormat customDateFormat = new DateFormat (inDateFormat);
									WritableCellFormat dateFormat = new WritableCellFormat (customDateFormat); 
									aDate = rs.getDate(i);
									cell = new jxl.write.DateTime(i-1,countRows,aDate,dateFormat);
								}
								//if (columnType.equalsIgnoreCase("DECIMAL")){ 					//C5150
								if (columnType.equalsIgnoreCase("DECIMAL") || columnType.equalsIgnoreCase("MONEY")){

									cell = new jxl.write.Number(i-1,countRows,rs.getFloat(i));
									totals[i-1] = totals[i-1] + Double.parseDouble(rs.getString(i));
								}
								//if (columnType.equalsIgnoreCase("DOUBLE")){			//C5150
								if (columnType.equalsIgnoreCase("DOUBLE") || columnType.equalsIgnoreCase("FLOAT") || columnType.equalsIgnoreCase("NUMERIC")){
									cell = new jxl.write.Number(i-1,countRows,rs.getDouble(i));	
									totals[i-1] = totals[i-1] + Double.parseDouble(rs.getString(i));
								}
								if (columnType.equalsIgnoreCase("INTEGER")){ 					//Tested
								//if (columnType.equalsIgnoreCase("INTEGER") || columnType.equalsIgnoreCase("INT")){				
									cell = new jxl.write.Number(i-1,countRows,rs.getInt(i));
									totals[i-1] = totals[i-1] + Integer.parseInt(rs.getString(i));
								}
								if (columnType.equalsIgnoreCase("LONG VARCHAR")){
									cell = new jxl.write.Label(i-1,countRows, escapeCharacters(rs.getString(i)));
								}
								if (columnType.equalsIgnoreCase("REAL")){
									cell = new jxl.write.Number(i-1,countRows,rs.getFloat(i));	
									totals[i-1] = totals[i-1] + Double.parseDouble(rs.getString(i));
								}
								//if (columnType.equalsIgnoreCase("SMALLINT")){	 				//C5150
								if (columnType.equalsIgnoreCase("SMALLINT") || columnType.equalsIgnoreCase("TINYINT")){	
								cell = new jxl.write.Number(i-1,countRows,rs.getInt(i));
									totals[i-1] = totals[i-1] + Integer.parseInt(rs.getString(i));
								}
								if (columnType.equalsIgnoreCase("TIME")){ 						//Tested
									aTime = rs.getTime(i);
									DateFormat customDateFormat = new DateFormat (inTimeFormat);
									WritableCellFormat dateFormat = new WritableCellFormat (customDateFormat); 
									cell = new jxl.write.DateTime(i-1,countRows,aTime,dateFormat);
								}
								//if (columnType.equalsIgnoreCase("TIMESTAMP")){ 					//C5150
								if (columnType.equalsIgnoreCase("TIMESTAMP") || columnType.equalsIgnoreCase("DATETIME") || columnType.equalsIgnoreCase("DATETIME2") || columnType.equalsIgnoreCase("SMALLDATETIME")){	
								aTimestamp = rs.getTimestamp(i);
									DateFormat customDateFormat = new DateFormat (inTimeStampFormat);
									WritableCellFormat dateFormat = new WritableCellFormat (customDateFormat); 
									cell = new jxl.write.DateTime(i-1,countRows,aTimestamp,dateFormat);					
								}
								//if (columnType.equalsIgnoreCase("VARCHAR")){ 					//C5150
								if (columnType.equalsIgnoreCase("VARCHAR") || columnType.equalsIgnoreCase("NVARCHAR") || columnType.equalsIgnoreCase("TEXT") || columnType.equalsIgnoreCase("UNIQUEIDENTIFIER")  || columnType.startsWith("VARCHAR") || columnType.startsWith("varchar") ){	
									cell = new jxl.write.Label(i-1,countRows, escapeCharacters(rs.getString(i)));
								}

							//  If the data value in the column is null then we just add a comma separator without any quotes
							} else {
								cell = new jxl.write.Label(i-1,countRows, "");
							}
							//  Add the newly parsed column to the row array
							sheet.addCell(cell);

						}
						countRows++;
					}
                    tot  = 0;    //  reset flag for multiple total lines in report C3425
                    if (totalsFlag.equalsIgnoreCase("Y")) {
					countRows ++;
					for (int y=1; y<numColumns+1; y++) {
						if (rsmd.getColumnTypeName(y).equalsIgnoreCase("BIGINT") |
							rsmd.getColumnTypeName(y).equalsIgnoreCase("INTEGER")|
							rsmd.getColumnTypeName(y).equalsIgnoreCase("DECIMAL") |
							rsmd.getColumnTypeName(y).equalsIgnoreCase("DOUBLE") |
							rsmd.getColumnTypeName(y).equalsIgnoreCase("REAL") |
							rsmd.getColumnTypeName(y).equalsIgnoreCase("SMALLINT")
							){
							cell = new jxl.write.Number(y-1,countRows,totals[y-1]);
							sheet.addCell(cell);
							
							/* Start C3425                                          *
							* This code will insert a "Total:" in the CSV only if   *
							* at least one preceeding column is a string            *
							* otherwise no "Total:" will be written.                *
							*********************************************************/
							tot = 1;
						}
						if ((y != numColumns)&& (tot != 1)) {
							if (rsmd.getColumnTypeName(y+1).equalsIgnoreCase("BIGINT") |
							   rsmd.getColumnTypeName(y+1).equalsIgnoreCase("INTEGER")|
						       rsmd.getColumnTypeName(y+1).equalsIgnoreCase("DECIMAL") |
							   rsmd.getColumnTypeName(y+1).equalsIgnoreCase("DOUBLE") |
							   rsmd.getColumnTypeName(y+1).equalsIgnoreCase("REAL") |
							   rsmd.getColumnTypeName(y+1).equalsIgnoreCase("SMALLINT")
							   ){
								cell = new jxl.write.Label(y-1,countRows, "Total:");
								sheet.addCell(cell);
							    }
						}
					}
				}                
				//  Catch any exceptions during the process and set them up in the result error string.
				} catch (SQLException sqlEx) {
					result = "SQL Error:  " + sqlEx.getMessage() + NEWLINE;
					result = result + "SQL Statement:  " + sqlStatement + NEWLINE;
				}catch (Exception ex) {
					result = "SQL Error:  " + ex.getMessage() + NEWLINE;
					result = result + "SQL Statement:  " + sqlStatement + NEWLINE;
				} finally {
					if (stmt != null) {
						try {
							stmt.close();
							} catch (SQLException sqlEx) {
							stmt = null;
						}
					}
					if (rs != null) {
						try {
							rs.close();
							} catch (SQLException sqlEx) {
							rs = null;
						}
					}
				}
				countRows++;
			}  //  end outer for loop
			
		//  We are done so close the file.
			workBook.write();
			workBook.close();

		}catch (Exception ioe) {
			result = "Application Error: Problem creating report file: [" + filePath + "]";
		}
		return (result);
	}   //  C2393  End new function

	/***************************************************************************************************
	*  NAME:  getDBinfo
	*
	*  PURPOSE:  This routine is responsible for parsing the database connection XML file.  The XML file
	*  connectin xml file includes all database information:
	*
	*	<connection name = "LSRPROD">
	*		<server>torlena1.mkm.can.ibm.com</server>
	*		<port>60006</port>	
	*		<driver>com.ibm.db2.jcc.DB2Driver</driver>	
	*	</connection>
	*
	*
	*  INPUTS:  The name of connection
	*
	*  OUTPUTS: if all required fields are parsed successfully then return empty string
	*  			else if any of required fields empty or there is any exception then retrun
	*  			a string with error message.
	*
	**************************************************************************************************  
	*  Modification History:
	*  2010-03-18 | MM | C4108 Created  
	*
	***************************************************************************************************/
	private static String getDBinfo(String dbname){
		String result = "";
		
		try{
			File file = new File("connections.xml");
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setValidating(true);
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(file);
			doc.getDocumentElement().normalize();
			//System.out.println("Root element " + doc.getDocumentElement().getNodeName());
			Element element = doc.getElementById(dbname.toUpperCase()); 
			if(element!=null){
				server_host = element.getAttribute("server"); 
				db2_port = element.getAttribute("port"); 
				connection_driver  = element.getAttribute("driver");
				server_type = element.getAttribute("server_type");
			}else{
				result = "Applidation Error: Can not find database name " + dbname + " in connections.xml\r\n";
			}
			//System.out.println("server:" +server +",port:"+port+",driver:"+driver);
			if(server_host.equals("") || db2_port.equals("") || connection_driver.equals("")){
				result = result + "There is problem to parse connection.xml, please check the xml file.\r\n"	;			
			}
		}catch(Exception ex){
			result = result +  ex.getMessage();
		}
		return result;
	}
	
}
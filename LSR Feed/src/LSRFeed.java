//General java modules
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
//db related modules
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.CallableStatement;
import java.sql.Types;
import java.sql.ResultSet;

//Custom modules
import lib.lenovo.DataMapper;
import lib.lenovo.CSVReader;
import lib.lenovo.ExcelReader;
import lib.lenovo.IFileReader;

/**
* Application: Lenovo Service Reporting Feeds.
*
* Version: 1.2
*
* Description:
*
* This application processes transactions for the Lenovo Service Reporting database.
* These transactions are read from one or more CSV files which are mapped into DB2 tables via
* XML configuration files.  Currently, this application processed new record insertions
* and existing record updates across five lines of business.
*
* JAVA prepared statements can not be used for this application because the SQL used is
* dynamically generated for each transaction and not constant.
*
* ---------------------------------------------------------------------------------------
* Modification History
* ---------------------------------------------------------------------------------------
* January 2006 - First Release 1.0
*
* March 12 2009 - C3771 Hang Shi Version 1.3.5 - Capture transactions with a count > 1.  ( e.g. a person who calls the help desk 5 times
*                  is captured as 1 row of data in the Feed with a transaction_cnt of 5)
*
* April 28 2009 - C3850 Hang Shi Version 1.3.6 - Exclude C3771 functions from appr_sumry and appr_fee15 feed
* 2009-10-12 C3992 MM Version 1.3.7 - Need to generate a unique service performed date when generating multiple BF transactions.
* 2009-12-17 C4046 MM Version 1.3.8 - When the feed generates multiple rows based on a transaction count > 1 we need to change this code
* 									  to stop using WW_CODE and start using SDW_COMP_ID as the trigger for generating multiple rows.
* 									  We will pick up the ww codes from the keys table and then lookup the possible SDW_COMPONENTS
* 									  associated with it in the SDW_COMPONENTS table.
* 2010-02-01 bugfix MM Version 1.3.9 - get GEN01 string from key table instead hard code.
* 2010-03-01 C4099 MM version 1.3.10 - Fixed bug duplicate rows in error file.
* 2010-10-06 C4199 Sakthivel Palanisamy Version 1.3.11 - Code Clean & Removal of approvedDataChangeRpt method.
* 2011-06-10 C4359 MM version 1.3.11 - Add support for Excel file.
*
*/
public class LSRFeed {
    private static final String PROGRAM = "Lenovo Service Reporting Data Feed";
    private static final String VERSION = "1.3.11";        //C4199 SP

    //  System properties file
    private static final String SYSTEM_PROPERTIES = "C:\\Users\\IBM_ADMIN\\Documents\\Projects\\RCTWorkspace\\LSR Feed\\bin\\LSR.properties";
    //private static final String SYSTEM_PROPERTIES = "LSR.properties";
    //  OS Independent new line character
    private static final String NEWLINE = System.getProperty("line.separator");

    //  Working directory of the application
    //private static final String FILE_IO_DIR = System.getProperty("user.dir") + "/file_io/lsr_feed";
    private static final String FILE_IO_DIR = System.getProperty("user.dir") + "\\bin\\file_io\\lsr_feed";
    //  Each type of transaction requires an XML map.  This map defines how the input CSV file
    //  relates to the DB2 database
    //private static final String NEW_TRANS_XML = "xml_map/new_transaction_map.xml";
    private static final String NEW_TRANS_XML = "xml_map\\new_transaction_map.xml";
    private static final String REJECTED_TRANS_XML = "xml_map/rejected_map.xml";
    private static final String APPROVED_TRANS_XML = "xml_map/approved_map.xml";

    //  Each type of transaction need a directory to find it's corresponding CSV files in.
    private static final String NEW_TRANS_DIR = "new_transaction";
    private static final String REJECTED_TRANS_DIR = "rejected";
    private static final String APPROVED_TRANS_DIR = "approved";

    //  CustomizedSQLState Code defined in Stored Procedure "LENOVO.SEARCHBL01_P"
    private static final String SQLState_NOT_FOUND = "38200";

    private static final String APP_REJ_BY_LENOVO = "LENOVO";
    private static final String APP_REJ_BY_LITA = "LITA";
    private static final String NOTFOUND="NOTFOUND";

    // WW code list which allow one row per service call
    private  static ArrayList ww_code_list = new ArrayList();
    // Used for map WW_CDE which need duplicating rows to key
    private  static HashMap wwcdeMap = new HashMap();
    // Used for map two keys in key table related to generated records
    private  static final HashMap genMap = new HashMap();
    private  static final String Excel_Extension = ".xls";

    /***************************************************************************************************
    *  NAME:  main
    *
    *  PURPOSE:  This is main program for the Lenovo Service Reporting Feeds. This application processes
    *  CSV files as insert and update transactions into the LSR database.
    *
    *  INPUTS:  When running the main program, 2 command line arguments are required.  See
    *  validateCommandArgs for a full description of command line arguments.
    *
    *
    *  OUTPUTS:  This program will produce one output report for each line of business.  The report
    *  is stored at the root of the business line and the file is called feed_rpt_{business line}.txt
    *  The report contains information pertaining to the number of successful and erroneous
    *  records and it will report any error message encountered on each transaction.
    *
    *  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
    *
    ***************************************************************************************************/
    public static void main(String[] args) {
        Connection db2Conn;
        File dir;
        File[] business_line_list;
        String business_line, feedReport, transactionReport;
        boolean transactionsFound;
        Properties sysProperties = new Properties();

        // Initialize final hashmap. Used for map two type of keys in key table related to generated records
        genMap.put("GEN1", "GR01");   // RCS Record is system generated based
        genMap.put("GEN2", "GB01");   // BF Record.

        //  Validate any command line arguments that are needed to run this application
        if (validateCommandArgs(args)) {
            //  Begin Get connection parameters from properties file
            if (loadSystemProperties(sysProperties)) {
                String connection_driver = getSystemProperty(sysProperties, "CONNECTION_DRIVER");
                String server_host = getSystemProperty(sysProperties, "SERVER_HOST");
                String db2_port = getSystemProperty(sysProperties,"DB2_PORT");
                String database_name = getSystemProperty(sysProperties, "DATABASE_NAME");
                String connection_url = "jdbc:db2://" + server_host + ":" + db2_port + "/" + database_name;

                db2Conn = connectDb(connection_driver, connection_url, args[0], args[1]);

                System.out.println(PROGRAM + " [" + VERSION + "]" + NEWLINE);
                //  If the connection the the databaes is successful
                if (db2Conn != null) {
                    System.out.println("Database Connection Established.");
                    System.out.println("Searching for Transactions...");

                    // Add WW_CDE which need to add one record per service call to an Arraylist.
                    String errMessage = getWWCDEAddDuplicatingRows(db2Conn);
                    if ( !errMessage.equals(""))
                    	System.out.println(errMessage);

                    //  This is the root directory where all file I/O takes place
                    dir = new File(FILE_IO_DIR);
                    if (dir.isDirectory()){
                        //  This application dynamically searches the root file I/O directory for business line
                        //  folders.  Each business line is represented as one folder.  If future business lines
                        //  are needed then we just need to add another folder at this level.
                        business_line_list = dir.listFiles();
                        for (int i=0; i<business_line_list.length; i++) {
                            if (business_line_list[i].isDirectory()) {
                                //  Initialize some variables
                                feedReport = "Started " + generateTimeStamp();
                                transactionReport = "";
                                transactionsFound = false;
                                business_line = business_line_list[i].getPath();

                                //  Each of the following sections represent one type of transaction that may
                                //  or may be acted on for the current business line.  If there are no input
                                //  CSV files for a particular transaction then that transaction is ignored.
                                //  Currently there are 4 types of transactions possible for this data feed.
                                //  Transactions are procesed inside the processTransactions method.  The
                                //  results of those transactions if any are received in the variable transactionReport.

                                //  These are New transactions into the database
                                transactionReport = processTransactions(db2Conn, business_line + "/" + NEW_TRANS_DIR, business_line + "/" + NEW_TRANS_XML);
                                if (!transactionReport.equals("")) {
                                    transactionsFound = true;
                                    feedReport = feedReport + "************************************************************************" + NEWLINE;
                                    feedReport = feedReport + "*                           NEW TRANSACTIONS" + NEWLINE;
                                    feedReport = feedReport + "************************************************************************" + NEWLINE;
                                    feedReport = feedReport + transactionReport;
                                }

                                //  These are Rejected transactions
                                transactionReport = processTransactions(db2Conn, business_line + "/" + REJECTED_TRANS_DIR, business_line + "/" + REJECTED_TRANS_XML);
                                if (!transactionReport.equals("")) {
                                    transactionsFound = true;
                                    feedReport = feedReport + "************************************************************************" + NEWLINE;
                                    feedReport = feedReport + "*                         REJECTED TRANSACTIONS" + NEWLINE;
                                    feedReport = feedReport + "************************************************************************" + NEWLINE;
                                    feedReport = feedReport + transactionReport;
                                }

                                //  Theses are Approved transactions
                                transactionReport = processTransactions(db2Conn, business_line + "/" + APPROVED_TRANS_DIR, business_line + "/" + APPROVED_TRANS_XML);
                                if (!transactionReport.equals("")) {
                                    transactionsFound = true;
                                    feedReport = feedReport + "************************************************************************" + NEWLINE;
                                    feedReport = feedReport + "*                         APPROVED TRANSACTIONS" + NEWLINE;
                                    feedReport = feedReport + "************************************************************************" + NEWLINE;
                                    feedReport = feedReport + transactionReport;
                                }

                                //  If the current line of business had some CSV files to process then we need to
                                //  output the results of those transactions to the report file.
                                if (transactionsFound) {
                                    feedReport = feedReport + NEWLINE + "Completed " + generateTimeStamp();
                                    if (writeToFile(business_line + "/feed_rpt_" + business_line_list[i].getName() + ".txt", "", feedReport)) {
                                        System.out.println("Created Report -> " + "feed_rpt_" + business_line_list[i].getName() + ".txt");
                                    }
                                }

                            }
                        }
                    } else {
                        System.out.println("Can not find working directory [" + FILE_IO_DIR + "]");
                        System.exit(1);
                    }
                    //  Everything is done so close connections.
                    closeDb(db2Conn);
                    System.out.println("Database Connection Closed.");
                }
                else {
					System.out.println("Application Error: Database Connection Problem.");
					System.exit(1);
				}
            }  else {
                System.out.println("Application Error: Problem Loading System Properties File: [" + SYSTEM_PROPERTIES + "]");
                System.exit(1);
            }
        }

    }  //  Main Program End


    /***************************************************************************************************
    *  NAME:  validateCommandArgs
    *
    *  PURPOSE:  This routine is used to validate the command line parameters that are needed to run
    *  this application.  Currently, this application requires 2 parameters which are used to establish
    *  a database connection.
    *
    *  INPUTS:  1.  String[] args - This routine receives the command line arguments in the string array
    *               args.  Two non null parameters are expected.  User ID and Password
    *
    *  OUTPUTS:  True if exactly two non null parameters are input, False otherwise.
    *
    *  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
    *
    ***************************************************************************************************/
    private static boolean validateCommandArgs(String[] args) {
        boolean result;

        result = false;
        //  Check is there are 3 non null parameters input in array args
        if (args.length == 2) {
            if (!args[0].equals("") && !args[1].equals("")) {
                 result = true;
            }
        }

        //  If the check above fails then print the required command line parameters to screen with
        //  an example.
        if (result == false) {
            System.out.println("Required Command Line Parameters");
            System.out.println("{User ID} {Password}" + NEWLINE);
            System.out.println("Example:  java -classpath db2jcc.jar;db2jcc_license_cu.jar LSRFeed.class SMITH ssd34fg" + NEWLINE);
        }
        return(result);
    }


    /***************************************************************************************************
    *  NAME:  loadSystemProperties
    *
    *  PURPOSE:  This routine is used to load application properties from a java properties file
    *            located in the current directory.  The file it is expecting is called LSR.properties
    *            Any system wide properties can be placed in this file.
    *
    *  INPUTS:  1.  Properties sysProperties - A new blank properties object
    *
    *  OUTPUTS:  The input properties object is loaded via file.
    *
    *  CREATED BY:  Victor Monaco  October, 2006 - Millennium IT
    *
    ***************************************************************************************************/
    private static boolean loadSystemProperties(Properties sysProperties) {
        boolean result = true;

        try {
            sysProperties.load(new FileInputStream(SYSTEM_PROPERTIES));
        } catch (IOException e) {
            result = false;
        }

        return (result);
    }


    /***************************************************************************************************
    *  NAME:  getSystemProperty
    *
    *  PURPOSE:  This is a simple utility routine to quickly return a value from the system properties
    *            object that is loaded from file when the application starts.  Given a key name, return
    *            the String value associated with that key.  If the key does not exist "" is returned.
    *
    *  INPUTS:  1.  Properties sysProperties - A properties object that is loaded with key value pairs
    *           2.  String prop - a key name
    *
    *  OUTPUTS:  The String value associated with prop
    *
    *  CREATED BY:  Victor Monaco  October, 2006 - Millennium IT
    *
    ***************************************************************************************************/
    private static String getSystemProperty(Properties sysProperties, String prop) {

        String result = sysProperties.getProperty(prop, "");

        return result;
    }


    /***************************************************************************************************
    *  NAME:  connectDb
    *
    *  PURPOSE:  This routine is used to establish a database connection.  If the connection is not
    *  successful then null is returned as the connection object.  Connection exceptions are caught,
    *  packaged and displayed to the screen immediately.
    *
    *  N.B.  AutoCommit is set to false.
    *
    *  INPUTS:  1.  String connection_driver -  JDBC Driver to be used
    *           2.  String connection_url - database connection url path
    *           3.  String user - User ID
    *           4.  String psw - User ID Password   {not encrypted}
    *
    *  OUTPUTS:  Valid Java connection object or Null if the connection was not successful.
    *
    *  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
    *
    ***************************************************************************************************/
    private static Connection connectDb(String connection_driver, String connection_url, String user, String psw) {

        Connection resultConn;
        String errMsg;

        resultConn = null;
        errMsg = "";

        //  Try to establish a connection using the input parameters
        try {
            Class.forName(connection_driver);
            resultConn = DriverManager.getConnection(connection_url, user,psw);

            resultConn.setAutoCommit(false);
        }
        //  This will catch missing database drivers
        catch (ClassNotFoundException cnfe) {
            errMsg = errMsg + "Exception Message:  " + cnfe.getMessage() + NEWLINE;
            errMsg = errMsg + "    Connection Driver -> " + connection_driver + NEWLINE;
            errMsg = errMsg + "    Connection URL    -> " + connection_url + NEWLINE;
        }
        //  This will catch invalid connection parameters
        catch (SQLException sqle) {
            errMsg = errMsg + "Exception Message:  " + sqle.getMessage() + NEWLINE;
            errMsg = errMsg + "    Connection Driver -> " + connection_driver + NEWLINE;
            errMsg = errMsg + "    Connection URL    -> " + connection_url + NEWLINE;
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
    *  NAME:  executeSQLStatement
    *
    *  PURPOSE:  This routine will execute a given SQL statement.  It will catch any exceptions, wrap
    *  them up and return them in the result error string.  Resources are cleaned up before returning
    *  to the calling module.
    *
    *  When performing an update statement, no rows found is normally a warning.  According to the
    *  business logic for this application all updates must act on at least one row.  If no rows
    *  were updated then an error should be signaled.
    *
    *  INPUTS:  1.  String sqlStatement - SQL Statement in string format
    *           2.  Connection con - Valid and active connection object
    *
    *  OUTPUTS:  This routine returns an error string.  If no error occurred the result is null.
    *
    *  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
    *
    ***************************************************************************************************/
    private static String executeSQLStatement(String sqlStatement, Connection con) {
        String result;
        Statement stmt;

        result = "";
        stmt = null;

        //  Make sure the statement is not empty
        if (!sqlStatement.equals("")) {
            try {
                stmt = con.createStatement();
                stmt.executeUpdate(sqlStatement);

                //  Added this more generic transaction verification
                if (stmt.getUpdateCount() != 1) {
                    result = "The following SQL statement resulted in no rows affected.  This may have been a resubmit from another business line.";
                }

                //  Every transaction is committed to the database.  This could be improved in the
                //  future to include DB2 save points and commit up to the last successful transaction.
                con.commit();

            //  Catch any SQL exceptions and wrap them up in the result string.  If an exception occurs then rollback.
            } catch (SQLException sqle) {
                result = sqle.getMessage();
                try {
                    con.rollback();
                } catch (SQLException sqlEx) {
                    result = "Application Error:  Can not rollback database.  " + sqlEx.getMessage();
                }

            //  Always clean up resources
            } finally {
                if (stmt != null) {
                    try {
                        stmt.close();
                    } catch (SQLException sqlEx) {
                        stmt = null;
                    }
                }
            }
        } else {
            result = "Can not execute empty or null SQL statement.";
        }

        //  If an error occurred then result will contain the error msg.
        return(result);
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

        result = false;

        try {
            con.close();
            result = true;
        }
        catch (SQLException sqle) {
            System.out.println("Application Error:  Database connection can not be Closed");
            System.out.println("Exception Message:  " + sqle.getMessage());
            result = false;
        }

        return(result);
    }


    /***************************************************************************************************
    *  NAME:  processTransactions
    *
    *  PURPOSE:  This is the work horse routine for the application.  It was designed to be as generic
    *  as possible. To process CSV files regardless of the data contained in the file as insert or update
    *  transactions into a database.  The application uses an XML configuration file to determine how
    *  the rows and columns in the CSV file will be used to perform the insert or update operation.
    *  The XML configuration file provides all of the information needed to map rows in a CSV file
    *  into an insert or update transaction.  The XML mapper file includes some of the following
    *  information:
    *       1.  The target table name
    *       2.  Columns in the CSV file that map to database columns (CSV can contain additional columns)
    *       3.  Data types and formats expected (ie dates)
    *       4.  Any data validation requirements (string lengths, max/min number values etc...)
    *
    *
    *  INPUTS:  1.  Connection db2Conn - An active database connection.
    *           2.  String CSVDataDir - This is the directory where the CSV files for this transaction
    *               can be found
    *           3.  String XMLMapperPath - This is the path to the XML mapper file for this transaction
    *           4.  Constants - NEW_TRANS_XML, REJECTED_TRANS_XML, APPROVED_TRANS_XML
    *                           NEW_TRANS_DIR, REJECTED_TRANS_DIR, APPROVED_TRANS_DIR
    *
    *  OUTPUTS: This routine sends a String report back to the calling module.  The report contains
    *           information regarding the processing of all CSV files that were found in the CSV Data
    *           Directory.  The report indicates the CSV file name processed, the total number of
    *           successful and erroneous transactions and all error messages reported along the way.
    *
    *  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
    *
    ***************************************************************************************************/
    private static String processTransactions(Connection db2Conn, String CSVDataDir, String XMLMapperPath) {
        DataMapper mapper;
        IFileReader reader;
        String [] nextLine;
        String sqlStatement, sqlError, header, result, dataWarning;
        File dir;
        File[] working_files_list;
        String working_file;
        boolean done;
        StringBuffer errMsg, rptMsg;
        int errorCnt, successCnt, approvedWarningCnt, generatedCnt;
        Object [] approvedResults;
        String app_rej_by="";
        Object[][] rejappbyObj = new Object[1][2];

        //  Initialize some variables
        result = ""; header = ""; rptMsg = new StringBuffer(); sqlError = ""; mapper = null; dataWarning = "";

        //  Go the CSV data directory
        dir = new File(CSVDataDir);
        if (dir.isDirectory()){
            working_files_list = dir.listFiles();
            //  Loop through all of the files found in the CSV data directory
            for (int x=0; x<working_files_list.length; x++) {
                if (working_files_list[x].isFile()) {
                    //  A file was found so lets start working on it.  Initialize counts and
                    //  set up the report reader for this file.
                    errMsg = new StringBuffer();
                    errorCnt = 0; successCnt = 0; approvedWarningCnt = 0; generatedCnt =0;
                    rptMsg.append("------------------------------------------------------------------------" + NEWLINE);
                    rptMsg.append("-  File -> " + working_files_list[x].getName() + NEWLINE);
                    rptMsg.append("------------------------------------------------------------------------" + NEWLINE);
                    working_file = working_files_list[x].getPath();

                    //  reader is associated with the CSV data file.  mapper is associated with the XML
                    //  mapper file.  The reader parses a CSV file and extracts one row into an Array of
                    //  string values.  That array of values is passed to the mapper object which then
                    //  parses, validates and prepares the data to create an insert or update transaction.
                    try {
                        //  Load the reader with the current CSV file
                        if(working_file.indexOf(Excel_Extension)>0){
                        	reader = new ExcelReader(working_file);
                        }else{
                        	reader = new CSVReader(new FileReader(working_file));
                        }
                        //  Load the mapper with the current XML file path.
                        mapper = new DataMapper(XMLMapperPath);

                        //  This is an XML option that verifies if the CSV file name contains a certain string.
                        //C3220 if (verifyCSVFileName(working_files_list[x].getName(), mapper.getCSVFileNameVerificationString())) {
                        app_rej_by=verifyCSVFileName(working_files_list[x].getName(), mapper.getCSVFileNameVerificationString());
                        if (! app_rej_by.equalsIgnoreCase(NOTFOUND)) {
                            //  When loading the mapper object with an XML file, the first thing we need to do
                            //  is to verify that all DB2 columns are mapped to a column number in the CSV file.  If
                            //  we used only CSV column numbers in the XML file then we can map every db2 column to
                            //  a CSV column and mapper.validateded would be true.  If we used CSV column header names
                            //  then we need to find the CSV column index that those names are found in.

                            if (!mapper.validated) {
                                done = false;

                                //  Loop through the CSV file row by row trying to pick up the header line to find
                                //  the column names we are looking for.  Rows beginning with * are ignored.
                                while (!done) {
                                    nextLine = reader.readNext();
                                    if (nextLine != null){
                                        if ((nextLine[0].trim().indexOf("*") != 0) && (!reader.currentLine().trim().equals(""))) {
                                            //  Try to find the columns we are looking for in this row
                                            mapper.setColumnIndexes(nextLine);
                                            //  If we found the header line and we can map all db2 columns to CSV indexes then validated is true
                                            if (mapper.validated) {
                                                header = reader.currentLine();
                                                done = true;
                                            }
                                        }
                                    } else {
                                        done = true;
                                    }
                                }

                            }

                            //  If we are in here then all DB2 column names are successfully mapped to a CSV column index
                            if (mapper.validated) {
                                //  Loop through all rows in the CSV file ignoring rows that begin with *
                                while ((nextLine = reader.readNext()) != null) {
                                    if ((nextLine[0].trim().indexOf("*") != 0) && (!reader.currentLine().trim().equals(""))) {

                                        //  Feed one row from the CSV file to the mapper object.  The mapper object will parse,
                                        //  validate, translate and prepare the data for the creation of a dynamic insert/update
                                        //  SQL statement
                                        if (mapper.feedLine(nextLine)) {
                                            //  If we get in here then the data row was successfully validated

                                            //  The business logic for this application requires four operations.  Two inserts
                                            //  and Two updates.  The mapper contains enough information at his point to
                                            //  dynamically generate any insert statement using the method generateInsertValues();
                                            //  Update statements need a bit of business log to them so there are custom methods
                                            //  used to generate those statements.
                                            sqlStatement = "";
                                            if (CSVDataDir.endsWith(NEW_TRANS_DIR)) {
                                            	if ((mapper.getDataByDB2Name("TRANSACTION_CNT") != null) && (mapper.getDataByDB2Name("SDW_COMP_ID") != null) && (CSVDataDir.indexOf("appr_sumry") <= 0) && (CSVDataDir.indexOf("appr_fee15") <= 0) ) {
                                                 	int transaction_cnt = Integer.parseInt(mapper.getDataByDB2Name("TRANSACTION_CNT"));
                                                 	String ww_code = mapper.getDataByDB2Name("SDW_COMP_ID");

                                                 	if (transaction_cnt > 1 && ww_code_list.contains(ww_code) ){
                                                 		// Set Transaction_CNT to 1 for the initial record
                                                 		sqlStatement = mapper.generateInsertValues(0, wwcdeMap, genMap);
                                                 	}else{
                                                 		sqlStatement = mapper.generateInsertValues();
                                                 	}
	                                            }else{
		                                       	 sqlStatement = mapper.generateInsertValues();
	                                            }

                                            } else if(CSVDataDir.endsWith(REJECTED_TRANS_DIR)) {

												//  store original target table
												String originalTargetTable = mapper.targetTable;
												//  get originating business line table based on transaction id
												String newTargetTable = getOriginatingRejectBusinesLine(mapper.getDataByDB2Name("TRANSACTION_ID"), db2Conn);

												if (newTargetTable.equalsIgnoreCase("")) {
													//  If there was an error the use the original target table
													mapper.targetTable = originalTargetTable;
												} else {
													//  use the new target table that was found using the transaction id
													mapper.targetTable = newTargetTable;
												}
												//  generate sql statement and return the target table back to the original
                                                if (app_rej_by.length() > 0 ) {
                                                	rejappbyObj [0][0] = "APP_REJ_BY";
                                                	rejappbyObj [0][1] = app_rej_by;
                                                	sqlStatement = mapper.generateInsertValues(rejappbyObj);
                                            	} else {
                                            		sqlStatement = mapper.generateInsertValues(rejappbyObj);
                                            	}
                                            	mapper.targetTable = originalTargetTable;

                                            } else if(CSVDataDir.endsWith(APPROVED_TRANS_DIR)) {

	                                           	approvedResults = processApprovedUpdate(mapper, db2Conn, app_rej_by);
                                                sqlError = (String) approvedResults[0];
                                                dataWarning = (String) approvedResults[1];

                                            } else {
                                                sqlStatement = "";
                                            }

                                            if (CSVDataDir.endsWith(APPROVED_TRANS_DIR)) {
                                                //  For approved record, it is already processed, check whether there are errors
                                                //  If a SQL Error occurred during execution then sqlError will contain the error
                                                //  message.  The error message is logged in the report and the erroneous data row
                                                //  is seperated into the error log (errMsg).  This error log will later be saved
                                                //  and left behind in the data directory.
                                                if (!sqlError.equals("")){
                                                    errorCnt++;
                                                    rptMsg.append("SQL Error:  " + sqlError + NEWLINE);
                                                    rptMsg.append(reader.currentLine() + NEWLINE + NEWLINE);
                                                    errMsg.append(reader.currentLine() + NEWLINE);
                                                } else if (!dataWarning.equals("")){
                                                    approvedWarningCnt++;
                                                    rptMsg.append("Data Warning:  " + dataWarning + NEWLINE);
                                                    rptMsg.append(reader.currentLine() + NEWLINE + NEWLINE);
                                                } else {
													successCnt++;
                                                }
                                            } else {
                                                //  Special algorithm is applied to approved records (see processApprovedUpdate),
                                                //  therefore, exclude those record from the logic below
                                                //  This will execute the generated SQL statement.

                                                sqlError = executeSQLStatement(sqlStatement, db2Conn);

                                                //  If a SQL Error occurred during execution then sqlError will contain the error
                                                //  message.  The error message is logged in the report and the erroneous data row
                                                //  is seperated into the error log (errMsg).  This error log will later be saved
                                                //  and left behind in the data directory.
                                                if (!sqlError.equals("")){
                                                    errorCnt++;
                                                    rptMsg.append("SQL Error:  " + sqlError + NEWLINE);
                                                    rptMsg.append("SQL Statement:  " + sqlStatement + NEWLINE);
                                                    rptMsg.append(reader.currentLine() + NEWLINE + NEWLINE);
                                                    errMsg.append(reader.currentLine() + NEWLINE);
                                                } else {
                                                    successCnt++;
                                                }


                                                if ((mapper.getDataByDB2Name("TRANSACTION_CNT") != null) && (mapper.getDataByDB2Name("SDW_COMP_ID") != null) && (CSVDataDir.indexOf("appr_sumry") <= 0) && (CSVDataDir.indexOf("appr_fee15") <= 0)) {
                                                	int transaction_cnt = Integer.parseInt(mapper.getDataByDB2Name("TRANSACTION_CNT"));
                                                	String ww_code = mapper.getDataByDB2Name("SDW_COMP_ID");
                                                	int generated_err_cnt = 0;
                                                	if (sqlError.equals("") && transaction_cnt > 1 && ww_code_list.contains(ww_code) ){
                                                		// Generate addtion records beside initial one, allow one record per service call based on Transaction_CNT
                                                		SimpleDateFormat sdf = new SimpleDateFormat();
                                                		sdf.applyPattern("yyyy/MM/dd");
                                                		Calendar calendar = Calendar.getInstance();
                                                		Date date = new Date();
                                                		String dateString = null;
                                                		for (int i=1; i<transaction_cnt; i++) {
                                                			dateString =  mapper.getDataByDB2Name("SERV_PERFORMED_DTE");
                                                			if (dateString != null){
                                                				date = sdf.parse(dateString);
                                                        		calendar.setTime(date);
                                                        		calendar.add(Calendar.DAY_OF_MONTH,-1);
                                                        		date = calendar.getTime();
                                                				if(!mapper.setDataByDB2Name("SERV_PERFORMED_DTE",sdf.format(date))){
                                                					rptMsg.append("Not able to set SERV_PERFORMED_DTE to " + sdf.format(date));
                                                				}
                                                			}
                                                			dateString =  mapper.getDataByDB2Name("SERVICE_DTE");
                                                			if (dateString != null){
                                                				date = sdf.parse(dateString);
                                                        		calendar.setTime(date);
                                                        		calendar.add(Calendar.DAY_OF_MONTH,-1);
                                                        		date = calendar.getTime();

                                                				if(!mapper.setDataByDB2Name("SERVICE_DTE",sdf.format(date))){
                                                					rptMsg.append("Not able to set SERVICE_DTE to " + sdf.format(date));
                                                				}
                                                			}
//                                                			 Add GENEARTED_CODE field to sql statement for generated records
                                                			sqlStatement = mapper.generateInsertValues(1, wwcdeMap, genMap);
                                                			sqlError = executeSQLStatement(sqlStatement, db2Conn);
                                                			//  If SQL error occured, will not increase the generated count
                                                			 if (!sqlError.equals("")){
                                                				 errorCnt++;
                                                				 generated_err_cnt++;
                                                			 }else{
                                                				 generatedCnt++;
                                                			 }

                                                		}

	                                            		// Report errors when inserting generated records
	                                                	if (!sqlError.equals("")){
	                                            			rptMsg.append("SQL Error:  " + sqlError + NEWLINE);
	                                            			rptMsg.append("SQL Statement:  " + sqlStatement + NEWLINE);
	                                            			rptMsg.append("Total error records (generated records from the record below):  " + generated_err_cnt + NEWLINE);
	                                                        rptMsg.append(reader.currentLine() + NEWLINE + NEWLINE);
	                                                        errMsg.append(reader.currentLine() + NEWLINE);
	                                                	}
                                                	}
                                                }
                                            }
                                        } else {
											//  not  (mapper.feedLine(nextLine)
                                            //  Data row did not pass validatation or other mapper errors occurred.  The method
                                            //  mapper.getlineError() will return any mapper related errors.  The error message
                                            //  is logged in the report and the erroneous data row is seperated into the error
                                            //  log (errMsg).  This error log will later be saved and left behind in the data directory.
                                            errorCnt++;
                                            rptMsg.append(mapper.getlineError());
                                            rptMsg.append(reader.currentLine() + NEWLINE + NEWLINE);
                                            errMsg.append(reader.currentLine() + NEWLINE);
                                        }

                                    }
                                    if (errorCnt > 100){
                                    	errMsg.append("Too many errors,processing stop.");
                                    	rptMsg.append("Too many errors,processing stop.");
                                    	reader.close();
                                    	File errorDir=new File(dir.getPath()+"/errors");
                                        if (!errorDir.exists()){
    										errorDir.mkdir();
    									}
                                        writeToFile(errorDir.getPath() + "/(ERROR)" + working_files_list[x].getName(), header, errMsg.toString());
                                        result = rptMsg.toString();
                                        return(result);
                                    }
                                }
                                //  We are done with this file so close it
                                reader.close();
                                //  We are done with this file so move it into the monthly processed directory unmodified.
                                moveFileToProcessed(working_files_list[x]);

                                //  If we had some error rows then we will leave those rows left behind for manual repair.
                                if (errorCnt > 0) {
                                    File errorDir=new File(dir.getPath()+"/errors");
                                    if (!errorDir.exists()){
										errorDir.mkdir();
									}
                                    writeToFile(errorDir.getPath() + "/(ERROR)" + working_files_list[x].getName(), header, errMsg.toString());
                                    errorDir=null;

                                }
                            } else {
                                rptMsg.append("Application Error:  Could not Validate Mapper XML file with CSV Data file." + NEWLINE);
                            }
                        } else {
                            rptMsg.append("Application Error:  CSV File Name [" + working_files_list[x].getName() + "] does not contain file validation string [" + mapper.getCSVFileNameVerificationString() + "]" + NEWLINE);
                        }
                    } catch (Exception mapperExc) {
                        rptMsg.append(mapperExc.getMessage() + NEWLINE);
                    }

                    //  Print the summary statistics
                    rptMsg.append("------------------------------------------------------------------------" + NEWLINE);
                    rptMsg.append("-  Successfully Processed: " + Integer.toString(successCnt) + " | Erroneous: " + Integer.toString(errorCnt));

                    if (CSVDataDir.endsWith(APPROVED_TRANS_DIR)) {
						rptMsg.append(" | Approved Data Warnings: " + Integer.toString(approvedWarningCnt));
					}

                    // Added report about generated records for New trans
                    if (CSVDataDir.endsWith(NEW_TRANS_DIR) && (CSVDataDir.indexOf("appr_sumry") <= 0)  && (CSVDataDir.indexOf("appr_fee15") <= 0)) {
                    	rptMsg.append(" | Generated Records: " + Integer.toString(generatedCnt));
                    }
                    rptMsg.append(NEWLINE);
                    rptMsg.append("------------------------------------------------------------------------" + NEWLINE);

                }  //end working_file
            }
        } else {

        }
        //  Send the processing results of all CSV files to the calling module.
        result = rptMsg.toString();
        return(result);
    }


    /***************************************************************************************************
    *  NAME:  writeToFile
    *
    *  PURPOSE:  This method is used to save the contents of two strings to file.  If the input file
    *  name already exists then the file is overwritten.  The contents of variable msg must be not null
    *  in order for the file to be written.  Header variable is optional.
    *
    *  INPUTS:  1.  String filePath - This is fully qualified file path to save the file to.
    *           2.  String header - This represents a header string for the file.
    *
    *  OUTPUTS:  True if the file was saved successfully, False otherwise.
    *
    *  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
    *
    ***************************************************************************************************/
    private static boolean writeToFile(String filePath, String header, String msg) {
        boolean result;
        FileWriter outFile;

        //  Try to create the output file and catch any IO exception and print to screen.
        try {
            outFile = new FileWriter(filePath);
            //  If we have something to save...
            if (!msg.equals("")) {
                //  If there was a header the write that out first
                if (!header.equals("")) {
                    outFile.write(header + NEWLINE);
                }
                outFile.write(msg);
                outFile.close();
            }
            result = true;

        //  Catch IO and print error to screen
        } catch (IOException io) {
            System.out.println("Failed writing to file [" + filePath + "]");
            System.out.println(io.getMessage());
            result = false;
        }

        return(result);
    }

    /***************************************************************************************************
    *  NAME:  moveFileToProcessed
    *
    *  PURPOSE:  This routine is used to move the input file to an archive subdirectory.  The archive
    *  subdirectory is created if one does not exist.  The archive subdirectory name is generated based
    *  on the current year and month (eg 2006-January).  Files are moved in the corresponding
    *  subdirectory depending on the current date.  File contents are moved in their original state
    *  without any modification.
    *
    *  N.B. If the current file name already exists in the archive subdirectory then the file is
    *  archived with a new name iterated by 1
    *  ie file.csv may become file-1.csv or file-n.csv where n is the next available number.
    *
    *  INPUTS:  1.  File inFile - This is a java file object that we want to move to the archive
    *               subdirectory
    *
    *  OUTPUTS:  Returns True if the file was successfully moved, False otherwise.
    *
    *  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
    *
    ***************************************************************************************************/
    private static boolean moveFileToProcessed(File inFile) {
        boolean result;
        File dest, dir;
        String destDir, fileName, fileExt, month, year;
        SimpleDateFormat formatter;
        Date currentDate;

        //  Find the current year and month.  The month is displayed as the full string (ie January)
        currentDate = new Date();
        formatter = new SimpleDateFormat("yyyy");
        year = formatter.format(currentDate);
        formatter.applyPattern("MMMM");
        month = formatter.format(currentDate);

        //  Setup and create the subdirectory if it does not exist
        destDir = inFile.getParent() + "/" + year + "-" + month;
        // Destination directory
        dir = new File(destDir);
        dir.mkdir();

        // Full Destination File Path
        dest = new File(destDir + "/" + inFile.getName());
        int i = 0; int pos = -1;

        //  We check to see if the current file name already exists in the destination subdirectory.
        //  If is exists then the file is archived with a new name iterated by 1
        //  ie file.csv may become file-1.csv or file-n.csv where n is the next available number.
        //  This process is repeated until an available name is found.
        while (dest.exists()) {
            i++;
            pos = inFile.getName().lastIndexOf(".");
            //  If the file has an extension then we insert the iterated number at the
            //  end of the file name and before the .extension
            if (pos > 0) {
                fileExt = inFile.getName().substring(pos);
                fileName = inFile.getName().substring(0,inFile.getName().lastIndexOf("."));
                fileName = fileName + "-" + Integer.toString(i) + fileExt;
            } else {
                fileName = inFile.getName() + "-" + Integer.toString(i);
            }
            dest = new File(destDir + "/" + fileName);
        }

        result = inFile.renameTo(dest);

        return(result);
    }

    /***************************************************************************************************
    *  NAME:  generateTimeStamp
    *
    *  PURPOSE:  This is a general purpose routine that generates a string time stamp in the form of
    *  2006-01-16 at 13:58:34 EST
    *
    *  INPUTS:  N/A
    *
    *  OUTPUTS:  Returns the current time stamp in string format
    *
    *  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
    *
    ***************************************************************************************************/
    private static String generateTimeStamp() {
        String result;
        SimpleDateFormat formatter;

        formatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
        result = formatter.format(new Date()) + NEWLINE + NEWLINE;

        return(result);
    }

    /***************************************************************************************************
    *  NAME:  verifyCSVFileName
    *
    *  PURPOSE:  This routine is used to verify that the input file name contains a key word string
    *  The fileNameVerificationString is an optional parameter in the XML mapper file that can be used
    *  to validate CSV files for their content.
    *
    *  ie If processing reject transactions we can optionally specify that all input CSV file names
    *  contain the word 'reject'.  This will help identify a CSV file with a particular type of
    *  transaction ensuring CSV files are not incorrectly processed as a different transaction.
    *
    *  INPUTS:  1.  String fileName - File name to be validated
    *           2.  String fileNameVerificationString - String to search for in fileName.
    *
    *  OUTPUTS:  True if the fileNameVerificationString was found in the fileName or if the
    *  fileNameVerificationString is null (indicating not to test).   False if it does not
    *  pass the test.
    *
    *  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
    *  Change History:
    *  Jan 2008 - 	C3220 change return value type from boolean to String
    *  				return value:
  	*					NOTREQUIRED: if file name verification string is not required.
  	*					NOTFOUND: if file name verification string is required but canot find.
  	*					LENOVO/LITA: if file name verification string is required and is found.
    *
    ***************************************************************************************************/
    private static String verifyCSVFileName(String fileName, String fileNameVerificationString) {
        String result;
        String keywords[] = null;
        result = "";

        //  If fileNameVerificationString is "" then we do not verify the file Name, return True
        if (fileNameVerificationString.equals("")) {
            result = "";
        }else{
	        //  If fileNameVerificationString is contained in the fileName, return True
	        keywords = fileNameVerificationString.split(",");
	        for(int  i=0;i<keywords.length;i++){
		        if (fileName.toUpperCase().indexOf(keywords[i].toUpperCase()) >= 0)  {
		            result = keywords[i];
		            break; //at least one fileNameVerificationString is contained in the fileName, return True

		        //  Else the test failed so return False.
		        } else {
		            result = NOTFOUND;
		        }
	        }
	        if(result.equalsIgnoreCase("invoice") || result.equalsIgnoreCase("reject")){
	        	result = APP_REJ_BY_LENOVO;
	        }else if (result.equalsIgnoreCase("accepted") || result.equalsIgnoreCase("unaccepted")){
	        	result = APP_REJ_BY_LITA;
	        }
        }

        return(result);
    }


    /***************************************************************************************************
    *  NAME:  processApprovedUpdate
    *
    *  PURPOSE:  Automated approved record load that will allow change of World Wide Code
    *            and other data
    *
    *  HIGH LEVEL ALOGRITHM:  Search for the originating business line using TRANSACTION_ID acorss all detail tables
    *                         If business line not found, flag as error and handle as errors are handled today. Exit from this method.
    *                                   This should never occur
    *                         If business line found then
	*
    *                               UPDATE LENOVO.<Business_Line_found_detail_table>
    *                                   SET STATUS = 'APPROVED', BILLABLE = 'Y'
    *                                   WHERE TRANSACTION_ID = <Transation_id from csv file>
    *                               DONE.
    *
    *
    *
    * @param mapper - This is the mapper object that is used to map CSV data to DB2.
    *                 The mapper object must be in a validated state and have been feed a data row.
    * @param db2Conn - Valid and active connection object
    * @return -  An error string.  If no error occurred the result is null.
    *
    *
    *  CREATED BY:  Cindy Chow  January, 2007  for C2719
    *
    ***************************************************************************************************/
      private static  Object[] processApprovedUpdate(DataMapper mapper, Connection db2Conn, String app_rej_by ){   // C3220
          Object[] result = new Object[2];   // C2719 Victor Monaco  // VM Added new data error element
          String transaction_id = null;
          String transaction_id_noQuote = null;
          ArrayList resultList = null;
          String targetDetailTb = null;
          String sqlStatement = "";


          result[0] = ""; result[1] = "";

          //  Pick up the following data elements by name from the data row that was fed into the mapper.
          transaction_id = mapper.getSQLDataByCSVIndexDesc("TRANSACTION_ID");
          //Remove the quote ("'" before and after transaction_id
          transaction_id_noQuote = transaction_id.replaceAll("'", "");

          // Get the original business line and the reject_id;
          // This will execute the generated SQL statement.

          resultList = executeSEARCHBL01_P(db2Conn, transaction_id_noQuote) ;

          Iterator it = resultList.iterator();
          result[0] = (String) it.next();

          //  If it is not empty, it means there are errors
          //  If a SQL Error occurred during execution then sqlError will contain the error
          //  message.  The error message is logged in the report and the erroneous data row
          //  is seperated into the error log (errMsg).  This error log will later be saved
          //  and left behind in the data directory.
          if (!result[0].equals("")){
              return result;      // exit from this method

          }

          targetDetailTb = (String) it.next();

          if (targetDetailTb.trim().equalsIgnoreCase("")) {
			result[1] = "Transaction Id " + transaction_id_noQuote + " could not be found in our database for Approval.";
			return result;     // exit from this method
		  }

          sqlStatement = "UPDATE LENOVO." + targetDetailTb + " SET STATUS = 'APPROVED', BILLABLE = 'Y'";
          if (app_rej_by.length() > 0 ) {
			sqlStatement = sqlStatement + ", APP_REJ_BY = '" + app_rej_by + "'";
          }
          sqlStatement = sqlStatement + " WHERE TRANSACTION_ID = " + transaction_id ;

          // This will execute the SQL statement.
          result[0] = executeSQLStatement(sqlStatement, db2Conn);

          return result;

      }


    /***************************************************************************************************
    *  NAME:  executeSEARCHBL01_P
    *
    *  PURPOSE:  Execute Stored Procedure SEARCHBL01_P
    *
    * @param con - Valid and active connection object
    * @param transaction_id - transaction_id to be searched using Stored Procedure LENOVO.SEARCHBL01_P
    * @return
    *
    *
    *  Signature of Stored Procedure SEARCHBL01_P:
    *                       CREATE PROCEDURE LENOVO.SEARCHBL01_P
    *                                   (IN _in VARCHAR(14),
    *                                    OUT targetDetailTb     VARCHAR(18),
    *                                    OUT rejectId           VARCHAR(20) )
    *
    *
    *  CREATED BY:  Cindy Chow, Jan 2007
    *
    ***************************************************************************************************/

    private static ArrayList executeSEARCHBL01_P(Connection con, String transaction_id ) {
            String result = "";
            CallableStatement cstmt = null;
            String targetDetailTb = null;
            String rejectId = null;

            ArrayList resultList = new ArrayList ();

            try{
                //Create a CallableStatement object
                cstmt = con.prepareCall("CALL LENOVO.SEARCHBL01_P(?,  ?, ?)");

                // Set input parameter
                cstmt.setString( 1, transaction_id );

                // Register output parameters
                cstmt.registerOutParameter (2, Types.VARCHAR);
                cstmt.registerOutParameter (3, Types.VARCHAR);

                cstmt.execute();

                // Get the output parameter values
                targetDetailTb = cstmt.getString(2);
                rejectId = cstmt.getString(3);

                con.commit();
                //  Catch any exceptions during the process and set them up in the result error string.
            } catch (SQLException sqlEx) {
                    if (sqlEx.getSQLState().equals(SQLState_NOT_FOUND)) {
                        result = "Transaction_id=" + transaction_id + " not found. " + sqlEx.getMessage() ;
                    } else {
                        result = "Error in execution of executeSEARCHBL01_P() for transtion_id= " + transaction_id + ". " + sqlEx.getMessage();
                    }
                    try{
                        con.rollback();
                    } catch (SQLException sqlRb) {
                        result = "Error in rollback of executeSEARCHBL01_P() for transtion_id= " + transaction_id + ". " + sqlEx.getMessage();
                    }

            //  Make sure we close all resources before exiting the routine.
            } finally {
                if (cstmt != null) {
                    try {
                        cstmt.close();
                        } catch (SQLException sqlEx) {
                            cstmt = null;
                    }
                }

            }   // End of finally

            resultList.add(result);
            resultList.add(targetDetailTb);
            resultList.add(rejectId);

            return (resultList);

    }  // End of executeSEARCHBL01_P


    /***************************************************************************************************
    *  NAME:  getOriginatingRejectBusinesLine
    *
    *  PURPOSE:  Given an input transaction id, this method will return a string that represents the
    *			 the reject business line table that the transaction id can be found in.
    *			 ie LENOVO.BF_REJECT, LENOVO.PL_REJECT  ETC...
    *
    *  INPUTS:  1.  String transaction_id - Raw data transaction id from CSV file
    *           2.  Connection db2conn - This is the active database connection object
    *
    *  OUTPUTS:  Returns the reject table name that the transaction id can be found in
    *
    *  CREATED BY:  Victor Monaco  Feb, 2007 - IBM  C2719
    *
    ***************************************************************************************************/
	private static String getOriginatingRejectBusinesLine(String transaction_id, Connection db2Conn) {
		String targetRejectTb = "";
		String error = "";
		String targetDetailTb = "";
		ArrayList resultList = null;

		resultList = executeSEARCHBL01_P(db2Conn, transaction_id) ;

        Iterator it = resultList.iterator();
        error = (String) it.next();
        //If it is not empty, it means there are errors
        //  If a SQL Error occurred during execution then sqlError will contain the error
        //  message.  The error message is logged in the report and the erroneous data row
        //  is seperated into the error log (errMsg).  This error log will later be saved
        //  and left behind in the data directory.
        if (!error.equals("")){
            return "";      // exit from this method
        }

        targetDetailTb = (String) it.next();
        targetRejectTb = "LENOVO." + targetDetailTb.substring(0,targetDetailTb.indexOf("_")) + "_REJECT" ;

		return(targetRejectTb);
	}


	 /***************************************************************************************************
	    *  NAME:  getWWCDEAddDuplicatingRows
	    *
	    *  PURPOSE:  This method is used to create a list of SDW_COMP_ID which need one record per service call
	    *
	    *  INPUTS:   1.  Connection con - This is the active database connection object
	    *
	    *  OUTPUTS:  This routine returns an error string.  If no error occurred the result is a blank string.
	    *
	    *
	    *  CREATED BY:  Hang Shi,  March, 12 2009 - C3771
	    *
	    ***************************************************************************************************/
	private static String getWWCDEAddDuplicatingRows(Connection db2Conn){

     String sqlStatement = "";
     String result = "";
     Statement stmt = null;
     ResultSet rs = null ;
     //ArrayList wwcdeList = new ArrayList();

     //get GEN1 and GEN2 string from key table

     String sqlStr = "";
     String genStr = "";
     String idStr1 = "";
     String idStr2 = "";
     String[] wwCode1 = null;
     String[] wwCode2 = null;
     try {
    	 sqlStr= "SELECT KEY, STRING_VALUE FROM LENOVO.KEYS WHERE KEY IN ('GEN1','GEN2')";
		 stmt = db2Conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY );
	     rs = stmt.executeQuery(sqlStr);
	     while (rs.next()) {
	    	 genStr = rs.getString(1).trim();
	    	 if (genStr != null && genStr.length()>0){
	    		 if(genStr.equalsIgnoreCase("GEN1")){
	    			 idStr1 = rs.getString(2).trim();
	    		 }else if(genStr.equalsIgnoreCase("GEN2")){
	    			 idStr2 = rs.getString(2).trim();
	    		 }
	         }
	     }
	     wwCode1 = idStr1.split(",");
	     idStr1 = "";
	     wwCode2 = idStr2.split(",");
	     idStr2 = "";
	     for(int i = 0; i < wwCode1.length-1; i ++){
	    	 idStr1 += "'" + wwCode1[i].trim() + "'," ;
	     }
	     idStr1 += "'" + wwCode1[wwCode1.length-1].trim() + "'";

	     for(int i = 0; i < wwCode2.length-1; i ++){
	    	 idStr2 += "'" + wwCode2[i].trim() + "'," ;
	     }
	     idStr2 += "'" + wwCode2[wwCode2.length-1].trim() + "'";

	     sqlStatement = "SELECT SDW_COMP_ID, 'GEN1' as KEY FROM LENOVO.SDW_COMPONENTS WHERE WW_CDE IN (" +idStr1 + ")"
	 			+ "UNION "
	 			+ "SELECT SDW_COMP_ID, 'GEN2' as KEY FROM LENOVO.SDW_COMPONENTS WHERE WW_CDE IN (" + idStr2 + ")"  ;
         stmt = db2Conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY );
         rs = stmt.executeQuery(sqlStatement);
         while (rs.next()) {
        	 if (rs.getString(1).trim() != null || !rs.getString(1).trim().equalsIgnoreCase("")){
        		 ww_code_list.add(rs.getString(1).trim());
        		 wwcdeMap.put(rs.getString(1).trim(), rs.getString(2).trim());
             }
         }
     } catch (SQLException sqlEx){
 	    result = "Error in execution of getWWCDEAddDuplicatingRows()" + NEWLINE ;
     } finally {
          if (rs != null) {
             try {
                 rs.close();
                 } catch (SQLException sqlEx) {
                     rs = null;
             }
          }
         if (stmt != null) {
              try {
                  stmt.close();
              } catch (SQLException sqlEx) {
                  stmt = null;
              }
          }
      }   // End of finally

      try {db2Conn.commit()   ;
      } catch (SQLException sqlEx) {
        }
     return result;
	}

}  // End of Class
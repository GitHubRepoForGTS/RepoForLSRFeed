package lib.lenovo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.*;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.*;
import org.xml.sax.*;
import java.sql.Timestamp;





/***************************************************************************************************
*  CLASS:  DataMapper
*
*  PURPOSE:  The DataMapper class is designed to be a re-useable object for use as a data feed into a
*  relational database. Currently, the application that uses this class has CSV files as the
*  data source.  This object receives data row by row (feedLine) in the form of an Array.  One
*  array element for each column in a row.  This layer of abstraction can easily accommodate other
*  sources of data such as other types of files and/or other database result sets as data sources.
*
*  USAGE:  An overall view of this objects functionality can be depicted with the following
*  question and answer.
*
*  Question: If we take CSV files as a sample data source then the question becomes:
*  I have a CSV file that contains many data rows and columns.  I am only interested in certain columns
*  of the CSV file.  Also the data needs to be validated before I try and import it.
*
*  How do I get the data from the CSV file into my relational database?
*
*  Answer:
*  This class can take the CSV file, process the columns of interest, validate the data, and
*  prepare a SQL Insert statement without any modifications to this code.  The calling program can
*  then execute the statement on the target database.  This module also goes a long way to
*  produce dynamic Update statements as well.
*
*  Generally  speaking, we send a row of data (source) to the Data Mapper.  The Data
*  Mapper parses and validates the data and sends back a complete SQL Insert statement.
*
*  Features:
*  The generic nature of this Class is achieved through XML configuration files sometimes referred to
*  as the XML mapper file or XML config file.  This XML mapper file is fed to the constructor for this
*  object.  The XML file contains all of the information that is required to map a data source (CSV in
*  this case) to a relational database (DB2 in this case).  The XML file tells this object which columns
*  from the data source we are interested in.  It also tells us the column data type and any optional
*  validations that we wish to perform before we construct the SQL statement.
*
*  Since the mapping between the source and target is done through independent XML files it is easy to
*  create an application (that uses this module) to insert and update many different tables with
*  different sources of data and different DB2 Table layouts.
*
*
*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
*				Ka-Lai Wong January, 2006 - IBM
*
*  ---------------------------------------------------------------------------------------
*  Modification History
*  ---------------------------------------------------------------------------------------
*  January 2006 - First Release 1.0
*
*  March 2006 - C2246 Post UAT Change Request
*             - Add a new field validation feature which will check that a number is not equal
*			    to a specific value.  This not equal value will be read from the XML mapper file.
*
*  Feb 2007 - Modified the decimal regular expression to accept values in format
*             such as: +0000000123.33 with leading + sign
*  Jan 2008 - C3220 Added the overloaded method:public String generateInsertValues(Object [][] colNameValuePairs)
*  March 2009 - C3771 HS Added the overloaded method generateInsertValues
*  2009-10-09 Mark Ma - C3992 add public method setDataByDB2Name
*  2009-12-17 MM - C4046 using SDW_COMP_ID column instead of WW_CDE
*  2010-03028 MM - C4327 modify the LSR Feed tool to allow data in DB2 TIMESTAMP format to be inserted.
***************************************************************************************************/
public class DataMapper {
	private static final String NEWLINE = System.getProperty("line.separator");

	//  XML nodes
	private static final String TARGET_TABLE = "targetTable";
	private static final String COLUMN = "column";
	private static final String DB2_COL_NAME = "db2ColName";
	private static final String CSV_MAP_TO = "CSVMapTo";
	private static final String CSV_INDEX = "CSVIndex";
	private static final String DATA_TYPE = "dataType";
	//  XML attributes
	private static final String CSV_FILENAME_MUST_CONTAIN = "fileNameMustContain";
	private static final String NAME = "name";
	private static final String NUMBER = "number";
	private static final String MAX_LENGTH = "maxLength";
	private static final String DATE_FORMAT = "dateFormat";
	private static final String TIME_FORMAT = "timeFormat"; 
	private static final String TIMESTAMP_FORMAT = "timestampFormat";
	private static final String MIN_VALUE = "minValue";
	private static final String MAX_VALUE = "maxValue";
	private static final String NOT_EQUAL_VALUE = "notEqValue";  //  C2246  Added NOT_EQUAL_VALUE attribute tag
	//  XML attribute (dataType) values
	private static final String STRING_TYPE = "STRING";
	private static final String INTEGER_TYPE = "INTEGER";
	private static final String DECIMAL_TYPE = "DECIMAL";
	private static final String DATE_TYPE = "DATE";
	private static final String TIME_TYPE = "TIME";
	private static final String TIMESTAMP_TYPE = "TIMESTAMP";

	//  The following default values indicate that XML optional values CSVIndex, maxLength, minValue, maxValue
	//  were not fetched from the  XML document.  These indicators are optional when defining the XML structure
	//  for a feed.  They are used for mapping and for validation.
	private static final int    CSV_INDEX_INDICATOR = -1;
	private static final int    MAX_LENGTH_INDICATOR = -1;
	private static final int    MIN_VALUE_INDICATOR = 135711;  //prime ascending
	private static final int    MAX_VALUE_INDICATOR = 117531;  //prime descending
	// C2246  Add new default indicator
	private static final int    NOT_EQUAL_VALUE_INDICATOR = 135711;  //prime ascending


	//  ******************************************************************************************
	//  *  Public Data Mapper Objects
	//  ******************************************************************************************

	//  The following two values are public just in case a future application needed to force
	//  validation to true or to set the target table directly in code rather than through
	//  the XML file.

	//  validated is set to True when the XML mapper file is successfully validated against a CSV
	//  file.  All of the data elements we are looking for in the XML file were found in the CSV
	//  file.  Every DB2 column was successfully mapped to a CSV column
	public boolean validated;

	// targetTable is used to hold the table name that we will be inserting/updating.  This is
	// typically read from the XML file.
	public String targetTable;


	//  ******************************************************************************************
	//  *  Private Data Mapper Objects
	//  ******************************************************************************************

	//	the columns arraylist is an array of columnDefinition objects. (see colDefinition for description)
	private ArrayList columns = new ArrayList();

	//  This is an optional validation string that is used to validate the CSV file name.  If
	//  this variable contains a string such as "reject" then the CSV file name must also
	//  contain this string somewhere in it's name.
	private String csvFileNameVerificationString;


	//  The column definition object is the center point of the data mapper.  A column definition
	//  object contains all of the information needed to map a CSV column to a DB2 column.  There are
	//  two logical pieces of information that is stored in this object.  Information relating to the
	//  mapping of a CSV column to a DB2 column and information relating to data from a particular
	//  row and column in CSV file.
	//
	//  1.  Mapping CSV to DB2  (All of this information is retrieved from the XML config file for a particular transaction)
	//
	//  	The following variables are used to describe how one column in CSV is mapped to one column in DB2
	//		db2ColName, CSVMapTo, dataType, CSVIndex, CSVIndexDesc
	//
	//		When mapping a CSV column to DB2 we need to know:
	//  	a)  What column from CSV do we want to map?
	//
	//			Ans:  CSVMapTo - If CSV file has a header then this is the name of the column
	//				  or
	//				  CSVIndex - If the CSV has no header then reference the column by position (first column in CSV is zero)
	//
	//		b)  If we intend to use the column data value in a) to insert/update data in DB2 then we need to
	//			know the name of the DB2 column?
	//
	//			Ans:  db2ColName - Name of DB2 column in database
	//
	//		c)  Since CSV files do not contain any information relating to the type of data for each
	//			column we need to gather this information?
	//
	//			Ans:  dataType - This object is used to describe the data type of the column and any
	//				  optional validation parameters.  (See dataType object)
	//
	//		c)  Sometimes the application that references this data mapper needs to reference a CSV
	//			column by name.  If the CSV file did not contain a header then there is no name associated
	//			with that column.  This is an optional variable that can be used to associate a CSV column
	//			index with a name.
	//
	//			Ans:  CSVIndexDesc - Give a CSV index a name to reference it by.
	//
	//	2.  Data for the column
	//		The following variables are used to describe the data from a particular row and column in CSV file.
	//		data, error, sqlData, preProcessedData.
	//
	//			a)  data -  This is used to store the raw data taken from the CSV file for a particular row and column
	//
	//			b)	preProcessedData - Since we are inserting/updating a DB2 database based on data coming from
	//				a CSV file, we need to pre process the data into a format the DB2 will accept (ie single quote
	//				needs to be escaped and dates need to be formatted a certain way)
	//
	//			c)  sqlData - This is a convenience data element that wraps SQL tags around data elements
	//				depending on the data type  (ie strings are wrapped with single quotes)
	//
	//			d)	error - This is used to hold any error messages associated with the data during processing
	//
	private class colDefinition {
		private String db2ColName;
		private String CSVMapTo;
		private dataType type;
		private int CSVIndex;
		private String CSVIndexDesc;
		private String data;
		private String error;
		private String sqlData;
		private String preProcessedData;


		//  This is one of the constructors for the column definition object.  Both constructors are
		//  basically the same except in one case, the CSV column is referenced by name and in the
		//  other case, the CSV column is referenced by positional index number.

		//  Construct column definition by CSV header name
		private colDefinition(String in_db2ColName, String in_CSVMapTo, dataType in_dataType) {
			this.db2ColName = in_db2ColName;
			this.CSVMapTo = in_CSVMapTo;
			this.type = in_dataType;
			this.CSVIndex = CSV_INDEX_INDICATOR;
			this.CSVIndexDesc = "";
			this.data = "";
			this.error = "";
			this.sqlData = "";
			this.preProcessedData = "";
		}

		//  Construct column definition by CSV index position.  The first column in CSV is index zero
		//  Since we referencing CSV columns by index, we optionally allow this column to have a
		//  column name description -> CSVIndexDesc.
		private colDefinition(String in_db2ColName, int in_CSVIndex, String in_CSVIndexDesc, dataType in_dataType) {
			this.db2ColName = in_db2ColName;
			this.CSVMapTo = "";
			this.type = in_dataType;
			this.CSVIndex = in_CSVIndex;
			this.CSVIndexDesc = in_CSVIndexDesc;
			this.data = "";
			this.error = "";
			this.sqlData = "";
			this.preProcessedData = "";
		}
	}


	//  This object is contained within a column definition object.  It is used to describe the data type
	//  and format of the column.  It is also used to specify any optional data validation that is needed
	//  for the column data.
	private class dataType {
		private String type;		//  Store the data type: STRING_TYPE, DATE_TYPE, INTEGER_TYPE, DECIMAL_TYPE
		private String dateFormat;	//  Store date format  (eg.  yyyy/mm/dd)
		private String timeFormat;	//  Store time format  (eg.  HH:MM:ss:mm)
		private String timestampFormat;// Store timestamp format  (eg.  YYYY-MM-DD-hh.mm.ss.zzzzzz)
		private int maxLength;		//  Do we want to validate for string length
		private float minValue;		//  Do we want to validate for number minimun value
		private float maxValue;		//  Do we want to validate for number maximum value
		// C2246
		private float notEqValue;   //  Do we want to validate inquality of a number

		// Constructor for string
		private dataType (String in_dataType,int in_maxLength) {
			this.type = in_dataType;
			this.dateFormat = "";
			this.timeFormat = "";
			this.timestampFormat ="";
			this.maxLength = in_maxLength;
			this.minValue = MIN_VALUE_INDICATOR;
			this.maxValue = MAX_VALUE_INDICATOR;
			// C2246
			this.notEqValue = NOT_EQUAL_VALUE_INDICATOR;
		}

		// Constructor for date, time, timestamp
		private dataType (String in_dataType,String in_dateFormat) {
			this.type = in_dataType;
			this.dateFormat = in_dateFormat;
			this.timeFormat = in_dateFormat;
			this.timestampFormat  = in_dateFormat;
			this.maxLength = MAX_LENGTH_INDICATOR;
			this.minValue = MIN_VALUE_INDICATOR;
			this.maxValue = MAX_VALUE_INDICATOR;
			// C2246
			this.notEqValue = NOT_EQUAL_VALUE_INDICATOR;
		}
		

		// Constructor for integer, decimal
		private dataType (String in_dataType,float in_minValue,float in_maxValue, float in_notEqValue) {
			this.type = in_dataType;
			this.dateFormat = "";
			this.timeFormat = "";
			this.timestampFormat = "";
			this.maxLength = MAX_LENGTH_INDICATOR;
			this.minValue = in_minValue;
			this.maxValue = in_maxValue;
			// C2246
			this.notEqValue = in_notEqValue;
		}

		//  Return the data type
		private String getDataType () {
			return (this.type);
		}
		//  Return the date format
		private String getDateFormat () {
			return (this.dateFormat);
		}
		 //  Return the time format
		private String getTimeFormat () {
			return (this.timeFormat);
		}
	//  Return the time format
		private String getTimestampFormat () {
			return (this.timestampFormat);
		}
		//  Return the string maximum length
		private int getMaxLength () {
			return (this.maxLength);
		}
		//  Return the number minimum value
		private float getMinValue () {
			return (this.minValue);
		}
		//  Return the number maximum value
		private float getMaxValue () {
			return (this.maxValue);
		}
		// C2246 Return the not equal number value
		private float getNotEqValue () {
			return (this.notEqValue);
		}
		//  Test for string data type
		private boolean isString() {
			return (this.type.equalsIgnoreCase(STRING_TYPE));
		}
		//  Test for date data type
		private boolean isDate() {
			return (this.type.equalsIgnoreCase(DATE_TYPE));
		}
	//  Test for time data type
		private boolean isTime() {
			return (this.type.equalsIgnoreCase(TIME_TYPE));
		}	
		//Test for time data type
		private boolean isTimestamp() {
			return (this.type.equalsIgnoreCase(TIMESTAMP_TYPE));
		}	
		//  Test for integer data type
		private boolean isInteger() {
			return (this.type.equalsIgnoreCase(INTEGER_TYPE));
		}
		//  Test for decimal data type
		private boolean isDecimal() {
			return (this.type.equalsIgnoreCase(DECIMAL_TYPE));
		}
	}



	/***************************************************************************************************
	*  NAME:  DataMapper
	*
	*  PURPOSE:  This is the Public Data Mapper Constructor.  When mapping a CSV file to create a DB2
	*  insert/update statement the first thing we need to do is load the XML configuration file
	*  that describes exactly how the CSV file relates to DB2.  The XML mapper config file tells us
	*  what table we will be inserting/updating to.  It also tells us the details of which columns
	*  from the CSV file we are interested in.  Basically, the XML file loads all of the information
	*  that was described in part one of the column definition object.
	*
	*  INPUTS:  1.  String inFileName - It's only parameter is the the path to the XML mapper config file.
	*
	*  OUTPUTS:  	N/A
	*				Can throw an exception to the calling program with error message.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public DataMapper(String inFileName) throws Exception {

		//  validated is initially set to false.  Once the data mapper has enough information to
		//  successfully find all of the CSV indexes then validated will be set to true.
		this.validated = false;

		//  This is an optional validation string that is used to validate the CSV file name.  If
		//  this variable contains a string such as "reject" then the CSV file name must also
		//  contain this string somewhere in it's name.
		this.csvFileNameVerificationString = "";

		//  loadXMLMapFile will parse the XML mapper file and load the DataMapper columns array with
		//  column definition objects.
		String errorMsg = loadXMLMapFile(inFileName);

		//  If XML parsing and the column definition objects were correctly set up then we try to
		//  validate if the mapper has all of it's CSV column indexes.  (This will only be true if
		//  the XML config file specified all columns by index rather than by header name)
		if (errorMsg.equals("")) {
			if (verifyColIndexes()) {
				this.validated = true;
			}
 		//  errorMsg contained a parse error so we can not continue.  Throw an exception to the calling
 		//  program with the error message.
		} else {
			throw new Exception(errorMsg);
		}
	}



	/***************************************************************************************************
	*  NAME:  loadXMLMapFile
	*
	*  PURPOSE:  This routine is used to load the XML mapper file which will setup the data mapper
	*  object with the columns array.  The columns array will contain all of the information that is
	*  needed to map a CSV file to DB2.  In addition to loading the columns array it will load the
	*  DB2 target table name.  The optional csvFileNameVerificationString variable will also be loaded
	*  if it exists in the XML mapper config file.
	*
	*  INPUTS:  1.  String inFileName - The file path to the XML mapper file
	*			2.  CONSTANTS - All XML constants listed in the constant declaration of the mapper
	*
	*  OUTPUTS:  This routine returns a single error message if one occurred.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*				Ka-Lai Wong	January, 2006 - IBM
	*
	***************************************************************************************************/
	private String loadXMLMapFile (String inFileName) {
		Document document;
		NodeList nodes_i, nodes_j;
		Node node_i, node_j;
		Element element_root, element_i, element_j;
		String result, elementTag_i, elementTag_j;
		String db2ColName, CSVMapTo, dataType, CSVIndexDesc, dateFormat, timeFormat, timestampFormat;
		int    CSVIndex, maxLength;
		//  C2246  added notEqValue to pick up the new not equal validation.
		float  minValue, maxValue, notEqValue;
		dataType dataAttributes;

		//  Initialize some variables
		result = "";
		document = null; db2ColName = null; CSVMapTo = null; CSVIndex = CSV_INDEX_INDICATOR; dataType = null; dataAttributes = null;


		//  Create DOM document for xml file
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		//  Turn on XML DTD validation
		factory.setValidating(true);

		//  Create errorHandler to handle any XML parsing errors.  This is an external error handler object/class
		//  which is part of the lenovo library
		XMLErrorHandler errorHandler = new XMLErrorHandler();

		//  Load the XML file into an XML document.  The XML doc will be parsed for syntax
		//  and validated against the DTD at this point.
		//  Register errorHandler to builder.  Any parse warning / error will invoke errorHandler
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(errorHandler);
			document = builder.parse(inFileName);

		//  General XML parse error
		} catch (SAXParseException spe) {
			result = result + "Application Error: XML parse failed! ";

		//  Error generated by this application (or a parser-initialization error)
		} catch (SAXException sxe) {
			result = result + "Application Error: Incorrect XML format! ";

		//  Parser with specified options can't be built
		} catch (ParserConfigurationException pce) {
			result = result + "Application Error: Parser with specified options cannot be built! ";

		//  I/O error
		} catch (IOException ioe) {
			result = result + "Application Error: XML File not found [" + inFileName + "]";
		}

		//  If builder.parse above was successful, errorCount and warningCount should be 0
		if (result.equals("")) {
			if (errorHandler.getErrorCount() > 0 || errorHandler.getWarningCount() > 0) {
				SAXException se = errorHandler.getFirstException();
				result = result + "Application Error: CSV is NOT processed because " + errorHandler.getErrorCount() + " error(s), " + errorHandler.getWarningCount() + " warning(s) are found in XML" + NEWLINE;
				result = result + "First error is: " + se.getMessage();
			}
		}

		//  If there were no XML errors and the file passed DTD validation then...
		if (result.equals("")) {
			//  Load xml elements from DOM document
			//  If the root node contains the optional CSV File Name Verification String
			element_root = document.getDocumentElement();
			this.csvFileNameVerificationString = element_root.getAttribute(CSV_FILENAME_MUST_CONTAIN);

			nodes_i = element_root.getChildNodes();
			//  Go through all nodes in the CSV map
			for (int i = 0; i < nodes_i.getLength(); i++) {
				node_i = nodes_i.item(i);
				if (node_i.getNodeType() == Node.ELEMENT_NODE) {
					element_i = (Element) node_i;
					elementTag_i = element_i.getTagName();

					//  If the node is Target Table then load dataMapper targetTable
					if (elementTag_i.equalsIgnoreCase(TARGET_TABLE)) {
						this.targetTable = element_i.getAttribute(NAME);
					}

					//  If the node is a column node then pick up the column attributes
					if (elementTag_i.equalsIgnoreCase(COLUMN)) {
						nodes_j = element_i.getChildNodes();

						//  Initialize attribute variables
						db2ColName = ""; CSVMapTo = "";	CSVIndex = CSV_INDEX_INDICATOR; CSVIndexDesc = ""; dataType = "";
						maxLength = MAX_LENGTH_INDICATOR; dateFormat = ""; minValue = MIN_VALUE_INDICATOR; maxValue = MAX_VALUE_INDICATOR;
						//  C2246 Added notEqValue default value
						notEqValue = NOT_EQUAL_VALUE_INDICATOR;

						//  Go through all of the attributes for a column node
						for (int j = 0; j < nodes_j.getLength(); j++) {
							node_j = nodes_j.item(j);
							if (node_j.getNodeType() == Node.ELEMENT_NODE) {
								element_j = (Element) node_j;
								elementTag_j = element_j.getTagName();

								//  Pick up DB2 column name
								if (elementTag_j.equalsIgnoreCase(DB2_COL_NAME)) {
									db2ColName = element_j.getAttribute(NAME);
								}
								//  Pick up the CSV map to header name
								if (elementTag_j.equalsIgnoreCase(CSV_MAP_TO)) {
									CSVMapTo = element_j.getAttribute(NAME);
								}
								//  Pick up the CSV position index
								if (elementTag_j.equalsIgnoreCase(CSV_INDEX)) {
									try {
										CSVIndex = Integer.parseInt(element_j.getAttribute(NUMBER));
										CSVIndexDesc = element_j.getAttribute(NAME);
									} catch (NumberFormatException nfe) {
										CSVIndex = CSV_INDEX_INDICATOR;
									}
								}

								//  Pick up data type information and any optional validation parameters.
								//  Once we have all of the data type information need then we will create
								//  a dataType called dataAttributes which will be stored along with the
								//  column definition object.
								if (elementTag_j.equalsIgnoreCase(DATA_TYPE)) {

									dataType = element_j.getAttribute(NAME);

									//  String
									if (dataType.equalsIgnoreCase(STRING_TYPE)) {
										try {
											maxLength = Integer.parseInt(element_j.getAttribute(MAX_LENGTH));
										} catch (NumberFormatException nfe) {
											maxLength = MAX_LENGTH_INDICATOR;
										}
										dataAttributes = new dataType(dataType, maxLength);
									}
									//  Date
									if (dataType.equalsIgnoreCase(DATE_TYPE)) {
										dateFormat = element_j.getAttribute(DATE_FORMAT);
										dataAttributes = new dataType(dataType, dateFormat);
									}
									//  Time
									if (dataType.equalsIgnoreCase(TIME_TYPE)) {
										timeFormat = element_j.getAttribute(TIME_FORMAT);
										dataAttributes = new dataType(dataType, timeFormat);
									}
									//  Timestamp
									if (dataType.equalsIgnoreCase(TIMESTAMP_TYPE)) {
										timestampFormat = element_j.getAttribute(TIMESTAMP_FORMAT);
										dataAttributes = new dataType(dataType, timestampFormat);
									}
									//  Integer or Decimal
									if (dataType.equalsIgnoreCase(INTEGER_TYPE) || dataType.equalsIgnoreCase(DECIMAL_TYPE)) {
										try {
											minValue = Float.parseFloat(element_j.getAttribute(MIN_VALUE));
										} catch (NumberFormatException nfe) {
											minValue = MIN_VALUE_INDICATOR;
										}
										try {
											maxValue = Float.parseFloat(element_j.getAttribute(MAX_VALUE));
										} catch (NumberFormatException nfe) {
											maxValue = MAX_VALUE_INDICATOR;
										}
										//  C2246 This Section - Pick up the optional not equal validation value
										try {
											notEqValue = Float.parseFloat(element_j.getAttribute(NOT_EQUAL_VALUE));
										} catch (NumberFormatException nfe) {
											notEqValue = NOT_EQUAL_VALUE_INDICATOR;
										}

										dataAttributes = new dataType(dataType, minValue, maxValue, notEqValue);
									}
								}

							}
						}

						//  Assume mandatory attributes db2ColName, CSVMapTo, datatype for this column
						//  were fetched successfully.

						//  Call this column definition constructor if CSV column was mapped by header name
						if (CSVMapTo.length() != 0) {
							columns.add(new colDefinition(db2ColName, CSVMapTo, dataAttributes));

						//  Call this column definition constructor if CSV column was mapped by positional index
						} else if (CSVIndex != CSV_INDEX_INDICATOR) {
							columns.add(new colDefinition(db2ColName, CSVIndex, CSVIndexDesc, dataAttributes));

						//  If the column is not mapped by header name or index then we have a problem.
						} else {
							result = result + "Application Error: XML Column must map to a CSV Column Name or CSV Column Number. " + NEWLINE;
						}


					}
				}
			}
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  feedLine
	*
	*  PURPOSE:  Once the data mapper has all of the information needed to map a CSV file to DB2.  We
	*  use this method to feed one line from the CSV file to mapper.  This method loops through all of
	*  the data mapper columns in the columns array (column definition objects).  For each column, we
	*  use the CSVIndex to find the data element in the input row.  Once we have the data we:
	*		a) Store the raw data
	*		b) Preprocess the data to the form acceptable to DB2
	*		c) Validate the data
	*		d) Wrap SQl around the data for convenience
	*
	*  INPUTS:  1.  String[] inputRow - One row from a CSV file.  Each data column is separated into
	*				array elements.
	*
	*  OUTPUTS:  Returns True if the input row was successfully processed.  If false was returned,
	*  the calling program can optionally read the error messages that lead to the false processing
	*  by calling the public method getlineError().
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public boolean feedLine (String[] inputRow) {
		boolean result;
		colDefinition col;

		result = true;

		for (int i = 0; i < columns.size(); i++) {
			col = (colDefinition)columns.get(i);
			//  Initialize the column data related variables.
			col.data = "";
			col.error = "";
			col.sqlData = "";
			col.preProcessedData = "";

			// This is to avoid an array out of bound errors.  Make sure that the imput row has
			// at least as many columns as the number read from CSVIndex
			if (inputRow.length > col.CSVIndex) {
				//  Pick up the raw data and store it without leading and trailing spaces
				col.data = inputRow[col.CSVIndex];
				col.data = col.data.trim();

				//  If we found some data then it will be pre-processed and validated
				if (!col.data.equals("")) {
					//  Pre-process col.data and validate dataType and value.  error will contain
					//  any validation errors
					col.error = validateColumn(col);

					//  If there was a validation  error
					if (!col.error.equals("")){
						result = false;
					//  The data is good so lets wrap SQL tags around the data so it will be
					//  easier to create dynamic insert/update statements later.
					} else {
						col.sqlData = wrapSQLColumn(col);
					}
				}

			//  The CSV column index read from XML map is outside of the range of columns in the input row.
			} else {
				col.error = "Data Error:  Can not map to column '" + col.CSVIndex + "' of input line";
				result = false;
			}
		}
		return(result);
	}



	/***************************************************************************************************
	*  NAME:  validateColumn
	*
	*  PURPOSE:  This method performs 3 tasks.
	*			 Step 1:  Preprocess raw data into a for that DB2 accepts.  Strings need to have single
	*					  quotes escaped.  Dates need to be in format yyyy-mm-dd
	*
	*			 Step 2:  Validate pre-processed data from step one against it's data type.  Numbers
	*					  need to contain digits only and dates need to be valid dates.  This is done
	*					  with regular expressions.
	*
	*			 Step 3:  Validate the data against any of the optional validation parameters that came
	*					  from the XML mapper file.  Strings can be validate for length.  Numbers for
	*					  min/max values.
	*
	*  INPUTS:  1.  colDefinition col - One column definition object (holds data, types, validation parameters)
	*			2.  CONSTANTS - MAX_LENGTH_INDICATOR, MIN_VALUE_INDICATOR, MAX_VALUE_INDICATOR
	*
	*  OUTPUTS:  This will return an error message if it could not pass validation
	*
	*  OTHER INFO:  Regular Expressions
	*
	*		Expression:  ^[^']*$
	*		Description:   This one matches all strings that do not contain the single quotation mark (').
	*		Matches:  [asljas], [%/&89uhuhadjkh], ["hi there!"]  [ More Details]
	*		Non-Matches:  ['hi there!'], [It's 9 o'clock], [''''']
	*
	*		Expression:  ^[-+]?\d*$
	*		Description:   Matches any integer number or numeric string, including positive and negative value characters (+ or -). Also matches empty strings.
	*
	*		Expression:  ^(\d|-)?(\d|,)*\.?\d*$
	*		Description:   Input for Numeric values. Handles negatives, and comma formatted values. Also handles a single decimal point
	*		Matches:  [5,000], [-5,000], [100.044]  [ More Details]
	*		Non-Matches:  [abc], [Hundred], [1.3.4]
	*
	*		Expression:  ^(?ni:(?=\d)((?'year'((1[6-9])|([2-9]\d))\d\d)(?'sep'[/.-])(?'month'0?[1-9]|1[012])\2(?'day'((?<!(\2((0?[2469])|11)\2))31)|(?<!\2(0?2)\2)(29|30)|((?<=((1[6-9]|[2-9]\d)(0[48]|[2468][048]|[13579][26])|(16|[2468][048]|[3579][26])00)\2\3\2)29)|((0?[1-9])|(1\d)|(2[0-8])))(?:(?=\x20\d)\x20|$))?((?<time>((0?[1-9]|1[012])(:[0-5]\d){0,2}(\x20[AP]M))|([01]\d|2[0-3])(:[0-5]\d){1,2}))?)$
	*		Description:	ISO date
	*		Matches:  2002-01-31|||1997-04-30|||2004-01-01
	*		Non-Matches:  2002-01-32|||2003-02-29|||04-01-01
	*
	*		C2719 New Regular Expression
	*		Expression:  ^[-+]?\d+(\.\d+)?$
	*		Description:   This matches any real number, with optional decimal point and numbers after the decimal, and optional positive (+) or negative (-) designation.
	*		Matches:  123|||-123.45|||+123.56
	*		Non-Matches:  123x|||.123|||-123.
	*
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*				Ka-Lai Wong	January, 2006 - IBM
	*
	***************************************************************************************************/
	private String validateColumn(colDefinition col) {
		String result, regExpString, formatString;
		Matcher m;
		SimpleDateFormat formatter;
		Date dateData;
		StringBuffer stringBuffer;

		//  Initialize some variables.
		result = ""; regExpString = "";	m = null; formatter = null;	dateData = null; stringBuffer = new StringBuffer("");


		//  (STEP 1) pre-process col.data into col.preProcessedData:

		//  If dataType is date, convert original date format in col.data into yyyy-MM-dd date format in col.preProcessedData
		if (col.type.isDate()) {
			// (a) set up original date format string from XML with correct case (lowercase y, d and uppercase M)
			formatString = col.type.getDateFormat();
			m = Pattern.compile("Y").matcher(formatString);
			formatString = m.replaceAll("y");
			m = Pattern.compile("m").matcher(formatString);
			formatString = m.replaceAll("M");
			m = Pattern.compile("D").matcher(formatString);
			formatString = m.replaceAll("d");

			//  Convert old format date in col.data into yyyy-MM-dd date in col.preProcessedData
			try {
				//  Create a SimpleDateFormat object using the original date format from XML
				formatter = new SimpleDateFormat(formatString);
				//  This is used to force strict date values so that java does not try to guess the
				//  date that was intended.  Feb 29 2006 is invalid because 2006 is not a leap year.
				//  Without setLenient(false) java would try to interpret this date as March 1, 2006.
				formatter.setLenient(false);
				//  Parse the date string into Date object using the original date format from XML
				dateData = formatter.parse(col.data,new ParsePosition(0));
				//  Change the Date's original format into DB2-recognizable format
				formatter.applyPattern("yyyy-MM-dd");
				//  Convert the re-formatted Date into string
				stringBuffer = formatter.format(dateData,stringBuffer,new FieldPosition(0));
				col.preProcessedData = stringBuffer.toString();
			} catch (IllegalArgumentException iae) {
				//  Invalid date format pattern
				result = "Application Error:  Column " + col.CSVIndex + " XML date format '" + col.type.getDateFormat() + "' is invalid";
			} catch (NullPointerException npe) {
				//  Empty date format pattern or date
				result = "Application Error:  Column " + col.CSVIndex + " XML date format '" + col.type.getDateFormat() + "' or date '" + col.data + "' is empty";
			}

		//  If dataType is String, convert any "'" in col.data to "''" in col.preProcessedData
		} else if (col.type.isString()) {
			m = Pattern.compile("'").matcher(col.data);
			col.preProcessedData = m.replaceAll("''");
		//  If dataType is Time, convert original time format in col.time into HH:mm:ss time format in col.preProcessedData
		}else if (col.type.isTime()) {				
			// (a) set up original date format string from XML with correct case (lowercase m, a)
			formatString = col.type.getTimeFormat();
			// a  Am/pm marker  
			m = Pattern.compile("A").matcher(formatString);
			formatString = m.replaceAll("a");
			// m  Minute in hour  
			m = Pattern.compile("M").matcher(formatString);
			formatString = m.replaceAll("m");

			//  Convert old format time in col.data into HH:mm:ss time in col.preProcessedData
			try {
				//  Create a SimpleDateFormat object using the original date format from XML
				formatter = new SimpleDateFormat(formatString);
				
				//  This is used to force strict date values so that java does not try to guess the
				//  date that was intended.  Feb 29 2006 is invalid because 2006 is not a leap year.
				//  Without setLenient(false) java would try to interpret this date as March 1, 2006.
				formatter.setLenient(false);
				//  Parse the date string into Date object using the original date format from XML
				dateData = formatter.parse(col.data,new ParsePosition(0));
				//  Change the Date's original format into DB2-recognizable format
				formatter.applyPattern("HH:mm:ss");
				//  Convert the re-formatted Date into string
				stringBuffer = formatter.format(dateData,stringBuffer,new FieldPosition(0));
				col.preProcessedData = stringBuffer.toString();
			} catch (IllegalArgumentException iae) {
				//  Invalid time format pattern					
					result = "Application Error:  Column " + col.CSVIndex + " XML time format '" + col.type.getTimeFormat() + "' is invalid";					
				
			} catch (NullPointerException npe) {
				//  Empty time format pattern or time				    
					result = "Application Error:  Column " + col.CSVIndex + " XML time format '" + col.type.getTimeFormat() + "' or time '" + col.data + "' is empty";
			}
			//  If dataType is Timestamp, convert original time format in col.timestamp into YYYY-MM-dd-HH.mm.ss.zzzzzz timestamp format in col.preProcessedData
		}else if (col.type.isTimestamp()) {
			formatString = col.type.getTimestampFormat();
			//  Convert old format time in col.data into HH:mm:ss time in col.preProcessedData
			try {				
				Timestamp ts = Timestamp.valueOf(col.data);		
				col.preProcessedData = ts.toString();
			} catch (Exception ex) {
				//  Invalid time format pattern					
				result = "Application Error:  Column " + col.CSVIndex + " XML timestamp format '" + col.type.getTimestampFormat() + "' is invalid.\r\n" + ex.getMessage();									
			}
		//  If dataType is other, copy col.data to col.preProcessedData as is
		} else {
			col.preProcessedData = col.data;
		}


        //  (STEP 2) if Step 1 result is ok then validate against regular expression

		//  Set up regular expression depending on dataType
        if (result.equals("")) {
			if (col.type.isString()) {
				regExpString = "";
			} else if (col.type.isInteger()) {
				regExpString = "^[-+]?\\d*$";
			} else if (col.type.isDecimal()) {
				//regExpString = "^(\\d|-)?(\\d|,)*\\.?\\d*$";  // C2719 Remove old Validation
				regExpString = "^[-+]?\\d+(\\.\\d+)?$";			// C2719 Add new validation to check for + sign
			} else if (col.type.isDate()) {
				regExpString = "^((((19|20)(([02468][048])|([13579][26]))-02-29))|((20[0-9][0-9])|(19[0-9][0-9]))-((((0[1-9])|(1[0-2]))-((0[1-9])|(1\\d)|(2[0-8])))|((((0[13578])|(1[02]))-31)|(((0[1,3-9])|(1[0-2]))-(29|30)))))$";
			} else if (col.type.isTime()) {
				regExpString = "^((([0]?[1-9]|1[0-2])(:|\\.)[0-5][0-9]((:|\\.)[0-5][0-9])?( )?(AM|am|aM|Am|PM|pm|pM|Pm))|(([0]?[0-9]|1[0-9]|2[0-3])(:|\\.)[0-5][0-9]((:|\\.)[0-5][0-9])?))$";
			} else if (col.type.isTimestamp()) {
				regExpString = "^((((19|20)(([02468][048])|([13579][26]))-02-29))|((20[0-9][0-9])|(19[0-9][0-9]))-((((0[1-9])|(1[0-2]))-((0[1-9])|(1\\d)|(2[0-8])))|((((0[13578])|(1[02]))-31)|(((0[1,3-9])|(1[0-2]))-(29|30)))) (20|21|22|23|[0-1]?\\d).([0-5]?\\d).([0-5]?\\d)(.[0-9]{0,6}))$";
			} else {
				result = "Application Error:  Unknown XML Column Data Type: " + col.type.getDataType().toUpperCase();
			}

			//  Validate col.preProcessedData using regular expression
			if (!regExpString.equals("")) {
				m = Pattern.compile(regExpString).matcher(col.preProcessedData);
				if (!m.matches()) {
					result = "Data Error:  Column " + col.CSVIndex + " (" + col.db2ColName +") item does not match type " + col.type.getDataType().toUpperCase() + ": " + col.data;
				}
			}
		}


		//  (STEP 3) If step 2 result is ok, validate col.data if it obeys the dataType's value constraint(s) in XML

		//  If dataType is date, no validation is needed here because it should already be done in step 1 and 2
		if (result.equals("")) {
			//  If dataType is String, validate its max length if given in XML
			if (col.type.isString() && col.type.getMaxLength() != MAX_LENGTH_INDICATOR) {
				if (col.data.length() > col.type.getMaxLength()) {
					result = "Data Error: Column " + col.CSVIndex + " (" + col.db2ColName + ") length " + col.data.length() + " is longer than expected maximum length of " + col.type.getMaxLength();
				}

			//  If dataType is integer / decimal, validate min and/or max value if given in XML
			//  C2246 Validate Not Equal Value for a number
			} else if (col.type.isInteger() || col.type.isDecimal()) {
				try {
					float x = Float.parseFloat(col.data);
					//  Validate against min value
					if (col.type.getMinValue() != MIN_VALUE_INDICATOR) {
						if (x < col.type.getMinValue()) {
							result = "Data Error: Column " + col.CSVIndex + " (" + col.db2ColName + ") value " + x + " is below the expected minimum " + col.type.getMinValue();
						}
					}
					//  Validate against max value
					if (col.type.getMaxValue() != MAX_VALUE_INDICATOR) {
						if (x > col.type.getMaxValue()) {
							result = "Data Error: Column " + col.CSVIndex + " (" + col.db2ColName + ") value " + x + " is above the expected maximum " + col.type.getMaxValue();
						}
					}
					//  C2246 This section - Validate against Not Equal Value
					if (col.type.getNotEqValue() != NOT_EQUAL_VALUE_INDICATOR) {
						if (x == col.type.getNotEqValue()) {
							result = "Data Error: Column " + col.CSVIndex + " (" + col.db2ColName + ") value " + x + " is equal to the excluded value " + col.type.getNotEqValue();
						}
					}

				//  If for some reason we could not create a number from the data.  (This should never happen here)
				} catch (NumberFormatException nfe) {
					result = "Data Error: Column " + col.CSVIndex + " (" + col.db2ColName + ") is not numeric: " + col.data;
				}
			}
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  wrapSQLColumn
	*
	*  PURPOSE:  This is a convenience method that will wrap data with SQL tags so that it is easier
	*  to create dynamic SQL insert/update statements.
	*			 a)  Strings - wrapped in single quotes
	*			 b)  Integer and Decimal are left as is
	*			 c)  Dates are wrapped in SQL date function  DATE('yyyy-mm-dd')
	*
	*  INPUTS:  1.  colDefinition col - One column definition object (holds data and type info etc...)
	*
	*  OUTPUTS:  The method returns a string which is the pre-Processed data from the column object
	*  wrapped with SQl tags.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	private String wrapSQLColumn(colDefinition col) {
		String result;

		//  If data type is String, wrapped in single quotes
		if (col.type.isString()) {
			result  = "'" + col.preProcessedData + "'";
		//  If data type is Integer, leave as is
		} else if (col.type.isInteger()) {
			result  = col.preProcessedData;
		//  If data type is Decimal, leave as is
		} else if (col.type.isDecimal()) {
			result  = col.preProcessedData;
		//  If data type is Date, wrap SQL date function around data
		} else if (col.type.isDate()) {
			result  = "DATE('" + col.preProcessedData + "')";
		//  If date type is Time
		} else if (col.type.isTime()) {
			result  = "TIME('" + col.preProcessedData + "')";	
		//  If date type is Timestamp
		} else if (col.type.isTimestamp()) {
			result  = "TIMESTAMP('" + col.preProcessedData + "')";	
		} else {
			result = "";
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  colIndexOf
	*
	*  PURPOSE:  This method is used to locate the CSV column index of a particular column name.  We
	*  take in the CSV header and the column (searchStr) that we want to find.  The CSV header line is
	*  already divided into separate array elements so we just need to find the array position of the
	*  searchStr.
	*
	*  INPUTS:  1.  String [] headerLine - CSV column title header divided into array elements.
	*			2.  String searchStr - The column index we want to find.
	*
	*  OUTPUTS:  Returns the CSV index (headerLine Array index) of the column we are looking for.
	*			 Returns -1 if the searchStr is not found in headerLine Array.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	private int colIndexOf (String [] headerLine, String searchStr) {
		int pos;

		//  Position not found -> -1
		pos = -1;
		if (headerLine.length > 0) {
			//  Loop through the array to find searchStr
			for (int i = 0; i < headerLine.length; i++) {
				if (headerLine[i].equalsIgnoreCase(searchStr)) {
					pos = i;
				}
        	}
		}

		return(pos);
	}



	/***************************************************************************************************
	*  NAME:  verifyColIndexes
	*
	*  PURPOSE:  This method is used to validate the DataMapper.  Every CSV column listed in the XML
	*  mapper file must have a corresponding CSV index associated with it.  If every CSV column listed
	*  in the XML file was identified by CSVIndex then this method will return True.  If any CSV column
	*  was listed by name then we must the CSV index associated with that name before this routine will
	*  return true.
	*
	*  INPUTS:  1.  Private DataMapper columns Array
	*
	*  OUTPUTS:  Returns True if every columnDefinition object in columns Array has a CSV Index
	*  associated with it.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	private boolean verifyColIndexes () {
		boolean result;
		colDefinition col;

		//  The the DataMapper columns array has some elements in it
		if (columns.size() == 0) {
			result = false;
		} else {
			result = true;
			//  Loop through all elements in columns array
			for (int i = 0; i < columns.size(); i++) {
				//  Pick up the columnDefinition object
				col = (colDefinition)columns.get(i);
				//  If the columnDefinition object has an index < 0 then it is not valid
				if (col.CSVIndex < 0) {
					result = false;
				}
			}
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  setColumnIndexes
	*
	*  PURPOSE:  This method is used to set the column CSV indexes based on the headerLine.  We loop
	*  through all of the columns in the columns array and for each columnDefinition object we set
	*  it's CSV index to the position where the header label (CSVMapTo) is found in headerLine.
	*
	*  INPUTS:  1.  String[] headerLine - CSV header labels divided into array elements
	*			2.  Private DataMapper columns Array
	*
	*  OUTPUTS:
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public boolean setColumnIndexes(String[] headerLine) {
		boolean result = false;
		colDefinition col;

		if (headerLine.length > 0) {
			//  Loop through the columns in the columns Array
			for (int i = 0; i < columns.size(); i++) {
				//  Get the current column
				col = (colDefinition)columns.get(i);
				//  Set the current columns CSVIndex based on the position
				//  that the CSVMapTo tag is found in the headerLine Array
				col.CSVIndex = colIndexOf(headerLine, col.CSVMapTo);
			}

		}

		//  Perhaps we found all of the CSV indexes that we need so let try to verify/validate the
		//  the DataMapper object
		if (verifyColIndexes() == true) {
					this.validated = true;
		}

		return(result);
	};



	/***************************************************************************************************
	*  NAME:  getDataByDB2Name
	*
	*  PURPOSE:  This method returns the raw data associated with the db2ColumnName input parameter.
	*
	*  INPUTS:  1.  String db2ColumnName - The DB2 name we want to find raw CSV data in.
	*			2.  Private DataMapper columns Array
	*
	*  OUTPUTS:  The raw data found in the columnDefinition  col.data variable
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
 	public String getDataByDB2Name(String db2ColumnName) {
		String result;

		colDefinition col;
		col = null;
		result = null;

		//  Loop through all of the columns in DataMapper columns array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);
			//  If the DB2 column name of this column is the same as the input db2 columns name then
			//  set the result to the raw data associated with the current column
			if (db2ColumnName.equalsIgnoreCase(col.db2ColName)) {
				result = col.data;
				break;
			}
		}

		return(result);
	}
 	
 	//C3992 start
 	/***************************************************************************************************
	*  NAME:  setDataByDB2Name
	*
	*  PURPOSE:  This method set the raw data associated with the db2ColumnName input parameter.
	*
	*  INPUTS:  1.  String db2ColumnName - The DB2 name we want to find raw CSV data in.
	*			2.  new value to be set 
	*
	*  OUTPUTS:  true / false
	*
	*  CREATED BY:  Mark Ma 2009-10-09
	*
	***************************************************************************************************/
 	public boolean setDataByDB2Name(String db2ColumnName,String value) {
		boolean result = true;

		colDefinition col;
		col = null;		

		//  Loop through all of the columns in DataMapper columns array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);
			//  If the DB2 column name of this column is the same as the input db2 columns name then
			//  set the result to the raw data associated with the current column
			if (db2ColumnName.equalsIgnoreCase(col.db2ColName)) {
				col.data = value;
				col.error = validateColumn(col);
				// If there was a validation  error
				if (!col.error.equals("")){
					result = false;
				//  The data is good so lets wrap SQL tags around the data so it will be
				//  easier to create dynamic insert/update statements later.
				} else {
					col.sqlData = wrapSQLColumn(col);
				}
				break;
			}
		}

		return(result);
	}

 	// C3992 end
	/***************************************************************************************************
	*  NAME:  getDataByCSVName
	*
	*  PURPOSE:  This method returns the raw data associated with the CSVColumnName input parameter.
	*
	*  INPUTS:  1.  String CSVColumnName - The CSV Column Name we want to find raw CSV data in.
	*			2.  Private DataMapper columns Array
	*
	*  OUTPUTS:  The raw data found in the columnDefinition  col.data variable
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public String getDataByCSVName(String CSVColumnName) {
		String result;

		colDefinition col;
		col = null;
		result = null;

		//  Loop through all of the columns in DataMapper columns array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);
			//  If the CSVMapTo name of this column is the same as the input CSVMapTo name then
			//  set the result to the raw data associated with the current column
			if (CSVColumnName.equalsIgnoreCase(col.CSVMapTo)) {
				result = col.data;
				break;
			}
		}
		return(result);
	}



	/***************************************************************************************************
	*  NAME:  getDataByCSVIndex
	*
	*  PURPOSE:  This method returns the raw data associated with the CSV Index input parameter.
	*
	*  INPUTS:  1.  int index - The CSV Index we want to find raw CSV data in.
	*			2.  Private DataMapper columns Array
	*
	*  OUTPUTS:  The raw data found in the columnDefinition  col.data variable
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public String getDataByCSVIndex(int index) {
		String result;

		colDefinition col;
		col = null;
		result = null;

		//  Loop through all of the columns in DataMapper columns array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);
			//  If the CSVIndex of this column is the same as the input index then
			//  set the result to the raw data associated with the current column
			if (index == col.CSVIndex) {
				result = col.data;
				break;
			}
		}
		return(result);
	}



	/***************************************************************************************************
	*  NAME:  getSQLDataByDB2Name
	*
	*  PURPOSE:  This method returns the sql data associated with the db2ColumnName input parameter.
	*
	*  INPUTS:  1.  String db2ColumnName - The DB2 name we want to find sql data in.
	*			2.  Private DataMapper columns Array
	*
	*  OUTPUTS:  The sql data found in the columnDefinition  col.sqlData variable
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
 	public String getSQLDataByDB2Name(String db2ColumnName) {
		String result;

		colDefinition col;
		col = null;
		result = null;

		//  Loop through all of the columns in DataMapper columns array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);
			//  If the db2ColumnName of this column is the same as the input db2ColumnName then
			//  set the result to the sql data associated with the current column
			if (db2ColumnName.equalsIgnoreCase(col.db2ColName)) {
				result = col.sqlData;
				break;
			}
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  getSQLDataByCSVName
	*
	*  PURPOSE:  This method returns the sql data associated with the CSVColumnName input parameter.
	*
	*  INPUTS:  1.  String CSVColumnName - The CSV Column Name we want to find sql data in.
	*			2.  Private DataMapper columns Array
	*
	*  OUTPUTS:  The sql data found in the columnDefinition  col.sqlData variable
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public String getSQLDataByCSVName(String CSVColumnName) {
		String result;

		colDefinition col;
		col = null;
		result = null;

		//  Loop through all of the columns in DataMapper columns array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);
			//  If the CSVMapTo name of this column is the same as the input CSVMapTo name then
			//  set the result to the sql data associated with the current column
			if (CSVColumnName.equalsIgnoreCase(col.CSVMapTo)) {
				result = col.sqlData;
				break;
			}
		}
		return(result);
	}



	/***************************************************************************************************
	*  NAME:  getSQLDataByCSVIndex
	*
	*  PURPOSE:  This method returns the sql data associated with the CSV Index input parameter.
	*
	*  INPUTS:  1.  int index - The CSV Index we want to find sql data in.
	*			2.  Private DataMapper columns Array
	*
	*  OUTPUTS:  The sql data found in the columnDefinition  col.sqlData variable
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public String getSQLDataByCSVIndex(int index) {
		String result;

		colDefinition col;
		col = null;
		result = null;

		//  Loop through all of the columns in DataMapper columns array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);
			//  If the CSVIndex of this column is the same as the input index then
			//  set the result to the sql data associated with the current column
			if (index == col.CSVIndex) {
				result = col.sqlData;
				break;
			}
		}
		return(result);
	}



	/***************************************************************************************************
	*  NAME:  getSQLDataByCSVIndexDesc
	*
	*  PURPOSE:  This method returns the sql data associated with the CSV Index Description input parameter.
	*
	*  INPUTS:  1.  String CSVIndexDesc - The CSV Index Description we want to find sql data in.
	*			2.  Private DataMapper columns Array
	*
	*  OUTPUTS:  The sql data found in the columnDefinition  col.sqlData variable
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public String getSQLDataByCSVIndexDesc(String CSVIndexDesc) {
		String result;

		colDefinition col;
		col = null;
		result = null;

		//  Loop through all of the columns in DataMapper columns array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);
			//  If the CSVIndexDesc of this column is the same as the input CSVIndexDescription then
			//  set the result to the sql data associated with the current column
			if (CSVIndexDesc.equalsIgnoreCase(col.CSVIndexDesc)) {
				result = col.sqlData;
				break;
			}
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  getlineError
	*
	*  PURPOSE:  This public method can be called after an error occurred in feedLine.  This routine
	*  will loop through the DataMapper columns array and for every column with an error
	*  (perhaps from data validation) we will append the error message to the result string.  The
	*  calling program can then display this error message to the user.
	*
	*  Errors will be displayed one per line.
	*
	*  INPUTS:  1.  Private DataMapper columns Array
	*
	*  OUTPUTS:  String of errors, one error per line.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public String getlineError() {
		String result;
		colDefinition col;

		col = null;
		result = "";

		//  Loop through all the the columns in the DataMapper columns Array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);
			//  If the current column has an error then append it to a new line in the result string.
			if (!col.error.equals("")){
				result = result + col.error + NEWLINE;
			}
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  generateUpdateValues
	*
	*  PURPOSE:  This is a convenience method that can help dynamically generate a portion of an
	*  SQL update statement.  The idea is to generate a comma separated list of update values to
	*  be used in a DB2 update statement such as:
	*
	*  MACHINE = 'ASDF', SERIAL_NUM = '1234SS', COST = 12, BILL_DATE  = DATE('2006-01-31')
	*
	*  The algorithm for generating this list is as follows:  If the XML mapper file contains a
	*  DB2 Column Name and a CSVIndex or CSVMapTo Name then we can generate the list above.  The calling
	*  program will still need to generate the WHERE portion of the SQL statement
	*
	*  INPUTS:  1.  Private DataMapper columns Array
	*
	*  OUTPUTS:  A portion of a dynamically generated update SQL statement.  See example above.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public String generateUpdateValues() {
		String result;
		colDefinition col;

		col = null;
		result = "";

		//  Loop through all the the columns in the DataMapper columns Array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);

			//  If the db2Column Name is present then add this and it's associated sqlData value
			//  to the result set.  Seperate by comma
			if (!col.db2ColName.equals("")){
				if (!col.data.equals("")){
					result = result + col.db2ColName + " = " + col.sqlData + ",";
				}
			}
		}

		//  Remove the trailing comma from the list
		if (!result.equals("")) {
			result = result.substring(0, result.length() -1) ;
		}

		return(result);
	}



	/***************************************************************************************************
	*  NAME:  generateInsertValues
	*
	*  PURPOSE:  Once the DataMapper is validated and we have CSV Column Indexes mapped to DB2 column
	*  names then this is enough information to generate a dynamic Insert statement of the following form
	*
	*  INSERT INTO TARGET.TABLE (MACHINE, SERIAL, BILL_DATE, COST) VALUES ('ASDF', 'R3R5', DATE('2006-01-30'), 10.99)
	*
	*  INPUTS:  1.  Private DataMapper columns Array
	*
	*  OUTPUTS:  Returns a dynamically generated Insert statement.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public String generateInsertValues() {
		String result, db2Columns, sqlStatement;
		colDefinition col;

		//  Initialize some values
		col = null;
		db2Columns = "";
		result = "";


		//  Generate the first part the Insert statement which is the DB2 target table.
		sqlStatement = "INSERT INTO " + this.targetTable + " (";

		//  Generate the second part the Insert statement which is the DB2 column names list)
		//  Loop through all the the columns in the DataMapper columns Array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);

			//  If the column has a DB2 name associated with it and there was some data found
			//  in this row of the CSV file, then it will be part of the insert statement.
			if (!col.db2ColName.equals("")){
				if (!col.data.equals("")){
					//  Pick up db2ColumnName
					db2Columns = db2Columns + col.db2ColName + ",";
				}
			}
		}

		//  If there was some data to insert
		if (!db2Columns.equals("")) {
			//  Remove trailing comma from db2Columns list generated above
			db2Columns = db2Columns.substring(0, db2Columns.length() -1) ;
			sqlStatement = sqlStatement + db2Columns + ") VALUES (";

			//  Loop through all the the columns in the DataMapper columns Array
			for (int i = 0; i < columns.size(); i++) {
				//  Get current column
				col = (colDefinition)columns.get(i);
				//  If the column has a DB2 name associated with it and there was some data found
				//  in this row of the CSV file, then it will be part of the insert statement.
				if (!col.db2ColName.equals("")){
					if (!col.data.equals("")){
						//  Pick up the sqlData for use in VALUES clause
						sqlStatement = sqlStatement + col.sqlData + ",";
					}
				}
			}
			//  Remove trailing comma from list generated above
			sqlStatement = sqlStatement.substring(0, sqlStatement.length() -1) ;
			sqlStatement = sqlStatement + ")";

			//  We found something to insert so return the SQL Insert Statement
			result = sqlStatement;
		}


		return(result);
	}

	/***************************************************************************************************
	*  NAME:  generateInsertValues
	*
	*  PURPOSE:  Once the DataMapper is validated and we have CSV Column Indexes mapped to DB2 column
	*  names then this is enough information to generate a dynamic Insert statement of the following form
	*            This method was customized for LSR only which will handle generated records and reset 
	*            TRANSACTION_CNT to 1 for each row
	*
	*  INSERT INTO TARGET.TABLE (MACHINE, SERIAL, BILL_DATE, COST) VALUES ('ASDF', 'R3R5', DATE('2006-01-30'), 10.99)
	*
	*  INPUTS:  
	*           1.  generatedFlag which indicate this is a generated reocords
	*               0 - the initail record
	*               1 - the rest fo records, set GENERATED_CODE
	*           2.  HashMap wwcdeKeyMap
	*           3.  HashMap generatedKeyMap
	*
	*  OUTPUTS:  Returns a dynamically generated Insert statement.
	*
	*  CREATED BY:  Hang Shi  March, 2009 - IBM  for C3771
	*  
	*   ---------------------------------------------------------------------------------------
	*  Modification History
	*  ---------------------------------------------------------------------------------------
	*
	*  Jan 2008 - C3220 Added the overloaded method:public String generateInsertValues(Object [][] colNameValuePairs)
	*  March 2009 - C3771 HS Added the overloaded method generateInsertValues
	*  2009-10-09 Mark Ma - C3992 add public method setDataByDB2Name
	*  2009-12-17 MM - C4046 using SDW_COMP_ID column instead of WW_CDE
	*
	***************************************************************************************************/
	public String generateInsertValues(int generatedFlag, HashMap wwcdeKeyMap, HashMap generatedKeyMap) {
		String result, db2Columns, sqlStatement, generatedCode, genKey;
		colDefinition col;

		//  Initialize some values
		col = null;
		db2Columns = "";
		result = "";
		generatedCode = "";
		genKey = "";


		//  Generate the first part the Insert statement which is the DB2 target table.
		sqlStatement = "INSERT INTO " + this.targetTable + " (";

		//  Generate the second part the Insert statement which is the DB2 column names list)
		//  Loop through all the the columns in the DataMapper columns Array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);

			//  If the column has a DB2 name associated with it and there was some data found
			//  in this row of the CSV file, then it will be part of the insert statement.
			if (!col.db2ColName.equals("")){
				if (!col.data.equals("")){
					//  Pick up db2ColumnName
					db2Columns = db2Columns + col.db2ColName + ",";
				}
			}
		}

		//  If there was some data to insert
		if (!db2Columns.equals("")) {
			//  Remove trailing comma from db2Columns list generated above
			db2Columns = db2Columns.substring(0, db2Columns.length() -1) ;
			if ( generatedFlag == 1){
				  // Generated records, set GENERATED_CODE TO y	
				sqlStatement = sqlStatement + db2Columns + ",GENERATED_CODE" + ") VALUES (";
			}else{
				sqlStatement = sqlStatement + db2Columns + ") VALUES (";
			}
		

			//  Loop through all the the columns in the DataMapper columns Array
			for (int i = 0; i < columns.size(); i++) {
				//  Get current column
				col = (colDefinition)columns.get(i);
				//  If the column has a DB2 name associated with it and there was some data found
				//  in this row of the CSV file, then it will be part of the insert statement.
				if (!col.db2ColName.equals("")){
					if (!col.data.equals("")){
						//  Pick up the sqlData for use in VALUES clause
						if (col.db2ColName.equalsIgnoreCase("TRANSACTION_CNT")){
							// Set transaction_CNT to 1 for each row
							sqlStatement = sqlStatement + 1 + ",";
						}else{
							sqlStatement = sqlStatement + col.sqlData + ",";
						}
						
						if (col.db2ColName.equalsIgnoreCase("SDW_COMP_ID") && (generatedFlag == 1)){
							// Search GENERATED_CODE value from two maps							
							genKey = (String) wwcdeKeyMap.get(col.data);							
						    generatedCode = (String) generatedKeyMap.get(genKey);
						    
						}
					}
				}
			}
			//  Remove trailing comma from list generated above
			sqlStatement = sqlStatement.substring(0, sqlStatement.length() -1) ;
			if ( generatedFlag == 1){
				  // Generated records, set GENERATED_CODE
				sqlStatement = sqlStatement + ",'" + generatedCode + "')";
			}else{
				sqlStatement = sqlStatement + ")";
			}			

			//  We found something to insert so return the SQL Insert Statement
			result = sqlStatement;
		}


		return(result);
	}
	

	/***************************************************************************************************
	*  NAME:  generateInsertValues
	*
	*  PURPOSE:  Once the DataMapper is validated and we have CSV Column Indexes mapped to DB2 column
	*  names then this is enough information to generate a dynamic Insert statement of the following form
	*
	*  INSERT INTO TARGET.TABLE (MACHINE, SERIAL, BILL_DATE, COST) VALUES ('ASDF', 'R3R5', DATE('2006-01-30'), 10.99)
	*
	*  INPUTS:  1.  Private DataMapper columns Array
	*           2.  This is two dimentional Object array that contains db2 column and value pairs to be inserted
	*               Sample to create the object array with an integer as a value.
	*				Object[] [] options = new Object[1][2];
	*				options [0][0] = "APP_REJ_BY";
	*				options [0][1] = new Integer(3);
	*
	*  OUTPUTS:  Returns a dynamically generated Insert statement.
	*
	*  CREATED BY:  Victor Monaco  January, 2008 - IBM
	*
	***************************************************************************************************/
	public String generateInsertValues(Object [][] colNameValuePairs) {
		String result, db2Columns, sqlStatement;
		colDefinition col;

		//  Initialize some values
		col = null;
		db2Columns = "";
		result = "";


		//  Generate the first part the Insert statement which is the DB2 target table.
		sqlStatement = "INSERT INTO " + this.targetTable + " (";

		//  Generate the second part the Insert statement which is the DB2 column names list)
		//  Loop through all the the columns in the DataMapper columns Array
		for (int i = 0; i < columns.size(); i++) {
			//  Get the current column
			col = (colDefinition)columns.get(i);

			//  If the column has a DB2 name associated with it and there was some data found
			//  in this row of the CSV file, then it will be part of the insert statement.
			if (!col.db2ColName.equals("")){
				if (!col.data.equals("")){
					//  Pick up db2ColumnName
					db2Columns = db2Columns + col.db2ColName + ",";
				}
			}
		}


		//  Pick up any new db2 columns and values brought in by the object array.  Pick up the DB2
		//  column name first.
		for (int i = 0; i < colNameValuePairs.length; i++) {

			String db2colname = colNameValuePairs[i][0].toString();
			Object value = colNameValuePairs[i][1];

			if (db2colname.length() > 0 & value != null) {
				db2Columns = db2Columns + db2colname + ",";
			}
		}


		//  If there was some data to insert
		if (!db2Columns.equals("")) {
			//  Remove trailing comma from db2Columns list generated above
			db2Columns = db2Columns.substring(0, db2Columns.length() -1) ;
			sqlStatement = sqlStatement + db2Columns + ") VALUES (";

			//  Loop through all the the columns in the DataMapper columns Array
			for (int i = 0; i < columns.size(); i++) {
				//  Get current column
				col = (colDefinition)columns.get(i);
				//  If the column has a DB2 name associated with it and there was some data found
				//  in this row of the CSV file, then it will be part of the insert statement.
				if (!col.db2ColName.equals("")){
					if (!col.data.equals("")){
						//  Pick up the sqlData for use in VALUES clause
						sqlStatement = sqlStatement + col.sqlData + ",";
					}
				}
			}

			//  Loop through all the the column values in the colNameValuePairs Array
			for (int i = 0; i < colNameValuePairs.length; i++) {

				String db2colname = colNameValuePairs[i][0].toString();
				Object value = colNameValuePairs[i][1];

				if (db2colname.length() > 0 & value != null) {

					//  If it's a number
					if (value.getClass().getSuperclass() == java.lang.Number.class) {
						sqlStatement = sqlStatement + value.toString() + ",";

					//  If it's a date
					} else if (value.getClass() == java.sql.Date.class) {
						// setup the date formatter
						SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
						//  format the date
						String strDate = formatter.format(value).toString();

						sqlStatement = sqlStatement + "Date('" + strDate  + "')" + ",";
					//  If it's a time
					} else if (value.getClass() == java.sql.Time.class) {
						// setup the date formatter
						SimpleDateFormat formatter = new SimpleDateFormat("hh.mm.ss a");
						//  format the date
						String strDate = formatter.format(value).toString();

						sqlStatement = sqlStatement + "Time('" + strDate  + "')" + ",";
					//  If it's a timestamp
					} else if (value.getClass() == java.sql.Timestamp.class) {
						try{
							Timestamp ts = Timestamp.valueOf(value.toString());		
							String strTimestamp  = ts.toString();
							sqlStatement = sqlStatement + "Timestamp('" + strTimestamp  + "')" + ",";
						} catch (Exception ex) {
							//  Invalid time format pattern					
							result = "Application Error:  Column " + col.CSVIndex + " XML timestamp format '" + col.type.getTimestampFormat() + "' is invalid.\r\n" + ex.getMessage();									
						}
						
					//  else assume string
					} else {
						sqlStatement = sqlStatement + "'" + value.toString() + "'" + ",";
					}
				}

			}

			//  Remove trailing comma from list generated above
			sqlStatement = sqlStatement.substring(0, sqlStatement.length() -1) ;
			sqlStatement = sqlStatement + ")";

			//  We found something to insert so return the SQL Insert Statement
			result = sqlStatement;
		}


		return(result);
	}


	/***************************************************************************************************
	*  NAME:  getCSVFileNameVerificationString
	*
	*  PURPOSE:  This is a simple public function to return the CSV File Name Verification String to the
	*  calling program.  This optional variable was set via the XML mapper config file and can be used
	*  to associate a particular CSV file name with a particular transaction.  ie.  The current mapper
	*  will only load CSV files with that contain the name 'approve'
	*
	*  INPUTS:  N/A
	*
	*  OUTPUTS:  The optional CSV file Name Verification String that may have been set through the
	*  XML mapper config file.
	*
	*  CREATED BY:  Victor Monaco  January, 2006 - Millennium IT
	*
	***************************************************************************************************/
	public String getCSVFileNameVerificationString() {
		String result;

		result = this.csvFileNameVerificationString;

		return(result);
	}


}

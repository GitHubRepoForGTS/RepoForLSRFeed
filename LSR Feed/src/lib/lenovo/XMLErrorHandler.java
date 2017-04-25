package lib.lenovo;

import org.xml.sax.*;

/***************************************************************************************************
*  CLASS:  XMLErrorHandler
*
*  PURPOSE:  This Class implements the xml SAX Error Handler and is used in conjunction with the
*  the Java xml.sax parser.  This class simply catches any XML parsing errors and allows the calling
*  program to retrieve the first error message.  Additionally, a count of the number of errors and
*  warnings can be retrieved through the public methods getErrorCount() and getWarningCount().
*
*  USAGE:
*
*  To create a new XMLErrorHandler use:
*		XMLErrorHandler errorHandler = new XMLErrorHandler();
*
*  To link implement this error handler use:
*			DocumentBuilder builder = factory.newDocumentBuilder();
*			builder.setErrorHandler(errorHandler);
*
*  CREATED BY:  Ka-Lai Wong January, 2006 - IBM
*
*  ---------------------------------------------------------------------------------------
*  Modification History
*  ---------------------------------------------------------------------------------------
*  January 2006 - First Release 1.0
*
***************************************************************************************************/
public class XMLErrorHandler implements org.xml.sax.ErrorHandler {
	private SAXException parseException;
	private int errorCount;
	private int warningCount;

	//  Error handler constructor
	public XMLErrorHandler() {
		parseException = null;
		errorCount = 0;
		warningCount = 0;
	}

	//  If a SAXParseException error occurred then this would have been called
	public void error(SAXParseException ex) {
		errorCount++;
		if (parseException == null) {
			parseException = ex;
		}
	}

	//  If a SAXParseException warning occurred then this would have been called
	public void warning(SAXParseException ex) {
		warningCount++;
	}

	//  If a SAXParseException fatal Error occurred then this would have been called
	public void fatalError(SAXParseException ex) {
		errorCount++;
		if (parseException == null) {
			parseException = ex;
		}
	}

	//  This can be used to retrieve the first error that occurred.
	public SAXException getFirstException() {
		return parseException;
	}

	//  Return a count of the number of errors that occurred
	public int getErrorCount() {
		return (this.errorCount);
	}

	//  Return a count of the number of warnings that occurred
	public int getWarningCount() {
		return (this.warningCount);
	}
}



package lib.lenovo;
/***************************************************************************************************
*  CLASS:  XMLErrorHandler
*
*  PURPOSE:  This object is used to hold the details of each sub report
*
*  USAGE:
*
*  To create a new SubReport use:
*		SubReport sub = new SubReport (String in_sql,String in_header, String in_totals)
*
* where in_sql is the sql statement to create the report
* where in_header is represent the result set header printing option.  (None or Db2Header for column names)
* where in_totals is a flag option for the report to display totals at the bottom the report.
*
*  CREATED BY:  Victor Monaco June, 2006
*
*  ---------------------------------------------------------------------------------------
*  Modification History
*  ---------------------------------------------------------------------------------------
*  June 2006 - First Release 1.0
*
***************************************************************************************************/
public class SubReport {
	private String sql;
	private String header;
	private String totals;

	// Constructor for the sub report
	public SubReport (String in_sql,String in_header, String in_totals) {
		this.sql = in_sql;
		this.header = in_header;
		this.totals = in_totals;
	}

	//  Return the sql
	public String getSQL () {
		return (this.sql);
	}

	//  Return the header
	public String getHeader () {
		return (this.header);
	}

	//  Return the totals flag
	public String getTotalsFlag () {
		return (this.totals);
	}
}
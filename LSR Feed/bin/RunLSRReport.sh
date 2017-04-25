#!/bin/ksh 
#
#
# PURPOSE: Run LSRReport java application
#
# DATE:    08/08/2007
#
# USAGE: RunLSRReport.sh <userid> 
#
# PARAMETERS:
#			<userid>: 
#           
#
# PROGRAMMER: Mark Ma
###########################################################################
# Modification History:
# 08/08/2007 | MM | v1.0.0 create
# 02/01/2008 | VM | v1.2.0 added and removed new options
# 02/26/2008 | VM | v1.2.0 Removed old invoice.xml as an option
# 06/02/2008 | VM | v1.2.0 Added Accrual Report
# 07/16/2008 | VM | v1.2.0 Added Credit Report
# 07/17/2008 | VM | v1.2.0 Added Validation Report
# 09/18/2008 | VM | v1.2.0 Added Descriptive Summary Report and Descriptive Credit Report
# 05/29/2009 | VM | v1.3.0 Remove Lita Marked report and created new submit_lita report
# 08/17/2009 | VM | v1.3.1 Add ability to create multiple invoices based on old and new billing rates
# 03/23/2010 | MM | v1.3.2 Add a new parameter <dbname> so it will be able to connect to different database.
# 07/19/2010 | VM | v1.3.3 Removed multiple invoice split by old and new billing rates.
# 11/16/2010 | VM | v1.3.4 Removed the GST related reports

DBNAME=""
UID=""
UPWD=""

EXTRACT_PATH=$HOME/lsrfeed/file_io/lsr_extract/

# give help if needed
if [ $# -eq 3 ] 
then
	DBNAME=$1
    UID=$2
    UPWD=$3
else
    if [ $# -eq 0 ]
    then
        grep DATABASE_NAME "$HOME/lsrfeed/LSR.properties" | tr -d '\r' | read junk junk DBNAME
        UID=$USER
        UPWD=$LSRPWD
    else
		echo "**********************************************************************" 
		echo "RunLSRReport.sh requires 0 or 3 parameters"
		echo "Usage: RunLSRReport.sh [<dbname> <userid> <password>] "
		echo "dbname userid and password parameter are optional. default dbname will be lsr" 
		echo "**********************************************************************" 
		exit 1
    fi
fi

#  Change Directory 
cd $HOME/lsrfeed/
	
while :
do
 clear
 echo "                 LSR Report v1.3.3                "
 echo "  *********************************************"
 echo "    1. Exit"
 echo "    2. Submit to Lita"
 echo "    3. Summary Report And Validation Report"
 echo "    4. Accrual Report"
 echo "    5. Credit Report"
  echo "  *********************************************"
 echo -n "Please enter option [1 - 5]"
 read opt
 case $opt in
 
  1) echo "Bye $USER";
      exit 1;;
  2) echo "************ Submit to Lita *************";
	 date 
	 echo "RunLSRReport.sh started..." 
	 #  Run the Java Program
	 java LSRExtract.class $DBNAME $UID $UPWD "submit_lita.xml"
	 echo "RunLSRReport.sh ended." ;;	
  3) echo "************ Summary Report *************";
	 date 
	 echo "RunLSRReport.sh started..." 
	 #  Run the Java Program

	 echo "Generating Regular Billing set."
	 echo "Running Validation Report"
	 java LSRExtract.class $DBNAME $UID $UPWD "validation_report.xml"
	 echo "Running Summary Report"
	 java LSRExtract.class $DBNAME $UID $UPWD "summary_report.xml"
	 echo "Running Descriptive Summary Report"
	 java LSRExtract.class $DBNAME $UID $UPWD "invoice_text.xml"
	 echo "RunLSRReport.sh ended." ;; 
  4) echo "************ Accrual Report *************";
	 date 
	 echo "RunLSRReport.sh started..." 
	 #  Run the Java Program
	 java LSRExtract.class $DBNAME $UID $UPWD "accrual.xml"
	 echo "RunLSRReport.sh ended." ;; 
  5) echo "************ Credit Report *************";
	 date 
	 echo "RunLSRReport.sh started..." 
	 #  Run the Java Program
	 echo "Running Credit Report"
	 java LSRExtract.class $DBNAME $UID $UPWD "credit_report.xml"
	 echo "Running Descriptive Credit Report"
	 java LSRExtract.class $DBNAME $UID $UPWD "credit_invoice_text.xml"
	 echo "RunLSRReport.sh ended." ;; 
 
  *) echo "$opt is an invaild option. Please select option between 1-6 only";
     echo "Press [enter] key to continue. . .";
     read enterKey;;
esac
done


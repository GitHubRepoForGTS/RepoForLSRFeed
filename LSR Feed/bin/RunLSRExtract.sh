#!/bin/ksh 
#
#
# PURPOSE: Run LSRReport java application
#
# DATE:    2010-03-19
# USAGE: RunLSRExtract.sh <dbname> <userid> <password> <xmlfile>
#
# PARAMETERS:
#			<dbname> 
#			<userid> 
#			<password>  
#			<xmlfile>
#
# PROGRAMMER: Mark Ma
###########################################################################
# Modification History:
# 2009-03-19 | MM | v1.0.0 create

Version="1.0.0"


DBNAME=""
UID=""
UPWD=""
XMLFILE=""
EXPECTED_DATE=""
ERRORFLAG=0

LOAD_DATE_FILE=${HOME}/lsrfeed/file_io/lsr_extract/loadDate.txt

EXTRACT_PATH=$HOME/lsrfeed/file_io/lsr_extract/

MAINPATH=$HOME/lsrfeed/
ATTACH_FILES=""

EMAILSUBJECT="LSR Extract Results - "$now
ERRORBODYFILE="${MAINPATH}file_io/cron_log/extractError-`date +%Y%m%d`.txt"
ERRORSUBJECT="LSR Extract Error-`date`"
SENDFROM="LSR-"$USER
EXTRACTTEMP="${MAINPATH}file_io/cron_log/extractTmp-`date +%Y%m%d`.txt"
EXTRACTLOG="${MAINPATH}file_io/cron_log/extract-`date +%Y%m%d`.log"
RCSNEWTRANS="${MAINPATH}file_io/lsr_feed/rcs/new_transaction/"
BFNEWTRANS="${MAINPATH}file_io/lsr_feed/break_fix/new_transaction/"

#generate email body file (empty)

echo "\r\n\r\n" > emailbody.txt

#clear temp files

echo "" > $ERRORBODYFILE
echo "" > $EXTRACTTEMP

finish(){
	cat $EXTRACTTEMP >> $ERRORBODYFILE
	cat $EXTRACTTEMP >> $EXTRACTLOG
	if [ $ERRORFLAG -eq 1 ]
	then
		if [ -n $emailList ]
		then			
			vmail.sh -t "$emailList" -f "$SENDFROM" -s "$ERRORSUBJECT" -b "$ERRORBODYFILE" -a "$ERRORBODYFILE"
		else
			cat $ERRORBODYFILE
		fi
	fi
	
	if [ -e $EXTRACTTEMP ]
	then
		rm $EXTRACTTEMP
	fi
	exit 1
}

echo "LSR Extract Program Version:$Version Started at `date`\r\n" >$EXTRACTLOG

# give help if needed
if [ $# -eq 4 ] 
then
    DBNAME=$1
    UID=$2
    UPWD=$3
    XMLFILE=$4
else
   	ERRORFLAG=1
   	echo "**********************************************************************" >> $ERRORBODYFILE
	echo "RunLSRExtract.sh requires 4 parameters" >> $ERRORBODYFILE
   	echo "Usage: RunLSRReport.sh [<dbname> <userid> <password> <xmlfile>] " >> $ERRORBODYFILE
   	echo "**********************************************************************" >> $ERRORBODYFILE
   	finish
fi


# getEmailList
emailList=""	
grep AIX_EMAIL_REPORT "$MAINPATH/LSR.properties" | tr -d '\r' | while read junk junk email
do 
	if [ ! -n "$emailList"  ]
		then
		emailList=$email
		else	
		emailList=$emailList,$email	
	fi
done 

if [ -z "$emailList" ]
then 
	ERRORFLAG=1
	echo "Can not find AIX_EMAIL_REPORT value in LSR.properties file. email address list is empty." >> $ERRORBODYFILE
	cat $ERRORBODYFILE 
	finish()
fi

# get LSR database name from property file

grep DATABASE_NAME "$HOME/lsrfeed/LSR.properties" | tr -d '\r' | read junk junk LSRDBNAME

if [ -z "$LSRDBNAME" ]
then 
	ERRORFLAG=1
	echo "Can not find DATABASE_NAME value in LSR.properties file. " >> $ERRORBODYFILE
	finish
fi

#  Change Directory 
cd ${MAINPATH}
# remove temp file
if [ -e $LOAD_DATE_FILE ]
then
	rm $LOAD_DATE_FILE
fi
# get expected load data date
db2 connect to $LSRDBNAME user $LSRUSERID using $LSRPWD >> $EXTRACTTEMP
db2 "SELECT PERIOD_END_DTE + 2 DAYS FROM LENOVO.PERIOD_DATES WHERE YEAR = YEAR(CURRENT DATE)  AND MONTH = MONTH(CURRENT DATE)" >> $EXTRACTTEMP
db2 terminate >>$EXTRACTTEMP
cat $EXTRACTTEMP | grep "/20"  > $LOAD_DATE_FILE
if [ -s $LOAD_DATE_FILE ]
then
	read EXPECTED_DATE < $LOAD_DATE_FILE
	echo Expected Date:$EXPECTED_DATE >>$EXTRACTLOG
	CURRENT_DATE=`date +%m/%d/%Y`
	echo Current Date:$CURRENT_DATE >>$EXTRACTLOG
else
	ERRORFLAG=1
	echo "Can not find expected date\r\n" >> $ERRORBODYFILE
	cat $EXTRACTTEMP >> $ERRORBODYFILE
	finish
fi 

if [ ${EXPECTED_DATE} = ${CURRENT_DATE} ]
then
	echo Run LSRExtract to retrieve data and load it to LSR database >>$EXTRACTLOG
	java -cp lib/db2/db2jcc_license_cisuz.jar:lib/db2/db2jcc_license_cu.jar:lib/db2/db2jcc.jar: LSRExtract.class $DBNAME $UID $UPWD $XMLFILE >>$EXTRACTTEMP
	if [ $? -eq 0 ]
	then		
		Report1="${EXTRACT_PATH}icpm_load_rcs_`date +%Y%m%d`.csv"	
		if [ -e $Report1 ]
		then	
			echo "Report 1: move ${Report1} to $RCSNEWTRANS" >>$EXTRACTLOG
			mv ${Report1} $RCSNEWTRANS
		else
			echo "Report $Report1 does not exist." >>$EXTRACTLOG
		fi
		Report2="${EXTRACT_PATH}icpm_load_bf_`date +%Y%m%d`.csv"
		if [ -e ${Report2} ]
		then			
			echo "Report 2: move ${Report2} to $BFNEWTRANS" >>$EXTRACTLOG
			mv ${Report2} $BFNEWTRANS
		else
			echo "Report $Report2 does not exist." >>$EXTRACTLOG
		fi

		Report3="${EXTRACT_PATH}CA_oow_discount_`date +%Y%m%d`.csv"
		echo Report3:${Report3} >>$EXTRACTLOG
		if [ -e ${Report3} ]
		then
			echo "Send email with ${Report3}." >>$EXTRACTLOG
			vmail.sh -t "$emailList" -f "$SENDFROM" -s "Canadian Out of Waranty Call Discount" -b "emailbody.txt"  -a "${Report3}"
			if [ $? -eq 0 ]
			then
				rm ${Report3}
			fi
		else
			echo "Report $Report1 does not exist." >>$EXTRACTLOG
		fi

		Report4="${EXTRACT_PATH}icpm_data_anomalies_`date +%Y%m%d`.csv"
		echo Report4:${Report4} >>$EXTRACTLOG
		if [ -e ${Report4} ]
		then		
			echo "Report 4: send email with ${Report4}." >>$EXTRACTLOG
			vmail.sh -t "$emailList" -f "$SENDFROM" -s "ICPM Unbillable Report" -b "emailbody.txt" -a "${Report4}"
			if [ $? -eq 0 ]
			then
				rm ${Report4}
			fi
		else
			echo "Report $Report4 does not exist." >>$EXTRACTLOG
		fi
	else
		ERRORFLAG=1		
		finish
	fi
	cat $EXTRACTTEMP >>$EXTRACTLOG
		
else
	echo "Today: $CURRENT_DATE is not the date: ${EXPECTED_DATE} to retrieve data and load it to LSR database" >>$EXTRACTLOG
fi
if [ -e $EXTRACTTEMP ]
then
	rm $EXTRACTTEMP
fi



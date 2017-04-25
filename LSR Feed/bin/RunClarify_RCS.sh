#!/bin/ksh 
#
#
# PURPOSE: Run LSRReport java application
#
# DATE:    2013-05-13
# USAGE: RunCostPull.sh <auto>
#
# PARAMETERS: this script can have 0 or 1 parameter, if there is <auto> provided then pull data on specific date, 
# 				if no parameter provided then pull data immediately.
#			
#             <auto>: 1
#
# PROGRAMMER: Mark Ma
###########################################################################
# Modification History:
# 2013-05-13 | SP | v1.0.0 create
#
###########################################################################
Version="1.0.0"


DBNAME="TORDSNQ"
UID="$CDNIWUSERID" 
UPWD="$CDNIWPWD"
CM_XMLFILE="cost_recovery_cm.xml"
CM_FILENAME="cost_recovery_cm"
HS_XMLFILE="cost_recovery_hs.xml"
HS_FILENAME="cost_recovery_hs"
LSRDBNAME="$LSRUSERID"
LSRUID="$LSRUSERID"
LSRUPWD="$LSRPWD"
EXPECTED_DATE=""
ERRORFLAG=0
EXTRACT_PATH=$HOME/lsrfeed/file_io/lsr_extract/
MAINPATH=$HOME/lsrfeed/
ATTACH_FILES=""
ERRORSUBJECT="LSR Cost Pull Error-`date`"
SENDFROM="LSR-"$USER
EXTRACTTEMP="${MAINPATH}file_io/cron_log/CostPullTmp-`date +%Y%m%d`.txt"
EXTRACTLOG="${MAINPATH}file_io/cron_log/CostPull-`date +%Y%m%d`.log"
CM_NEWTRANS="${MAINPATH}file_io/lsr_feed/cost_recovery_cm/new_transaction/"
HS_NEWTRANS="${MAINPATH}file_io/lsr_feed/cost_recovery_hs/new_transaction/"
EMAILBODY="${MAINPATH}file_io/cron_log/emailbody.txt"
LOAD_DATE_FILE=${HOME}/lsrfeed/file_io/lsr_extract/loadDate.txt
LINES=0
HISTORY_RUN=0

#*************************************
#  FINISH SUBROUTINE
#*************************************
finish(){
	if [ $ERRORFLAG -eq 1 ]
	then
		if [ -n $emailList ]
		then			
			vmail.sh -t "$emailList" -f "$SENDFROM" -s "$ERRORSUBJECT" -b "$EXTRACTLOG" -a "$EXTRACTLOG" >> $EXTRACTLOG
		fi
	fi
	
	if [ -e $EXTRACTTEMP ]
	then
		rm $EXTRACTTEMP
	fi
	if [ -e $EMAILBODY ]
	then
		rm $EMAILBODY
	fi
	
	if [ -e $LOAD_DATE_FILE ]
	then
		rm $LOAD_DATE_FILE
	fi
	exit
}


#*************************************
# PULLDATA SUBROUTINE
#*************************************
# 3 parameters: xml_file_name, extract_file_name,new_tran_path
# i.e: PullData $CM_XMLFILE "cost_recovery_cm" $CM_NEWTRANS
PullData(){
	echo "Run LSRExtract to retrieve data and load it to LSR database" >>$EXTRACTLOG
	java -cp .:lib/db2/db2jcc_license_cisuz.jar:lib/db2/db2jcc_license_cu.jar:lib/db2/db2jcc.jar:lib/jexcelapi_2_6_9_1.4/jexcelapi/jxl.jar LSRExtract.class $DBNAME $UID $UPWD $1 >> $EXTRACTLOG
	if [ $? -eq 0 ]
	then		
		Report1="${EXTRACT_PATH}$2_`date +%Y%m%d`.csv"	
		if [ -e $Report1 ]
		then	
			#  This section of code is for processing the current month only.
			#  Check if there is at least one row of data to load for current month.  If there is no new data then do not
			#  delete the current result set.  If we have new data then delete the current set of data.
			if [ $2 = "cost_recovery_cm" ]
			then
				LINES=`cat $Report1 | sed '/^\s*$/d' | wc -l`
				if [ $LINES -ge 2 ]
				then
					echo "Delete all records in COST.COST_RECOVERY_CM table" >> $EXTRACTLOG 
					db2 connect to $LSRDBNAME user $LSRUID using $LSRUPWD >> $EXTRACTLOG
					db2 "delete from COST.COST_RECOVERY_CM" >>$EXTRACTLOG
					db2 terminate >>$EXTRACTLOG
				fi

			fi

			echo "Report 1: move ${Report1} to $3" >>$EXTRACTLOG
			mv ${Report1} $3
		else
			ERRORFLAG=1
			echo "Report $Report1 does not exist. Nothing to do." >>$EXTRACTLOG
			finish
		fi



		#  Run the Java Program to load the data file
		echo  "Run LSRFeed to load the generated files to db2 " >>$EXTRACTLOG
         	java  LSRFeed.class $LSRUSERID $LSRPWD   >>$EXTRACTLOG 2>&1
		if [ $? -eq 0 ]
		then
			echo "LSRFeed Java program success." >>$EXTRACTLOG
		else
			ERRORFLAG=1	
			echo "Error: LSRFeed Java program failed." >>$EXTRACTLOG
			finish
		fi

		
		#  This section of code is for processing the current month only.
		#  Examine the results of the feed report.  If there are no errors then move it	to the backup directory so no 
		#  successful processsing email will be sent.   If there are errors then send the report as usual.  The
		#  LSRFeed shell program will take care of sending and storing the feed report if necessary.
		if [ $2 = "cost_recovery_cm" ]
		then
			#  On the day we run the history report I want to see all feed reports so ignore this code
			if [ $HISTORY_RUN -eq 0 ]
			then
				FEEDREPORT="${MAINPATH}file_io/lsr_feed/$2/feed_rpt_$2.txt"
				if [ -e ${FEEDREPORT} ]
				then
					#  Check for errors in the file and move to backup directory if there are none otherwise leave it.
					#  Get the feed report line only
					FEED_RPT=`sed -n '/Successfully/p' ${FEEDREPORT} | head -1`

					#Cut the line so that we get the error count only.  First cut to pipe character then cut to semicolon then remove blanks
					Err=`echo "$FEED_RPT" | cut -d'|' -f2 | cut -d':' -f2 | sed 's/^ *//;s/ *$//'`

					#Cut the line so that we get the success count only.  First cut to pipe character then cut to semicolon then remove blanks
					Success=`echo "$FEED_RPT" | cut -d'|' -f1 | cut -d':' -f2 | sed 's/^ *//;s/ *$//'`

					# Check to see if there were no errors
					if [ $Err == '0' ]
					then
						# There were no errors so next check to see if any rows were successful.  If there were no errors and
						# some rows were successfully processed then all is ok.  Do not send successful run email, just store in backup directoy.
						if [ $Success != '0' ]
						then
							echo "Current Month Data Load Successful.  Feed report moved to backup." >>$EXTRACTLOG
							mv ${FEEDREPORT} "${MAINPATH}file_io/lsr_feed/$2/backup/feed_rpt_$2_`date +%Y%m%d-%H%M%S`.txt" >>$EXTRACTLOG
						fi
					fi
				fi
			fi
		fi
	else
		ERRORFLAG=1	
		echo "Error: LSRExtract Java program failed." >>$EXTRACTLOG
		finish
	fi
}


#*************************************
#     MAIN PROGRAM STARTS HERE
#*************************************
echo "LSR Cost Recovery Pull Script Version:$Version Started at `date`\r\n" >$EXTRACTLOG


#*************************************
#  getEmailList
#*************************************
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
	echo "Can not find AIX_EMAIL_REPORT value in LSR.properties file. email address list is empty." >> $EXTRACTLOG
	exit 1
fi


#*************************************
#  get LSR database name from property file
#*************************************
grep DATABASE_NAME "$HOME/lsrfeed/LSR.properties" | tr -d '\r' | read junk junk LSRDBNAME

if [ -z "$LSRDBNAME" ]
then 
	ERRORFLAG=1
	echo "Error: Can not find DATABASE_NAME value in LSR.properties file. " >> $EXTRACTLOG
	finish
fi


#*************************************
#  generate email body file (empty)
#*************************************
echo "\r\n\r\n" > $EMAILBODY

cd ${MAINPATH}

#*************************************
#  Pull data for the history month
#*************************************
echo "get expected load data date" >> $EXTRACTLOG
db2 connect to CDNIW user $UID using $UPWD
db2 -tf ${MAINPATH}file_io/lsr_feed/cost_recovery_hs/monthend.ddl | grep 20 >$EXTRACTTEMP
EXPECTED_DATE="`cat $EXTRACTTEMP`"
echo "expected date: ${EXPECTED_DATE}" >>$EXTRACTLOG
db2 terminate >>$EXTRACTLOG

if [ -n "${EXPECTED_DATE}" ]
then
	CURRENT_DATE=`date +%Y-%m-%d`
	echo Current Date:$CURRENT_DATE >>$EXTRACTLOG
else
	ERRORFLAG=1
	echo "Error: Can not find expected date\r\n" >> $EXTRACTLOG		
	finish
fi 

if [ ${EXPECTED_DATE} = ${CURRENT_DATE} ]
then
	HISTORY_RUN=1
	PullData $HS_XMLFILE "cost_recovery_hs" $HS_NEWTRANS
else
	echo "Today: $CURRENT_DATE is not the date: ${EXPECTED_DATE} to retrieve data and load it to LSR database" >>$EXTRACTLOG
fi


#*************************************
#  Pull data for the current month
#*************************************
PullData $CM_XMLFILE "cost_recovery_cm" $CM_NEWTRANS


#*************************************
#  End output to log
#*************************************
echo "LSR CostPull Script Version:$Version Ended at `date`\r\n" >>$EXTRACTLOG

finish

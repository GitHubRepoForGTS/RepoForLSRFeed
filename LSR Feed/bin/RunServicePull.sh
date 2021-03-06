#!/bin/ksh 
#
#
# PURPOSE: Run LSRReport java application
#
# DATE:    2010-03-19
# USAGE: RunServicePull.sh <auto>
#
# PARAMETERS: this script can have 0 or 1 parameter, if there is <auto> provided then pull data on specific date, 
# 				if no parameter provided then pull data immediately.
#			
#             <auto>: 1
#
# PROGRAMMER: Mark Ma
###########################################################################
# Modification History:
# 2009-03-19 | MM | v1.0.0 create
# 2010-06-07 | MM | v1.0.1 C4152 Clean up temp files.
# 2010-10-20 | MM | v1.0.2 
#					1. log vmail information.
# 					2. remove empty error file.
# 2010-12-21 | SP | v1.0.3 C4266 Modify ICPM Pull shell to include more data pull feeds
# 2011-01-20 | MM | v1.0.4 C4279 "Remote Take Over" Data Pull
###########################################################################
Version="1.0.4"


DBNAME="TORDSNQ"
UID="$CDNIWUSERID" 
UPWD="$CDNIWPWD"
XMLFILE="icpm_data_pull.xml"
LSRDBNAME="$LSRUSERID"
LSRUID="$LSRUSERID"
LSRUPWD="$LSRPWD"
RTOXMLFILE="rto_data_pull.xml"

EXPECTED_DATE=""
ERRORFLAG=0

LOAD_DATE_FILE=${HOME}/lsrfeed/file_io/lsr_extract/loadDate.txt

EXTRACT_PATH=$HOME/lsrfeed/file_io/lsr_extract/

MAINPATH=$HOME/lsrfeed/
ATTACH_FILES=""

ERRORSUBJECT="LSR Service Pull Error-`date`"
SENDFROM="LSR-"$USER
EXTRACTTEMP="${MAINPATH}file_io/cron_log/ServicePullTmp-`date +%Y%m%d`.txt"
EXTRACTLOG="${MAINPATH}file_io/cron_log/ServicePull-`date +%Y%m%d`.log"
RCSNEWTRANS="${MAINPATH}file_io/lsr_feed/rcs/new_transaction/"
BFNEWTRANS="${MAINPATH}file_io/lsr_feed/break_fix/new_transaction/"
NSSNEWTRANS="${MAINPATH}file_io/lsr_feed/depot_repair/new_transaction/"
EMAILBODY="${MAINPATH}file_io/cron_log/emailbody.txt"


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

PullData(){
	echo Run LSRExtract to retrieve data and load it to LSR database >>$EXTRACTLOG
	java -cp lib/db2/db2jcc_license_cisuz.jar:lib/db2/db2jcc_license_cu.jar:lib/db2/db2jcc.jar:lib/jexcelapi_2_6_9_1.4/jexcelapi/jxl.jar LSRExtract.class $DBNAME $UID $UPWD $XMLFILE >> $EXTRACTLOG
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
			vmail.sh -t "$emailList" -f "$SENDFROM" -s "Canadian Out of Waranty Call Discount" -b "$EMAILBODY"  -a "${Report3}" >> $EXTRACTLOG
			if [ $? -eq 0 ]
			then
				rm ${Report3}
			fi
		else
			echo "Report $Report3 does not exist." >>$EXTRACTLOG
		fi

#C4266 Begin | SP | 2010/12/21

		Report4="${EXTRACT_PATH}depot_data_anomalies_`date +%Y%m%d`.csv"
#		Report4="${EXTRACT_PATH}icpm_data_anomalies_`date +%Y%m%d`.csv"
#C4266 End
		echo Report4:${Report4} >>$EXTRACTLOG
		if [ -e ${Report4} ]
		then		
			echo "Report 4: send email with ${Report4}." >>$EXTRACTLOG
#C4266 Begin | SP | 2010/12/21 			
			
			vmail.sh -t "$emailList" -f "$SENDFROM" -s "Depot Service Data Anomalies Report" -b "$EMAILBODY" -a "${Report4}" >> $EXTRACTLOG
#			vmail.sh -t "$emailList" -f "$SENDFROM" -s "ICPM Unbillable Report" -b "$EMAILBODY" -a "${Report4}" >> $EXTRACTLOG
#C4266 End
			
			if [ $? -eq 0 ]
			then
				rm ${Report4}
			fi
		else
			echo "Report $Report4 does not exist." >>$EXTRACTLOG
		fi

#C4266 Begin | SP | 2010/12/22
		Report5="${EXTRACT_PATH}nss_load_depot_`date +%Y%m%d`.csv"
		if [ -e ${Report5} ]
		then			
			echo "Report 5: move ${Report5} to $NSSNEWTRANS" >>$EXTRACTLOG
			mv ${Report5} $NSSNEWTRANS
		else
			echo "Report $Report5 does not exist." >>$EXTRACTLOG
		fi
#C4266 End
		#C4279
		echo  "Run LSRFeed to load the generated files to db2 " >>$EXTRACTLOG
		RunLSRFeed.sh  >>$EXTRACTLOG
		if [ $? -eq 0 ]
		then
			echo "LSRFeed Java program success." >>$EXTRACTLOG
		else
			ERRORFLAG=1	
			echo "Error: LSRFeed Java program failed." >>$EXTRACTLOG
			finish
		fi
	else
		ERRORFLAG=1	
		echo "Error: LSRExtract Java program failed." >>$EXTRACTLOG
		finish
	fi
	
	#C4279
	# Run rto_data_pull
	echo Run LSRExtract to retrieve rto_data_pull  >>$EXTRACTLOG
	java -cp lib/db2/db2jcc_license_cisuz.jar:lib/db2/db2jcc_license_cu.jar:lib/db2/db2jcc.jar:lib/jexcelapi_2_6_9_1.4/jexcelapi/jxl.jar LSRExtract.class $LSRDBNAME $LSRUID $LSRUPWD $RTOXMLFILE >> $EXTRACTLOG
	if [ $? -eq 0 ]
	then	
		Report6="${EXTRACT_PATH}rto_load_rcs_`date +%Y%m%d`.csv"	
		if [ -e $Report6 ]
		then	
			echo "Report 6: move ${Report6} to $RCSNEWTRANS" >>$EXTRACTLOG
			mv ${Report6} $RCSNEWTRANS
		else
			echo "Report $Report6 does not exist." >>$EXTRACTLOG
		fi
	else
		ERRORFLAG=1	
		echo "Error: LSRExtract Java program failed." >>$EXTRACTLOG
		finish
	fi
		
	
}


echo "LSR ServicePull Script Version:$Version Started at `date`\r\n" >$EXTRACTLOG

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
	echo "Can not find AIX_EMAIL_REPORT value in LSR.properties file. email address list is empty." >> $EXTRACTLOG
	exit 1
fi

# get LSR database name from property file

grep DATABASE_NAME "$HOME/lsrfeed/LSR.properties" | tr -d '\r' | read junk junk LSRDBNAME

if [ -z "$LSRDBNAME" ]
then 
	ERRORFLAG=1
	echo "Error: Can not find DATABASE_NAME value in LSR.properties file. " >> $EXTRACTLOG
	finish
fi

#generate email body file (empty)
echo "\r\n\r\n" > $EMAILBODY

#  Change Directory 
cd ${MAINPATH}

if [ $# -eq 0 ]
then
	PullData
else
	# remove temp file
	if [ -e $LOAD_DATE_FILE ]
	then
		rm $LOAD_DATE_FILE
	fi
	# get expected load data date
	echo "" > $EXTRACTTEMP
	db2 connect to $LSRDBNAME user $LSRUSERID using $LSRPWD >> $EXTRACTTEMP
	db2 "SELECT PERIOD_END_DTE + 2 DAYS FROM LENOVO.PERIOD_DATES WHERE YEAR = YEAR(CURRENT DATE)  AND MONTH = MONTH(CURRENT DATE)" >> $EXTRACTTEMP
	db2 terminate >>$EXTRACTTEMP
	cat $EXTRACTTEMP >> $EXTRACTLOG
	cat $EXTRACTTEMP | grep "/20"  > $LOAD_DATE_FILE
	if [ -s $LOAD_DATE_FILE ]
	then
		read EXPECTED_DATE < $LOAD_DATE_FILE
		echo Expected Date:$EXPECTED_DATE >>$EXTRACTLOG
		CURRENT_DATE=`date +%m/%d/%Y`
		echo Current Date:$CURRENT_DATE >>$EXTRACTLOG
	else
		ERRORFLAG=1
		echo "Error: Can not find expected date\r\n" >> $EXTRACTLOG		
		finish
	fi 

	if [ ${EXPECTED_DATE} = ${CURRENT_DATE} ]
	then
		PullData
	else
		echo "Today: $CURRENT_DATE is not the date: ${EXPECTED_DATE} to retrieve data and load it to LSR database" >>$EXTRACTLOG
	fi
fi
echo "LSR ServicePull Script Version:$Version Ended at `date`\r\n" >>$EXTRACTLOG
finish

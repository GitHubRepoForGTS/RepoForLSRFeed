#!/bin/ksh 
#
#
# PURPOSE: Run LSRFeed java application
# REQUIREMENT: 
# 	1. This script will run as cron job, every hour Mon-Sat.
#	2. Log all activities in 1 log file per day. include any output of LSRFeed java program.
#	   the log file name located in $HOME/lsrfeed/log directory, and the log file name as 
#	   lsrfeed-YYYYMMdd.log
#   3. Send notification email to ids provided in email list file, when there is any input 
#	   file is processed.the email body is the output of LSRFeed java program.
#	4. After the input files are processed, rename it as feed_rpt_<businessline>.txt.YYYYmmdd-HHMMss 
#	   and move it to backup directory under business line directory.
#
# DATE:    08/08/2007
#
# USAGE: RunLSRFeed.sh [<userid> <password>]
#
# PARAMETERS:
#			<userid>:
#           <password>: both userid and password are optional, if you don't provide
#                                   it will using default logon userid.
#
# PROGRAMMER: Mark Ma
###########################################################################
# Modification History:
# 08/08/2007 | MM | v1.0.0 create
# 08/28/2007 | MM | v1.0.1 
# 	1. Remove the email_list.txt file and put the email addresses in the file LSR.properties 
#	2. Move the cron job "log" directory into here:   /home/lsrdev/lsrfeed/file_io/cron_log 
#	3. Put the LSRxxx password in the .profile file.
#	4. Attach the report files in the email as separate attachments rather than appending the report text to the body.
#	5. When move the report files into the backup directory, Append the timestamp before the file extension. 
#	6. Put error files into an error directory instead if leaving them the data import directory. 
#	7. Add a carriage return and new line for each line of text going to the .log file generated by the shell.
# 01/20/2008 | MM | v1.0.2 C3220
#   1. Added error report by email
#   2. Changed the number of parameter, only allow 0 or 2 parameters
# 12/20/2008 | MM | v1.0.3 C3427
# 	1. Added script parsemail.sh before process data, look for data file in email
# 	2. Added code to check is there any previous instance running, if yes then exit.
# 01/29/2009 | JWF | V1.0.4 C3701
#       1. appended $USER parm to SENDFROM parm to identify the sender of Notify emails
#
# 08/06/2009 | MM| v1.0.5 C3918 add LSR Validation after LSRFeed script.
#
# 05/25/2010 | MM | v1.0.6 C4152 clean up temp files
############################################################################
version=1.0.6

#
# Format the variable $now as "Friday 18 December 1998 16:22"
#
xday=`date +%a`
xdayno=`date +%d`
xmonth=`date +%b`
xyear=`date +%Y`
xtime=`date +%H:%M:%S`
now=$xday", "$xdayno" "$xmonth" "$xyear" "$xtime

Name="`basename $0` - ${version}" 
MAINPATH=$HOME/lsrfeed/
BUSINESS_PATH=${MAINPATH}file_io/lsr_feed
CURRENTDIR=
REPORT_NAME=
BACKUP_DIR=
BACKUP_FILE_NAME=
LOGFILE="${MAINPATH}file_io/cron_log/lsrfeed-`date +%Y%m%d`.log"
TEMP_FILE="${MAINPATH}temp_file"
ATTACH_FILES=""
EMAILBODYFILE="${MAINPATH}file_io/cron_log/reportbody-`date +%Y%m%d`.txt"
EMAILSUBJECT="LSR Feed Results - "$now
ERRORBODYFILE="${MAINPATH}file_io/cron_log/errorbody-`date +%Y%m%d`.txt"
ERRORSUBJECT="LSR Feed Error - "$now
SENDFROM="LSR-"$USER

echo "**********************************************************************\r\n" >> $LOGFILE;
echo "RunLSRFeed.sh started at: `date`\r\n"  >> $LOGFILE;

# C3427 check to see is there any previous instance running, if yes then exit.
ps -ef | grep RunLSRFeed.sh | grep $USER | grep -v grep >> $LOGFILE;
ps -ef | grep $USER | grep RunLSRFeed.sh | grep -v grep | wc -l >tmp
processNum=`cat tmp`
rm tmp
if [ -n $processNum ]
then
	if [ $processNum -gt 1 ]
	then
		echo "There is another process is currently running. exit and wait for next run..." >> $LOGFILE;
		exit 1
	fi
fi

# generate email body file

echo "Hello, " > $EMAILBODYFILE
echo "" >> $EMAILBODYFILE
echo "The Lenovo Service Reporting data feed has processed your request.  Please find the results of your request in the attached file(s) below." >> $EMAILBODYFILE
echo "" >> $EMAILBODYFILE
echo "" >> $EMAILBODYFILE
echo "" >> $EMAILBODYFILE
echo "Regards, " >> $EMAILBODYFILE 
echo "" >> $ERRORBODYFILE
echo $SENDFROM >> $EMAILBODYFILE 
echo "" >> $EMAILBODYFILE
echo "N.B.  Please do not respond to this email.  It has been generated from an IBM service machine." >> $EMAILBODYFILE 
echo "" >> $EMAILBODYFILE


# generate error email body file

echo "Hello, " > $ERRORBODYFILE
echo "" >> $ERRORBODYFILE
echo "The Lenovo Service Reporting Feed Program has problem.  Please see the log in the attached file below." >> $ERRORBODYFILE
echo "" >> $ERRORBODYFILE
echo "" >> $ERRORBODYFILE
echo "Regards, " >> $ERRORBODYFILE 
echo "" >> $ERRORBODYFILE
echo $SENDFROM >> $ERRORBODYFILE 
echo "" >> $ERRORBODYFILE
echo "N.B.  Please do not respond to this email.  It has been generated from an IBM service machine." >> $ERRORBODYFILE 
echo "" >> $ERRORBODYFILE



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


# give help if needed
if [ $# -eq 2 ] 
then   
    # C3427 look for data file in email
    echo "Checking emails ...\r\n" >> $LOGFILE;
	$HOME/cronjobs/parsemail/parsemail.sh lsr >> $LOGFILE;
    # change to working directory
    cd $MAINPATH

    #  Run the Java Program
     java LSRFeed.class $1 $2 >> $LOGFILE 2>&1
     # C3918 start 
	 java com.ibm.lsr.process.LSRProcessMain.class  $1 $2 AUTO
	 # C3918 end

else 
    if [ $# -eq 0 ]
    then
    	# C3427 look for data file in email
	    echo "Checking emails ...\r\n" >> $LOGFILE;
		$HOME/cronjobs/parsemail/parsemail.sh lsr >> $LOGFILE;
        # change to working directory
        cd $MAINPATH
		
		# C3427 make sure $LSRUSERID $LSRPWD are not empty
		if [[ $LSRUSERID == "" || $LSRPWD == "" ]]
		then
			echo "user id or password is empty, please check .profile.\r\n" >> $LOGFILE;
			exit 1
		else
        #  Run the Java Program
         java  LSRFeed.class $LSRUSERID $LSRPWD >> $LOGFILE 2>&1
         # C3918 start 
		 java com.ibm.lsr.process.LSRProcessMain.class  $LSRUSERID $LSRPWD AUTO
		 # C3918 end

        fi
    else
		echo "RunLSRFeed.sh requires 0 or 2 parameters...\r\n" >> $LOGFILE;
   		echo "Usage: RunLSRFeed.sh [<userid> <password>]  \r\n" >> $LOGFILE;
   		echo "RunLSRFeed.sh ended at: `date`\r\n" >> $LOGFILE;
   		echo "**********************************************************************\r\n" >> $LOGFILE;
   		exit 1
   fi
fi

if [ $? -gt 0 ] 
then
	echo "LSRFeed process failed.\r\n" >> $LOGFILE; 
	#mail -s "LSRFeed process failed." $emailList < $LOGFILE >> $LOGFILE 2>&1;
	vmail.sh -t "$emailList" -f "$SENDFROM" -s "$ERRORSUBJECT" -b $ERRORBODYFILE -a $LOGFILE >> $LOGFILE 2>&1;
else
	echo "\r\n"  >> $LOGFILE;
	cd $BUSINESS_PATH
	# list all business lines
	ls -d1 ./* > $TEMP_FILE
	# go through all business lines
	while read CURRENTDIR
	do 
		# remove "./"
		CURRENTDIR=`echo $CURRENTDIR | cut -c 3-`

		if [ -d $CURRENTDIR ] 
		then
			echo "The business:$CURRENTDIR\r\n" >> $LOGFILE;

			REPORT_NAME=`echo feed_rpt_${CURRENTDIR}` 
			cd $CURRENTDIR		
			if [ -s ${REPORT_NAME}.txt ]
			then
				echo "Input file in ${CURRENTDIR} is processed.\r\n" >> $LOGFILE
				BACKUP_FILE_NAME="backup/${REPORT_NAME}_`date +%Y%m%d-%H%M%S`.txt"
				mv ${REPORT_NAME}.txt $BACKUP_FILE_NAME  	>>$LOGFILE 2>&1
				ATTACH_FILES="  ${ATTACH_FILES} -a ${BUSINESS_PATH}/${CURRENTDIR}/${BACKUP_FILE_NAME}"
			else
				echo "There is no input file to process.\r\n" >>$LOGFILE
			fi
			cd ..
		fi

	done < $TEMP_FILE
	cd $MAINPATH
	if [ -n "$ATTACH_FILES" ]
	then 	 
		#getEmailList();
		echo "The report is sent to ${emailList}" >> $LOGFILE
		#vmail.sh $emailList ${ATTACH_FILES} >> $LOGFILE 2>&1;
		vmail.sh -t "$emailList" -f "$SENDFROM" -s "$EMAILSUBJECT" -b $EMAILBODYFILE ${ATTACH_FILES} >> $LOGFILE 2>&1;
	fi	
fi
cd $MAINPATH

rm  $TEMP_FILE >>  $LOGFILE 2>&1;
rm $EMAILBODYFILE >>  $LOGFILE 2>&1;
rm $ERRORBODYFILE >>  $LOGFILE 2>&1;

echo "RunLSRFeed.sh ended at: `date`\r\n" >> $LOGFILE;
echo "**********************************************************************\r\n" >> $LOGFILE;

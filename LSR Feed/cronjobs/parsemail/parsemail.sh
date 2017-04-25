#!/bin/ksh 
#
# Name:  parsemail.sh
# ---------------------------------
# Description:  This script will proform the following tasks:
# 				1. open mailbox looking for a subject with some specific text.
#  				2. parse the mails that matches subject text in mailbox, get attached zip files
# 				3. email will be procesed beginning with the oldest email first (FIFO)
#				4. If there is more than one email with the target subject we can only process on  
# 					one email at time
#               2. extract and place the zip files into to the directory 
# 					lsrfeed/file_io/historic_data. 
#               3. remove the mail from mailbox
# ---------------------------------
# Arguments: env: lsr or clm  
# ---------------------------------
# Change History 
# 12/14/2008   	Mark Ma - Initial
# 07/03/2010	Mark Ma - Version 1.2.0 C4168 added check pn_exclude:
#							1.parse email for subject=PN_EXCLUDE and saved attachment to target path
#							2.added target path /home/lsrprod/lsrfeed/file_io/lsr_feed/pn_exclude for Billing_Invalid_PN.csv in report.config
#							3.added load data from attachement and import to db2 table LENOVO.PN_EXCLUDE
#							4.send email back to sender.
#     
# -------------------------------------------------------------------------------

version="1.2.0"

if [ ! $# -eq 1 ] 
then   
   	echo "**********************************************************************\r\n" 
   	echo "`date` \r\n" 
	echo "parsemail.sh requires 1 parameter (lsr or clm)...\r\n" 
   	echo "Usage: parsemail.sh lsr  \r\n" 
   	exit 1
fi


# ---------------------------------------------------------------------------------------
# Function: 	IndexOf()
# Description: 	a function return the position where $2 in S1 
# Input: 		string1, string2 
# Output: 		0: S2 is not included in S1
#               >0:  S2 is included in S1
# Usage: 		IndexOf string1 string2
# ---------------------------------------------------------------------------------------


IndexOf()
{	
	awk -v a="$1" -v b="$2" 'BEGIN{print index(a,b)}'
}


# ---------------------------------------------------------------------------------------
# Function: 	loadConfig()
# Description: 	read data from config file into two array
# Input: 		none
# Output: 		fill up array report_keys and report_paths
# Usage: 		loadConfig
# ---------------------------------------------------------------------------------------
loadConfig(){

	if [ -e $CONFIG_FILE ]
	then
		i=0
		while read report_key report_path
		do 
			report_keys[$i]=${report_key}
			report_paths[$i]=${report_path}
			(( i=i+1 ))	
		done < $CONFIG_FILE
	else
		echo "the config file $CONFIG_FILE does not exist." 
		exit -1
	fi
}


# ---------------------------------------------------------------------------------------
# Function: 	GetPathByReportKey()
# Description: 	there is a config file which pre-defined report file names and matched 
# 				directories. this function will return target directory based on input 
#				report key.
# Input: 		report key string 
# Output: 		target directory
# Usage: 		GetPathByReportKey report_key
# ---------------------------------------------------------------------------------------

GetPathByReportKey()
{
	i=0
	while [ $i -lt ${#report_keys[*]}  ]
		do 
		  ret=`IndexOf "$1" "${report_keys[$i]}"`
		  if [ $ret -gt 0 ]
		  then
		  	echo "${report_paths[$i]}" 
		  	break;
		  fi		  
		  (( i = i + 1))
	done
	
}

# C4168 process pn_exclude file
# -------------------------------------------------------------------------------------------
# Function: Process_PN_Exclude()
# Description: this function will do the following works:
# 	1. Import data from email attachement into LENOVO.PN_EXCLUDE table.
#	2. Send email back to sender.
#
# Input: 
#	data file name: the data file path and name detached from email
#	
# Output: none
# Usage: Process_PN_Exclude fileName sender
# --------------------------------------------------------------------------------------------


Process_PN_Exclude()
{
	echo "processing pn_exclude file $1 ...\r\n" 
	db2 connect to $dbname > $DB2LOGFILE
	db2 "IMPORT FROM $1 OF DEL RESTARTCOUNT 1 REPLACE INTO LENOVO.PN_EXCLUDE (PARTNO)" >> $DB2LOGFILE
	ErrorCnt=0;
	ErrorCnt=`grep "SQLSTATE=" $DB2LOGFILE | grep -v "SQLSTATE=23505" | wc -l `;
  	if [ $ErrorCnt -eq 0 ]; then
  		# generate email body file
		echo "Hello, " > $EMAILBODYFILE
		echo "" >> $EMAILBODYFILE
		echo "The Lenovo Service Reporting System has received and processed your PN_EXCLUDE data file successfully.  " >> $EMAILBODYFILE
		echo "" >> $EMAILBODYFILE
		cat $DB2LOGFILE | grep "Number of rows" >> $EMAILBODYFILE
		echo "" >> $EMAILBODYFILE
		echo "Regards, " >> $EMAILBODYFILE 
		echo "" >> $ERRORBODYFILE
		echo $SENDFROM >> $EMAILBODYFILE 
		echo "" >> $EMAILBODYFILE
		echo "N.B.  Please do not respond to this email.  It has been generated from an IBM service machine." >> $EMAILBODYFILE 
		echo "" >> $EMAILBODYFILE
		$HOME/lsrfeed/vmail.sh -t "${emailList},$SENDER" -f "$SENDFROM" -s "$EMAILSUBJECT" -b $EMAILBODYFILE 
  	else
  		# generate error email body file
		
		echo "Hello, " > $ERRORBODYFILE
		echo "" >> $ERRORBODYFILE
		echo "The Lenovo Service Reporting Processing PN_Exclude Program has problem.  Please see the log in the attached file below." >> $ERRORBODYFILE
		echo "" >> $ERRORBODYFILE
		echo "" >> $ERRORBODYFILE
		echo "Regards, " >> $ERRORBODYFILE 
		echo "" >> $ERRORBODYFILE
		echo $SENDFROM >> $ERRORBODYFILE 
		echo "" >> $ERRORBODYFILE
		echo "N.B.  Please do not respond to this email.  It has been generated from an IBM service machine." >> $ERRORBODYFILE 
		echo "" >> $ERRORBODYFILE
		$HOME/lsrfeed/vmail.sh -t "${emailList}" -f "$SENDFROM" -s "Processing PN_Exclude Program has problem" -b $ERRORBODYFILE -a $DB2LOGFILE
		
  	fi
  	cat $DB2LOGFILE 
  	
	# Clear tempertory files
	
	if [ -e $EMAILBODYFILE ]
	then
		rm $EMAILBODYFILE
	fi
	if [ -e $ERRORBODYFILE ]
	then
		rm $ERRORBODYFILE
	fi
	if [ -e $DB2LOGFILE ]
	then
		rm $DB2LOGFILE
	fi
	if [ -e result.tmp ]
	then
		rm result.tmp
	fi
	
}

# ---------------------------------------------------------------------------------------
# Function: 	ExtractZipFile
# Description: 	a function extract zip file then move extracted files to target directory 
# 				where defined in config file.
# Input: 		name of zip file. 
# Output: 		extract zip file then move extracted files to target directory where defined 
# 				in config file.
# Usage: 		ExtractZipFile zipfile_name
# ---------------------------------------------------------------------------------------

ExtractZipFile()
{
	# get all extracted file names and save in an array
	echo "extracting $1 ....\r\n" 
	current_dir=`pwd`
	cd $ZIP_DIR
	i=0
	ret=`IndexOf "$1" ".zip"`
	if [ ret -gt 0 ]
	then
		jar -xvf $1 | while read null filename 
			do			
				extracted_files[$i]=$filename
				(( i=i+1 ))		
			done
		# find each extracted file's target directory 	
		i=0
		while [ $i -lt ${#extracted_files[*]}  ]
		do
			targetdir=`GetPathByReportKey "${extracted_files[$i]}"`
			echo "${extracted_files[$i]} ==> ${FEED_DATA_DIR}/$targetdir" 
			if [ -n "${extracted_files[$i]}" ]
			then
				move "${extracted_files[$i]}" "${FEED_DATA_DIR}/$targetdir"
			fi
			(( i=i+1 ))
		done
		move $1 $PROCESSED_DIR/$1.$currentYear-$currentMonth-$currentDay-$currentHour-$currentMin-$currentSecond
	else
		if [ -n "$1" ]
		then
			SUBJECT=`echo $SUBJECT | tr -d ' '`
			if [[ "$SUBJECT" == "PN_EXCLUDE" ]]
			then
				cat $1 | tr -d '\r' > "$PN_EXCLUDE_DIR/$1"
				Process_PN_Exclude "$PN_EXCLUDE_DIR/$1"
			else				
				targetdir=`GetPathByReportKey "$1"`
				if [ -n "$targetdir" ]
				then
					echo "$1 ==> ${FEED_DATA_DIR}/$targetdir" 
					move "$1" "${FEED_DATA_DIR}/$targetdir"
				else
					echo "there is no target directory defined for $1 in $CONFIG_FILE, this file will saved in $ZIP_DIR directory." 
				fi				
			fi			
		else
			echo "the file $1 does not exist." 
		fi
	fi
	cd $current_dir
}


################################# Main Logic ##########################################

Name="`basename $0` - ${version}" 

currentYear=`date +%Y`
currentMonth=`date +%m`
currentDay=`date +%d`
currentHour=`date +%H`
currentMin=`date +%M`
currentSecond=`date +%S`


#support both LSR and CLM
ENV=$1
FEED_DIR="$HOME/${ENV}feed"
FILE_IO_DIR="${FEED_DIR}/file_io"
ZIP_DIR="${FILE_IO_DIR}/historic_data"
PROCESSED_DIR="${ZIP_DIR}/processed"
FEED_DATA_DIR="${FILE_IO_DIR}/${ENV}_feed"
CONFIG_FILE="report.config"
WORKING_DIR="$HOME/cronjobs/parsemail"
PN_EXCLUDE_DIR="${FEED_DATA_DIR}/pn_exclude"
SENDFROM="LSR-"$USER
# C4168
CURRENT_DIR=`pwd`
EMAILBODYFILE="${CURRENT_DIR}/emailbody.tmp"
EMAILSUBJECT="RE: PN_EXCLUDE - "`date +%Y-%m-%d`
ERRORBODYFILE="${CURRENT_DIR}/errorEmailBody.tmp"
ERRORSUBJECT="LSR Parse PN_EXCLUDE Error - "`date +%Y-%m-%d`
SENDER=""
DB2LOGFILE="${CURRENT_DIR}/DB2LOG.tmp"
SUBJECT=""


echo "**********************************************************************\r\n" 
echo "$Name started at: `date`\r\n"  


# getEmailList
emailList=""	
grep AIX_EMAIL_REPORT "$HOME/lsrfeed/LSR.properties" | tr -d '\r' | while read junk junk email
do 
	if [ ! -n "$emailList"  ]
		then
		emailList=$email
		else	
		emailList=$emailList,$email		
	fi

done 	

# get LSR database name
dbname=""
grep DATABASE_NAME "$HOME/lsrfeed/LSR.properties" | tr -d '\r' | read junk junk dbname


old_dir=`pwd`
cd $WORKING_DIR

# delete parse email result file
if [ -e result.tmp ]
then
	rm result.tmp
fi
# if zip dir does not exist, create one.
if [ ! -e $ZIP_DIR ]
then
	mkdir $ZIP_DIR
fi
# if pn_exclude dir does not exist, create one.
if [ ! -e $PN_EXCLUDE_DIR ]
then
	mkdir $PN_EXCLUDE_DIR
fi
# if processed dir does not exist, create one.
if [ ! -e $PROCESSED_DIR ]
then
	mkdir $PROCESSED_DIR
fi
# load config file into memory
loadConfig
# get zip file from mailbox (one mail at a time)
cat /var/spool/mail/$USER | awk -v datadir=$ZIP_DIR -f save_mail_attachments.awk 
if [ -e result.tmp ]
then
	mail_number=`awk '/mail_number:/ {print $2}' result.tmp`
	SENDER=`awk '/Send_From:/ {print $2}' result.tmp`
	SUBJECT=`awk '/Subject:/ {print $2}' result.tmp`
	#echo "mail_number = ${mail_number}"	
	i=0
	awk '/Attached_files:/ {print $2}' result.tmp | while read line 
	do
		#save each zip file name in array
		zip_files[$i]=$line
		ExtractZipFile ${zip_files[$i]}
		(( i=i+1 ))		
	done
	# remove the current mail from mailbox
	echo "d ${mail_number}" | mail 
	#echo "the size of zip_files:" ${#zip_files[*]}
fi	

cd $old_dir
echo "$Name ended at: `date`\r\n"  
echo "**********************************************************************\r\n" 



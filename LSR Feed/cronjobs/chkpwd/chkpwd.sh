#! /usr/bin/ksh
#########################################################################################
# Script name: 	chkpwd.sh
# Description: 	this script will check predefined password change date in profile, if 
#              	the password will expire in certain days, this script will send email 
#              	to notify staff to change password. if the password will expire in two
# 			   	days, this script will shut down all cron jobs to prevent password being
#              	locked out.
# Developer: 	Mark Ma
# Usage: 	 	chkpwd.sh
# Parameters: 	none
#
# Change History:
# 2008-02-06 | MM | initial version
# 2008-07-24 | MM | move this script and log file to cronjobs/chkpwd directory, 
#                   changed the date shut down cron jobs same day when password expires
# 2008-12-20 |JWF | V1.2 - C3650
#                - changed logic to allow for additional ID's and passwords to be added
# 2009-02-02 |JWF | V1.2.1 amendment to C3650
#                - changed SENDTO parm to "LSR-"$USER  (next version will be 1.3)
#########################################################################################

version="1.2.1"
enviro=$ENV
FILENAME=${enviro}_id.dat


# calculates the last day of the month
# input:  	year month
# output :	days in the month
# usage :	lastday year month

lastday()  {
        integer year month leap
#                         ja fe ma ap ma jn jl ag se oc no de
        set -A mlength xx 31 28 31 30 31 30 31 31 30 31 30 31

        year=$1
        if ((year<1860 || year> 3999)) ; then
                print -u2 year out of range: $year
                return 1
        fi
        month=$2
        if ((month<1 || month> 12)) ; then
                print -u2 month out of range
                return 1
        fi

        if ((month != 2)) ; then
                print ${mlength[month]}
                return 0
        fi

        leap=0
        if ((!(year%100))); then
                ((!(year%400))) && leap=1
        else
                ((!(year%4))) && leap=1
        fi

        feblength=28
        ((leap)) && feblength=29
        print $feblength
        return 0
}

# calculates the number of days since
# input:	year month day
# output: 	the number days 
# usage: 	date2jd year month day

date2jd() {
        integer ijd day month year mnjd jd lday

        year=$1
        month=$2
        day=$3
        lday=$(lastday $year $month) || exit $?

        if ((day<1 || day> lday)) ; then
                print -u2 day out of range
                return 1
        fi

        ((standard_jd = day - 32075 
           + 1461 * (year + 4800 - (14 - month)/12)/4 
           + 367 * (month - 2 + (14 - month)/12*12)/12 
           - 3 * ((year + 4900 - (14 - month)/12)/100)/4))
        ((jd = standard_jd-2400001))


        print $jd
        return 0
}

# clculates the date based on number of days since ...
# input :	number of days
# output :	year month day
# usage :	jd2date number

jd2date()
{
        integer standard_jd temp1 temp2 jd year month day

        jd=$1
        if ((jd<1 || jd>782028)) ; then
                print julian day out of range
                return 1
        fi
        ((standard_jd=jd+2400001))
        ((temp1 = standard_jd + 68569))
        ((temp2 = 4*temp1/146097))
        ((temp1 = temp1 - (146097 * temp2 + 3) / 4))
        ((year  = 4000 * (temp1 + 1) / 1461001))
        ((temp1 = temp1 - 1461 * year/4 + 31))
        ((month = 80 * temp1 / 2447))
        ((day   = temp1 - 2447 * month / 80))
        ((temp1 = month / 11))
        ((month = month + 2 - 12 * temp1))
        ((year  = 100 * (temp2 - 49) + year + temp1))
        print $year $month $day
        return 0
}

reportError(){
	$HOME/lsrfeed/vmail.sh -t "$emailList" -f "LSR-"$USER -s "ACTION REQUIRED: chkpwd.sh error." -b $LOGFILE  >> $LOGFILE 2>&1;
	exit 1
}

# generate email body file
# usage: genemailbody serverName daysLeft
# input: $ENVS: the name of server
# daysLeft: the number of days the password will expire.
# output: a email body file generated.
#

genemailbody(){
	echo "Hello, " 
	echo "" 
	  if [ $3 == "Y" ] || [ $3 == "y" ] ;  then
	   echo "The Password Check Utility chkpwd.sh has detected the password for $1 will expire in $2 days .  Please change it ASAP. Otherwise all cron jobs will be shut down." 
	   echo "" 
	   echo "Please Note: After changing the password, don't forget update the env line (${1}) with the new password and changedate using yyyy-MM-dd format in the \"$IDFILE\" file." 
	  else
	   echo "The Password Check Utility chkpwd.sh has detected the password for $1 will expire in $2 days .  Please change it ASAP."
	   echo "" 
	   echo "Please Note: After changing the password, don't forget update env line (${1}) with the new changedate using yyyy-MM-dd format in the \"$IDFILE\" file." 
	  fi;
	echo "" 
	echo "Regards, " 
	echo "" 
	echo $SENDFROM 
	echo ""
	echo "N.B.  Please do not respond to this email.  It has been generated from an IBM service machine."  
	echo "" 
}

# generate final email body file
# usage: genemailbody serverName 
# input: $ENVS: the server name 
#
# output: a email body file generated.
#

genfinalbody(){
	echo "Hello, " 
	echo ""
	 if [ $2 == "Y" ] || [ $2 == "y" ] ;  then
	   echo "The Password Check Utility chkpwd.sh version $version has detected the password for $1 expired today (`date`). All cron jobs are shut down.  Please change it ASAP.  "
	   echo "" 
	   echo "Please Note: After changing the password, don't forget update the env line (${1}) with the new password and changedate using yyyy-MM-dd format in the \"$IDFILE\" file."
	  else
	   echo "The Password Check Utility chkpwd.sh version $version has detected the password for $1 expired today (`date`). Please change it ASAP.  "
	   echo "" 
	   echo "Please Note: After changing the password, don't forget update the env line (${1}) with the new changedate using yyyy-MM-dd format in the \"$IDFILE\" file."
	 fi;
	echo "" 
	echo "Regards, " 
	echo ""  
	echo $SENDFROM 
	echo "" 
	echo "N.B.  Please do not respond to this email.  It has been generated from an IBM service machine." 
	echo "" 
}

################################# Main Logic ##########################################

Name="`basename $0` - ${version}" 

currentYear=`date +%Y`
currentMonth=`date +%m`
currentDay=`date +%d`


## load SERVERS

#######################################################
#  Get the input data file name
#  ensure name is lower case
#######################################################

enviro=$(echo $USER | tr ‘[A-Z]’ ‘[a-z]’)
FILENAME=${enviro}_id.dat
IDFILE=$HOME/$FILENAME
j=1    
while read LINE
do
 firstchar=`expr substr "$LINE" 1 1`
    if [[ ${#LINE} > 2 ]] ; then
          if [[ "$firstchar" != '#' ]] ; then
	    ##  parse the variables and export
	     echo $LINE | read env userid password changedate expirydays stpcrn rest
	     ENVS[$j]=$env
	     CHANGEDATES[$j]=$changedate
	     EXPIRYDAYSS[$j]=$expirydays
	     STPCRNS[$j]=$stpcrn
	     ((j=j+1))
	  fi
    fi

done < $IDFILE

((j=j-1))

VARLINES=$j
MAINPATH="$HOME/cronjobs/chkpwd"
LOGFILE="${MAINPATH}/chkpwd-`date +%Y%m%d`.log"
EMAILBODYFILE="${MAINPATH}/notifybody-`date +%Y%m%d`.txt"
FINALBODYFILE="${MAINPATH}/finalbody-`date +%Y%m%d`.txt"

NOTIFYDAYS="10"

echo "**********************************************************************" >> $LOGFILE;
echo "chkpwd1.sh started at: `date`\r\n"  >> $LOGFILE;

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


num=1

until [ $num -gt $VARLINES ]
do
	# validate change date
	if [ -z ${CHANGEDATES[$num]} ]
	then
		echo "Abort! The ${ENVS[$num]} PWDCHANGEDATE is empty. please fix it in the \"$IDFILE\" file."\r\n >> $LOGFILE;
		reportError;
	fi;
	dte1=${CHANGEDATES[$num]}

	if [ `echo $dte1 | cut -c5` != '-' ] || [ `echo $dte1 | cut -c8` != '-' ]
	then
		echo "Abort! The date format of ${ENVS[$num]}PWDCHANGEDATE: $dte1 is incorrect. The format must be yyyy-MM-dd. Please fix it in the \"$IDFILE\" file.\r\n" >> $LOGFILE;
		reportError;
	fi;

	# parse the dates and the stop cron indcator
	changeDay=`echo ${CHANGEDATES[$num]} | cut -c9-10 `
	changeMonth=`echo ${CHANGEDATES[$num]} | cut -c6-7 `
	changeYear=`echo ${CHANGEDATES[$num]} | cut -c1-4 `
	stopcron=`echo ${STPCRNS[$num]} | tr '[a-z]' '[A-Z]' `               # translate to upper case

	if (($changeDay<1 || $changeDay>31)) ; 	then
		echo "Abort! The day field is wrong in ${ENVS[$num]}PWDCHANGEDATE: $changeYear-$changeMonth-$changeDay.\r\n" >> $LOGFILE;
		reportError;
	fi;
	if (($changeMonth<1 || $changeMonth>12)) ; then
		echo "Abort! The month field is wrong in ${ENVS[$num]}PWDCHANGEDATE: $changeYear-$changeMonth-$changeDay.\r\n" >> $LOGFILE;
		reportError;
	fi;
	if (($changeYear<2007)) ; then
		echo "Abort! The year field is wrong in ${ENVS[$num]}PWDCHANGEDATE: $changeYear-$changeMonth-$changeDay.\r\n" >> $LOGFILE;
		reportError;
	fi;
	echo "\r\nSERVERS:${ENVS[$num]} change date: ${CHANGEDATES[$num]} \r\n" >> $LOGFILE;
	echo ${CHANGEDATES[$num]} | tr '-' ' ' | read changeYear changeMonth changeDay >> $LOGFILE;
	changeDays=$(date2jd $changeYear $changeMonth $changeDay)
	expiryDays=${EXPIRYDAYSS[$num]}
	eval '(('expiryDays=${expiryDays}+$changeDays'))' 
	jd1=$(date2jd $currentYear $currentMonth $currentDay) 
	eval '(('dayleft=${expiryDays}-$jd1'))'
	
	if ((${dayleft}<${NOTIFYDAYS})) 
	then
	      if ((${dayleft}<1))
		then
		     if [ ${STPCRNS[$num]} == "Y" ] || [ ${STPCRNS[$num]} == "y" ] ; then	
		        crontab -r >> $LOGFILE 2>&1;
			echo "\r\n" >> $LOGFILE;
			echo "the password expired today, all cron jobs are shut down. a final notification email is sent." >> $LOGFILE;
			genfinalbody ${ENVS[$num]} ${STPCRNS[$num]} > $FINALBODYFILE;
			$HOME/lsrfeed/vmail.sh -t "$emailList" -f "LSR-"$USER -s "CAUTION: All cron jobs are shut down due to ${ENVS[$num]} password expiry." -b $FINALBODYFILE  >> $LOGFILE 2>&1;
		      else
		        echo "\r\n" >> $LOGFILE;
		        echo "the password expired today, a final notification email is sent." >> $LOGFILE;
			genfinalbody ${ENVS[$num]} ${STPCRNS[$num]} > $FINALBODYFILE;
			$HOME/lsrfeed/vmail.sh -t "$emailList" -f "LSR-"$USER -s "CAUTION: ${ENVS[$num]} password expiry notice.
			" -b $FINALBODYFILE  >> $LOGFILE 2>&1;
		      fi; 	
		  
		else			
			genemailbody ${ENVS[$num]} $dayleft ${STPCRNS[$num]} > $EMAILBODYFILE
			$HOME/lsrfeed/vmail.sh -t "$emailList" -f "LSR-"$USER -s "ACTION REQUIRED: ${ENVS[$num]} password will expire in ${dayleft} days." -b $EMAILBODYFILE  >> $LOGFILE 2>&1;
			echo "the password will expire in $dayleft days, a notification email is sent." >> $LOGFILE;
		fi
	else
		echo "${ENVS[$num]} password will expired in $dayleft days"  >> $LOGFILE;
	fi	
	
	((num=num+1))
done

exit 0
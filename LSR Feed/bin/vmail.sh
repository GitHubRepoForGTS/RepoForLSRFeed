#! /usr/bin/ksh
############################################################################
#
# Send email with attachement utility 
# Created by Victor Monaco 2007-08-22
# Usage: attmail.sh 
# Parameters:
#

#  Options 
#
#  -t to address (required)
#  -f from addr  (optional)
#  -b bodyfile       (optional)
#  -s subject    (optional)
#  -a attachment (optional)
#
#
# Change History:
#	2008-01-31 Mark Ma
#	2010-04-02 | MM | Version 1.2 -- Remove extra empty line added in attachment files.
#	2010-07-13 | MM | Version 1.3 -- Changed attachment file name (excluded path name)
#
#############################################################################
set -f    

version=1.3

#
# Format the variable $now as "Friday 18 December 1998 16:22"
#
xday=`date +%a`
xdayno=`date +%d`
xmonth=`date +%b`
xyear=`date +%Y`
xtime=`date +%H:%M:%S`
now=$xday", "$xdayno" "$xmonth" "$xyear" "$xtime


SENDTO=""
SENDFROM=""
BODYFILE=""
SUBJECT=""
DEBUG=0
nattach=0

set -A ATTACH

# parse command line parameters
while getopts ':df:t:b:s:a:' opt ; do
	case $opt in
	d)	DEBUG=1
		#echo "Debug is on"
		;;
	t)
		SENDTO=$OPTARG
		#echo "send to: $SENDTO"
		;;
	f)	SENDFROM=$OPTARG
		#echo "send from: $SENDFROM"
		;;
	b)
		BODYFILE=$OPTARG
		#echo "body files: $BODYFILE"
		;;
	a)
		ATTACH[nattach]=$OPTARG
		#echo "attachement files: ${ATTACH[nattach]}"
		((nattach=nattach+1))
		;;
	s)
		SUBJECT=$OPTARG
		#echo "subject: $SUBJECT"
		;;
	\?)
		echo "error: what is -${OPTARG}?"
		;;
	:)
		echo "something do not understand"
		;;
	esac
done

#  Parameter error checking: an address is required

if [ -z "$SENDTO" ] ; then
	echo "-t ADDRESS is required"
	exit 1
fi

if [ -n "$BODYFILE" ] ; then
	if [[ ! -f ${BODYFILE} ||  ! -r ${BODYFILE} ]] ; then
		echo "body file $BODYFILE must exist and be a readable file"
		exit 1
	fi
fi

#
#  Parameter error checking: If ATTACH was specified, it
#  must exist and be readable.  

i=0
errors=0
while ((i<nattach)); do
	if [[ ! -f ${ATTACH[i]} ||  ! -r ${ATTACH[i]} ]] ; then
		echo " ${ATTACH[i]} must exist and be a readable file"
		((errors=errors+1))
	fi
	((i=i+1))
done

if ((errors)) ; then
	echo "attatched files must exists and be a readable file."
	exit 1
fi

#
# Create message boundary number based on process id
#
boundary=JPR$$

#
# Create message id number based on date and process id
#
messid=`date +%d%m%y`$$

#
# Create temporary file based on date time and process id
#
tempfile=/tmp/vmail-`date +%d%m%y%H%M%S`$$
touch $tempfile

#
# Domain is taken from the domain field in /etc/resolv.conf file
#
senderdomain=`grep domain /etc/resolv.conf | cut -f2`


#
# Create header part of email file
#
echo "From: "$SENDFROM >> $tempfile
echo "To: "$SENDTO >> $tempfile
echo "Date: "$now >> $tempfile
echo "Mime-Version: 1.0 "$version >> $tempfile
echo "Content-Type: Multipart/Mixed; boundary=Message-Boundary-"$boundary >> $tempfile
echo "Subject: "$SUBJECT>> $tempfile

echo "Priority: normal" >> $tempfile
echo "Message-Id: <"$messid"."$senderdomain >> $tempfile
echo "Status: RO" >> $tempfile
echo "" >> $tempfile
echo "" >> $tempfile
echo "--Message-Boundary-"$boundary >> $tempfile
echo "Content-type: text/plain; charset=US-ASCII" >> $tempfile
echo "Content-transfer-encoding: 7BIT" >> $tempfile
echo "Content-description: Read Me First" >> $tempfile
echo "" >> $tempfile

#
# Create mail message body part of email file
#

cat  $BODYFILE >> $tempfile

#
# For each file, create enclosure for the file.
#

messno=0
while [ $nattach -gt $messno ]
do

	#messno=`expr $messno + 1`

	echo "--Message-Boundary-"$boundary >> $tempfile
	echo "Content-type: Application/Octet-Stream; name=`basename ${ATTACH[$messno]}`; type=Text" >> $tempfile
	echo "Content-description: attachment; filename=${ATTACH[$messno]}" >> $tempfile
	echo "" >> $tempfile
	cat ${ATTACH[$messno]} >> $tempfile
	echo "\r" >> $tempfile
	messno=`expr $messno + 1`
done

#
#  Send email message straight to sendmail. 
#
if ((DEBUG)) ; then
#/usr/lib/sendmail $sendto < $tempfile
	echo "debug is on"
	cat $tempfile
else
	#
	#  Send email message straight to sendmail. 
	#
	/usr/lib/sendmail $SENDTO < $tempfile
	#
	# Remove temp file
	#
	rm $tempfile
fi
exit 0



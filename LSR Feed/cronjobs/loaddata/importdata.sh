#!/bin/ksh 
#
###########################################################################################
# description: Extract data from remote databases  and load into LSR database:
# Remote database		remote table		LSR table	
# ------------------------------------------------------------------------------
# LENDB				LENOVO.TIER_1_CRU	LENOVO.TIER_1_CRU
# 				LENOVO.SDF		LENOVO.SDF
#				LENOVO.SDF_PIVOT	LENOVO.SDF_PIVOT
#				LENOVO.MTYPE_PART	LENOVO.MTYPE_PART
#				LENOVO.PARTS		LENOVO.PARTS
#				LENOVO.MTYPE_LOAD		LENOVO.MTYPE_LOAD
#				LENOVO.WAC_FAMILY_CAP	LENOVO.WAC_FAMILY_CAP
# ZBACDB2			BIDS.BTGMACH		LENOVO.BTGMACH	
#				BIDS.BTGSVPAC		LENOVO.BTGSVPAC
# CDNIW			
#
# This utility is designed to run in following fashions:
#
#	1. "all": execute full extract/load all tables
#		importdata.sh all
#
#	2. <days_left_in_current_month>: run as cron job once a month n days before the month ends.
#	if  If the load was unsuccesful then try to reload the unsuccessful 
#	tables the next day.
#		importdata.sh <days_left_in_current_month>
#
#	3. <tablename>: only extract/load specific table, the <tablename> must be one of following:
#				"TIER_1_CRU", "BTGMACH", "BTGSVPAC", "SDF", "SDF_PIVOT"	, 
#                               "MTYPE_PART", "PARTS", "MTYPE_LOAD", "WAC_FAMILY_CAP", "RTO"
#		importdata.sh <tablename>
#   4. <week_days>:C3783 MON, TUE, WED, THU, FRI,SAT, SUN run a full load only on provided week days.
# 
# usage: 	importdata.sh <parameter>
#
# Author: 	Mark Ma
# CR#:   	C3164 2008-11-17
#
# Change History:
#
# 2008-11-17 | MM | initial version.
# 2009-01-29 | JWF| C3701 - Changed SQL for BTGMACH to get Status's (STATUS IN ('PW', 'SH', 'SI', 'SO'))
#                         - Added $USER (logon userid to the email FROM variable) to clarify where informational Email message originate 
# 2009-02-12 | HS| C3708 - Reqirement 2 Import data from CDNIW to LSR LENOVO.DR_STATUS_BILL table
# 2009-03-12 | MM | C3783 - change the current load schedule to every Friday at beginning at 7:00 AM.
#							Added week name as parameter i.e. Mon, Tue, Wed, Thu, Fri, Sat, Sun
#							importdata.sh Fri 
#							it will run full load on Friday
# 2009-05-11 | HS| C3863 - Import 4 tables ("MTYPE_PART", "PARTS", "MTYPE", "WAC_FAMILY_CAP") to LSR database
#                          Modified load sequence for loading all tables
# 2009-08-21 | MM | C3949 - New World Wide Code processes and miscellaneous changes.
# 							1. Removed backlog related process.
# 2009-10-29 | MM | C4003 - Rename LENOVO.MTYPE to LENOVO.MTYPE_LOAD
# 2009-11-30 | MM | C4042 - Version: 1.1.5 add new column:  EXTERNAL_CRU CHAR 1 nullable to table LENOVO.MTYPE_PART.
# 2010-02-02 | MM |       - Version: 1.1.6 using real column names instead of using * for export  TIER_1_CRU
# 2010-11-01 | MM | C4235 - Version: 1.2  New RTO table from LENDB
# 2011-07-06 | MM | C4358 - version: 1.2.1 Added report error on load failure SQL2036N
############################################################################################

version=1.2.1  

# Convert log file into DOS/Windows format
# input:  	name of log name
# output :	
# usage :	unix2doc logFileName

unix2doc(){	 
	if [ -s $1 ] 
	then
		awk 'sub("$", "\r")' $1 >tempfile;
		rm $1
		mv tempfile $1
	fi	
}

# when there is error occurred, send email 
# input:  remote table name
# output :	
# usage :	exitError tableName

sendError(){
	# generate error email body file
	ERRORSUBJECT="Load data from remote tables failed "
	echo "Hello, " > $ERRORBODYFILE
	echo "" >> $ERRORBODYFILE
	echo "Load data from remote tables failed.  Please see the log in the attached file below." >> $ERRORBODYFILE
	echo "" >> $ERRORBODYFILE
	echo "" >> $ERRORBODYFILE
	echo "Regards, " >> $ERRORBODYFILE 
	echo "" >> $ERRORBODYFILE
	echo $SENDFROM >> $ERRORBODYFILE 
	echo "" >> $ERRORBODYFILE
	echo "N.B.  Please do not respond to this email.  It has been generated from an IBM service machine." >> $ERRORBODYFILE 
	echo "" >> $ERRORBODYFILE
	${FEEDDIR}/vmail.sh -t "$emailList" -f "$SENDFROM" -s "$ERRORSUBJECT" -b $ERRORBODYFILE -a $ERRORFILE >> $LOGFILE 2>&1;
	rm $ERRORBODYFILE
	
}


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


# extract data from remote table then import into local database.
# input:  	tableName
# output :	
# usage :	processData tableName

processData(){
	
	ErrorCnt=0;
	#other error count
	ErrorCnt1=0;
		
	echo "***************************************************************************************" >$PROCESS_LOG;
	echo "Extract data from $1..." >> $PROCESS_LOG;
	
	# C3863, Hang Shi, 2009-05-11
	if [[ $1 == "TIER_1_CRU" || $1 == "SDF" || $1 == "SDF_PIVOT" || $1 == "MTYPE_PART" || $1 == "PARTS" || $1 == "MTYPE_LOAD" || $1 == "WAC_FAMILY_CAP" || $1 == "RTO" ]]  
	then		
		db2 CONNECT to LENDB USER $LENDBUSERID USING $LENDBPWD >> $PROCESS_LOG;
	fi
	
	if [[ $1 == "BTGMACH" || $1 == "BTGSVPAC" ]] 
	then		
		db2 CONNECT to ZBACDB2 USER $PEWUSERID USING $PEWPWD >> $PROCESS_LOG;
	fi
	
	ErrorCnt=`grep SQLSTATE= $PROCESS_LOG |  wc -l `;
	
	if [ $ErrorCnt -gt 0 ]
	then
		echo "Can not connect to remote database. Please check user name and password in id file." >>$ERRORFILE;
		echo "Extract data from Table $1 did not complete at: `date`" >>$ERRORFILE;		
		echo "$1 failed." > $1
		cat $ERRORFILE >>$PROCESS_LOG;
	else
		echo "Run the Export Program for $1 ..." >>$PROCESS_LOG;	
		if [ -e $IXFDIR/$1.ixf ]
		then
			rm "$IXFDIR/$1.ixf"
		fi
		if [ -e "${WORKINGDIR}/EXPORT_$1.TXT" ]
		then
			rm "${WORKINGDIR}/EXPORT_$1.TXT"
		fi
		ErrorCnt=0;
		case "$1" in
		  TIER_1_CRU)
		    # C3708 added if/else, Hang Shi, 2009-02-12
		    if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
		    then
		      # for DEV and UAT, limit 3000 records		  
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" "select MTYPE, PART_NUM, CLAIM_TIER from LENOVO.TIER_1_CRU"  fetch first 3000 rows only  >> $PROCESS_LOG
		    elif [[ $dbname == "LSRPROD" ]] 
		    then
		      # for production
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" "select MTYPE, PART_NUM, CLAIM_TIER from LENOVO.TIER_1_CRU" >> $PROCESS_LOG
		    else  
		      echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
		    fi 		    
		    ;;
		  SDF)
		    # C3708 added if/else, Hang Shi, 2009-02-12
		    if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
		    then
		      # for DEV and UAT, limit 3000 records		  		  
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT SDF, TOT_MONTHS, PHASE, PHASE_DURATION, SRV_DELIV, WARR_MAINT_UPG, LABOR, TRAVEL, PARTS, HC_MONTHS, CVG_HOURS, CVG_DAYS, CVG_MONTHS, RESP_TIME, RESP_INDICATOR, DESCRIPTION, MON_START, MON_STOP, TUE_START, TUE_STOP, WED_START, WED_STOP, THU_START, THU_STOP, FRI_START, FRI_STOP, SAT_START, SAT_STOP, SUN_START, SUN_STOP, MONTH_COUNT, BATTERY_MONTHS FROM LENOVO.SDF  fetch first 3000 rows only    >> $PROCESS_LOG
		    elif [[ $dbname == "LSRPROD" ]] 
		    then
		      # for production
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT SDF, TOT_MONTHS, PHASE, PHASE_DURATION, SRV_DELIV, WARR_MAINT_UPG, LABOR, TRAVEL, PARTS, HC_MONTHS, CVG_HOURS, CVG_DAYS, CVG_MONTHS, RESP_TIME, RESP_INDICATOR, DESCRIPTION, MON_START, MON_STOP, TUE_START, TUE_STOP, WED_START, WED_STOP, THU_START, THU_STOP, FRI_START, FRI_STOP, SAT_START, SAT_STOP, SUN_START, SUN_STOP, MONTH_COUNT, BATTERY_MONTHS FROM LENOVO.SDF  >> $PROCESS_LOG
		    else  
		      echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
		    fi 		    		    
		    ;;
		  SDF_PIVOT)
		    # C3708 added if/else, Hang Shi, 2009-02-12
		    if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
		    then
		      # for DEV and UAT, limit 3000 records		  		  
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT SDF_GROUP, SDF, SDF_IND, TRANSACTION_CODE, PHASE FROM LENOVO.SDF_PIVOT  fetch first 3000 rows only  >> $PROCESS_LOG
		    elif [[ $dbname == "LSRPROD" ]] 
		    then
		      # for production
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT SDF_GROUP, SDF, SDF_IND, TRANSACTION_CODE, PHASE FROM LENOVO.SDF_PIVOT  >> $PROCESS_LOG
		    else  
		      echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
		    fi 		    		    
		    ;;
		  MTYPE_PART)
		    # C3863, Hang Shi, 2009-05-11
		    if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
		    then
		      # for DEV and UAT, limit 3000 records		  		  
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT MTYPE, PART_NUM, CLAIM_TIER, MANDATORY_CRU_TIER, EXTERNAL_CRU FROM LENOVO.MTYPE_PART  fetch first 3000 rows only  >> $PROCESS_LOG
		    elif [[ $dbname == "LSRPROD" ]] 
		    then
		      # for production
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT MTYPE, PART_NUM, CLAIM_TIER, MANDATORY_CRU_TIER, EXTERNAL_CRU FROM LENOVO.MTYPE_PART  >> $PROCESS_LOG
		    else  
		      echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
		    fi 		    		    
		    ;;
		  PARTS)
		    # C3863, Hang Shi, 2009-05-11
		    if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
		    then
		      # for DEV and UAT, limit 3000 records		  		  
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT PART_NUM, NAME_COMMODITY, NAME_FAMILY, DESCRIPTION, GA_DATE, PART_TYPE, INDICATOR, TIER FROM LENOVO.PARTS  fetch first 3000 rows only  >> $PROCESS_LOG
		    elif [[ $dbname == "LSRPROD" ]] 
		    then
		      # for production
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT PART_NUM, NAME_COMMODITY, NAME_FAMILY, DESCRIPTION, GA_DATE, PART_TYPE, INDICATOR, TIER FROM LENOVO.PARTS  >> $PROCESS_LOG
		    else  
		      echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
		    fi 		    		    
		    ;;
		  MTYPE_LOAD)
		    # C3863, Hang Shi, 2009-05-11
		    if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
		    then
		      # for DEV and UAT, limit 3000 records		  		  
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT MTYPE, DIVISION, PM_CODE, ITEM_DESC, BRAND, IS_PSEUDO, IS_VALID, UPD_DT, FAMILYNAME, FAMILYNAMEDESC, SERIESNAME, SERIESNAMEDESC FROM LENOVO.MTYPE  fetch first 3000 rows only  >> $PROCESS_LOG
		    elif [[ $dbname == "LSRPROD" ]] 
		    then
		      # for production
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT MTYPE, DIVISION, PM_CODE, ITEM_DESC, BRAND, IS_PSEUDO, IS_VALID, UPD_DT, FAMILYNAME, FAMILYNAMEDESC, SERIESNAME, SERIESNAMEDESC FROM LENOVO.MTYPE  >> $PROCESS_LOG
		    else  
		      echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
		    fi 		    		    
		    ;;
		  WAC_FAMILY_CAP)
		    # C3863, Hang Shi, 2009-05-11
		    if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
		    then
		      # for DEV and UAT, limit 3000 records		  		  
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT NAME_FAMILY, WAC_CAP FROM LENOVO.WAC_FAMILY_CAP  fetch first 3000 rows only  >> $PROCESS_LOG
		    elif [[ $dbname == "LSRPROD" ]] 
		    then
		      # for production
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT NAME_FAMILY, WAC_CAP FROM LENOVO.WAC_FAMILY_CAP  >> $PROCESS_LOG
		    else  
		      echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
		    fi 		    		    
		    ;;		    
		  BTGMACH)
		    # C3708 added if/else, Hang Shi, 2009-02-12
		    if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
		    then
		      # for DEV and UAT, limit 3000 records		  		  
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" "SELECT MACH_ID, MACH_TYPE, MODEL, SERIAL, STATUS, SHIP_DATE, WAR_EXP_DATE, SDF, COUNTRY FROM BIDS.BTGMACH WHERE SHIP_DATE >= DATE('2005-03-01') AND STATUS IN ('PW', 'SH', 'SI', 'SO')"  fetch first 3000 rows only >> $PROCESS_LOG		  		   
		    elif [[ $dbname == "LSRPROD" ]] 
		    then
		      # for production
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" "SELECT MACH_ID, MACH_TYPE, MODEL, SERIAL, STATUS, SHIP_DATE, WAR_EXP_DATE, SDF, COUNTRY FROM BIDS.BTGMACH WHERE SHIP_DATE >= DATE('2005-03-01') AND STATUS IN ('PW', 'SH', 'SI', 'SO')"  >> $PROCESS_LOG
		    else  
		      echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
		    fi 		    		    
		    ;;
		  BTGSVPAC)
		    # C3708 added if/else, Hang Shi, 2009-02-12
		    if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
		    then
		      # for DEV and UAT		  
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT MACH_ID, MA_SDF, SERV_START_DATE, SERV_END_DATE FROM BIDS.BTGSVPAC  fetch first 3000 rows only >> $PROCESS_LOG
		    elif [[ $dbname == "LSRPROD" ]] 
		    then
		      # for production
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" SELECT MACH_ID, MA_SDF, SERV_START_DATE, SERV_END_DATE FROM BIDS.BTGSVPAC >> $PROCESS_LOG
		    else  
		      echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
		    fi 
		    ;; 
		  RTO)
		    # C4235
		    if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
		    then
		      # for DEV and UAT, limit 3000 records		  
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" "select START_TIME, END_TIME, LAST_ACTION_TIME,	TECHNICIAN_NAME, TECHNICIAN_ID, SESSION_ID, SESSION_TYPE, STATUS, NAME, PHONE_NUMBER, CASE_NUMBER, TECHNICIANS_NAME, CUSTOM_FIELD4, CUSTOM_FIELD5, TRACKING_ID, CUSTOMER_IP, DEVICE_ID, INCIDENT_TOOLS_USED, RESOLVED_UNRESOLVED, CHANNEL_ID, CHANNEL_NAME, CALLING_CARD, CONNECTING_TIME, WAITING_TIME, TOTAL_TIME, ACTIVE_TIME, WORK_TIME, HOLD_TIME, TIME_IN_TRANSFER, REBOOTING_TIME, RECONNECTING_TIME, PLATFORM, GEO, TECHNICIAN_EMAIL, PROBLEM_CODE  FROM RTO.RTO_LMI_SESSION_DETAILS WHERE GEO IN ('US', 'CA')  fetch first 3000 rows only"  >> $PROCESS_LOG
		    elif [[ $dbname == "LSRPROD" ]] 
		    then
		      # for production
		      db2 EXPORT TO "$IXFDIR/$1.ixf" OF IXF MESSAGES "${WORKINGDIR}/EXPORT_$1.TXT" "select START_TIME, END_TIME, LAST_ACTION_TIME,	TECHNICIAN_NAME, TECHNICIAN_ID, SESSION_ID, SESSION_TYPE, STATUS, NAME, PHONE_NUMBER, CASE_NUMBER, TECHNICIANS_NAME, CUSTOM_FIELD4, CUSTOM_FIELD5, TRACKING_ID, CUSTOMER_IP, DEVICE_ID, INCIDENT_TOOLS_USED, RESOLVED_UNRESOLVED, CHANNEL_ID, CHANNEL_NAME, CALLING_CARD, CONNECTING_TIME, WAITING_TIME, TOTAL_TIME, ACTIVE_TIME, WORK_TIME, HOLD_TIME, TIME_IN_TRANSFER, REBOOTING_TIME, RECONNECTING_TIME, PLATFORM, GEO, TECHNICIAN_EMAIL, PROBLEM_CODE  FROM RTO.RTO_LMI_SESSION_DETAILS WHERE GEO IN ('US', 'CA')" >> $PROCESS_LOG
		    else  
		      echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
		    fi 		    
		    ;;     
		  *)
		    echo "$Name. Invalid table name: $1 " >>$ERRORFILE;
		    
		    ;;
		esac
		# Copy message file to logfile 
		echo "***** Message from ${WORKINGDIR}/EXPORT_$1.TXT *****" >>$PROCESS_LOG;
		cat "${WORKINGDIR}/EXPORT_$1.TXT" >>$PROCESS_LOG;
		# catch disk is full error: hard stop
		ErrorCnt1=0
		ErrorCnt1=`grep "The disk is full" $PROCESS_LOG |  wc -l `;
		if [ $ErrorCnt1 -gt 0 ]
		then
			echo "Extract data from $1 did not complete at: `date`\r\n" >>$ERRORFILE;
			echo "The disk is full. hard stop" >>$ERRORFILE;
			cat ${WORKINGDIR}/EXPORT_$1.TXT >>$ERRORFILE;
			echo "$1 failed." > $1

			if [ -e $IXFDIR/$1.ixf ]
			then
				rm "$IXFDIR/$1.ixf"
			fi
			cat $ERRORFILE >>$LOGFILE;
			sendError
			exit 1
		fi
		db2 CONNECT reset >>$PROCESS_LOG
		ErrorCnt=`grep SQLSTATE= $PROCESS_LOG |  wc -l `;
		
		if [ $ErrorCnt -gt 0 ]
		then
			echo "Extract data from $1 did not complete at: `date`\r\n" >>$ERRORFILE;
			cat ${WORKINGDIR}/EXPORT_$1.TXT >>$ERRORFILE;
			echo "$1 failed." > $1
			
			if [ -e $IXFDIR/$1.ixf ]
			then
				rm "$IXFDIR/$1.ixf"
			fi
			cat $ERRORFILE >>$PROCESS_LOG;
		else			
			# Logon to DB2
			chmod 644 "$IXFDIR/$1.ixf" >> $PROCESS_LOG;
			# remove message files
			if [ -e "${WORKINGDIR}/EXPORT_$1.TXT" ]
			then
				rm "${WORKINGDIR}/EXPORT_$1.TXT"
			fi
			ErrorCnt=0;
			db2 CONNECT to $dbname USER $LSRUSERID USING $LSRPWD >> $PROCESS_LOG
			ErrorCnt=`grep SQLSTATE= $PROCESS_LOG |  wc -l `;
			if [ $ErrorCnt -gt 0 ]
			then
				echo "Load data $1 did not complete at: `date`\r\n" >>$ERRORFILE
				echo "Can not connect to $dbname. Please check user name and password in id file." >>$ERRORFILE
				if [ -e "$IXFDIR/$1.ixf" ]
				then
					rm "$IXFDIR/$1.ixf"
					echo "$1 failed." > $1
				fi
				cat $ERRORFILE >>$PROCESS_LOG;
			else
				echo "Starting Import" >>$PROCESS_LOG
				#  Run the Import Program
				ErrorCnt=0;
				case "$1" in
			  		TIER_1_CRU)
			  		        # C3708 added if/else, Hang Shi, 2009-02-12
			  		        if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
			  		        then
			  			  # for DEV and UAT
						  db2 "LOAD  FROM $IXFDIR/$1.ixf OF IXF METHOD N (MTYPE, PART_NUM, CLAIM_TIER) MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.TIER_1_CRU (MTYPE, PART_NUM, CLAIM_TIER) COPY NO INDEXING MODE AUTOSELECT" >> $PROCESS_LOG
						elif [[ $dbname == "LSRPROD" ]] 
						then
						  # for production
						  db2 "LOAD FROM $IXFDIR/$1.ixf OF IXF METHOD N (MTYPE, PART_NUM, CLAIM_TIER) MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.TIER_1_CRU (MTYPE, PART_NUM, CLAIM_TIER) COPY YES TO /db2/db2_backup/lsrprodloadcopy INDEXING MODE AUTOSELECT" >> $PROCESS_LOG
						else  
						  echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
						fi    
						;;
					 SDF)
			  		        # C3708 added if/else, Hang Shi, 2009-02-12
			  		        if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
			  		        then					 
					 	  # for DEV and UAT
						  db2 "LOAD  FROM  $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.SDF COPY NO INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						elif [[ $dbname == "LSRPROD" ]] 
						then						
						  # for production
						  db2 "LOAD FROM  $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.SDF COPY YES TO /db2/db2_backup/lsrprodloadcopy INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						else  
						  echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
						fi    						
						;;
					  SDF_PIVOT)
			  		        # C3708 added if/else, Hang Shi, 2009-02-12
			  		        if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
			  		        then					  
					  	  # for DEV and UAT
						  db2 "LOAD  FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.SDF_PIVOT COPY NO INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						elif [[ $dbname == "LSRPROD" ]] 
						then						
						  # for production
						  db2 "LOAD FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.SDF_PIVOT COPY YES TO /db2/db2_backup/lsrprodloadcopy INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						else  
						  echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
						fi    						
						;;
					  MTYPE_PART)
			  		        # C3863, Hang Shi, 2009-05-11
			  		        if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
			  		        then					  
					  	  # for DEV and UAT
						  db2 "LOAD  FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.MTYPE_PART COPY NO INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						elif [[ $dbname == "LSRPROD" ]] 
						then						
						  # for production
						  db2 "LOAD FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.MTYPE_PART COPY YES TO /db2/db2_backup/lsrprodloadcopy INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						else  
						  echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
						fi    						
						;;
					  PARTS)
			  		        # C3863, Hang Shi, 2009-05-11
			  		        if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
			  		        then					  
					  	  # for DEV and UAT
						  db2 "LOAD  FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.PARTS COPY NO INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						elif [[ $dbname == "LSRPROD" ]] 
						then						
						  # for production
						  db2 "LOAD FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.PARTS COPY YES TO /db2/db2_backup/lsrprodloadcopy INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						else  
						  echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
						fi    						
						;;						
					  MTYPE_LOAD)
			  		        # C3863, Hang Shi, 2009-05-11
			  		        if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
			  		        then					  
					  	  # for DEV and UAT
						  db2 "LOAD  FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.MTYPE_LOAD COPY NO INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						elif [[ $dbname == "LSRPROD" ]] 
						then						
						  # for production
						  db2 "LOAD FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.MTYPE_LOAD COPY YES TO /db2/db2_backup/lsrprodloadcopy INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						else  
						  echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
						fi    						
						;;						
					  WAC_FAMILY_CAP)
			  		        # C3863, Hang Shi, 2009-05-11
			  		        if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
			  		        then					  
					  	  # for DEV and UAT
						  db2 "LOAD  FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.WAC_FAMILY_CAP COPY NO INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						elif [[ $dbname == "LSRPROD" ]] 
						then						
						  # for production
						  db2 "LOAD FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.WAC_FAMILY_CAP COPY YES TO /db2/db2_backup/lsrprodloadcopy INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						else  
						  echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
						fi    						
						;;												
					  BTGMACH)
			  		        # C3708 added if/else, Hang Shi, 2009-02-12
			  		        if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
			  		        then					  
					  	  # for DEV and UAT
						  db2 "LOAD  FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.BTGMACH COPY NO INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						elif [[ $dbname == "LSRPROD" ]] 
						then						
						  # for production
						  db2 "LOAD FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.BTGMACH COPY YES TO /db2/db2_backup/lsrprodloadcopy INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						else  
						  echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
						fi    						
						;;
					  BTGSVPAC)
			  		        # C3708 added if/else, Hang Shi, 2009-02-12
			  		        if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
			  		        then					  
					  	  # for DEV and UAT
						  db2 "LOAD  FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.BTGSVPAC COPY NO INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						elif [[ $dbname == "LSRPROD" ]] 
						then						
						  # for production
						  db2 "LOAD FROM $IXFDIR/$1.ixf OF IXF MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.BTGSVPAC COPY YES TO /db2/db2_backup/lsrprodloadcopy INDEXING MODE AUTOSELECT" >> $PROCESS_LOG 
						else  
						  echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
						fi    						
						;;					
					 	RTO)
			  		    	# C4235
			  		    	if [[ $dbname == "LSRDEV" || $dbname == "LSRUAT" ]] 
			  		        then
			  			  	# for DEV and UAT
						  		db2 "LOAD  FROM $IXFDIR/$1.ixf OF IXF METHOD N (START_TIME, END_TIME, LAST_ACTION_TIME,	TECHNICIAN_NAME, TECHNICIAN_ID, SESSION_ID, SESSION_TYPE, STATUS, NAME, PHONE_NUMBER, CASE_NUMBER, TECHNICIANS_NAME, CUSTOM_FIELD4, CUSTOM_FIELD5, TRACKING_ID, CUSTOMER_IP, DEVICE_ID, INCIDENT_TOOLS_USED, RESOLVED_UNRESOLVED, CHANNEL_ID, CHANNEL_NAME, CALLING_CARD, CONNECTING_TIME, WAITING_TIME, TOTAL_TIME, ACTIVE_TIME, WORK_TIME, HOLD_TIME, TIME_IN_TRANSFER, REBOOTING_TIME, RECONNECTING_TIME, PLATFORM, GEO, TECHNICIAN_EMAIL, PROBLEM_CODE) MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.RTO (START_TIME, END_TIME, LAST_ACTION_TIME,	TECHNICIAN_NAME, TECHNICIAN_ID, SESSION_ID, SESSION_TYPE, STATUS, NAME, PHONE_NUMBER, CASE_NUMBER, TECHNICIANS_NAME, CUSTOM_FIELD4, CUSTOM_FIELD5, TRACKING_ID, CUSTOMER_IP, DEVICE_ID, INCIDENT_TOOLS_USED, RESOLVED_UNRESOLVED, CHANNEL_ID, CHANNEL_NAME, CALLING_CARD, CONNECTING_TIME, WAITING_TIME, TOTAL_TIME, ACTIVE_TIME, WORK_TIME, HOLD_TIME, TIME_IN_TRANSFER, REBOOTING_TIME, RECONNECTING_TIME, PLATFORM, GEO, TECHNICIAN_EMAIL, PROBLEM_CODE) COPY NO INDEXING MODE AUTOSELECT" >> $PROCESS_LOG
							elif [[ $dbname == "LSRPROD" ]] 
							then
						  	# for production
						  		db2 "LOAD FROM $IXFDIR/$1.ixf OF IXF METHOD N (START_TIME, END_TIME, LAST_ACTION_TIME,	TECHNICIAN_NAME, TECHNICIAN_ID, SESSION_ID, SESSION_TYPE, STATUS, NAME, PHONE_NUMBER, CASE_NUMBER, TECHNICIANS_NAME, CUSTOM_FIELD4, CUSTOM_FIELD5, TRACKING_ID, CUSTOMER_IP, DEVICE_ID, INCIDENT_TOOLS_USED, RESOLVED_UNRESOLVED, CHANNEL_ID, CHANNEL_NAME, CALLING_CARD, CONNECTING_TIME, WAITING_TIME, TOTAL_TIME, ACTIVE_TIME, WORK_TIME, HOLD_TIME, TIME_IN_TRANSFER, REBOOTING_TIME, RECONNECTING_TIME, PLATFORM, GEO, TECHNICIAN_EMAIL, PROBLEM_CODE) MESSAGES ${WORKINGDIR}/LOAD_$1.TXT REPLACE INTO LENOVO.RTO (START_TIME, END_TIME, LAST_ACTION_TIME,	TECHNICIAN_NAME, TECHNICIAN_ID, SESSION_ID, SESSION_TYPE, STATUS, NAME, PHONE_NUMBER, CASE_NUMBER, TECHNICIANS_NAME, CUSTOM_FIELD4, CUSTOM_FIELD5, TRACKING_ID, CUSTOMER_IP, DEVICE_ID, INCIDENT_TOOLS_USED, RESOLVED_UNRESOLVED, CHANNEL_ID, CHANNEL_NAME, CALLING_CARD, CONNECTING_TIME, WAITING_TIME, TOTAL_TIME, ACTIVE_TIME, WORK_TIME, HOLD_TIME, TIME_IN_TRANSFER, REBOOTING_TIME, RECONNECTING_TIME, PLATFORM, GEO, TECHNICIAN_EMAIL, PROBLEM_CODE) COPY YES TO /db2/db2_backup/lsrprodloadcopy INDEXING MODE AUTOSELECT" >> $PROCESS_LOG
						else  
						  echo " Invalid environment $dbname. Please check DATABASE_NAME in $HOME/lsrfeed/LSR.properties." >>$ERRORFILE
						fi    
						;;
					  *)
						echo "Nothing..."
						;;
				esac
				# Copy message file to logfile 
				echo "***** Message from ${WORKINGDIR}/LOAD_$1.TXT *****" >>$PROCESS_LOG;
				cat "${WORKINGDIR}/LOAD_$1.TXT" >>$PROCESS_LOG;
				# C4358 check for SQL2036N
				ErrorCnt=`grep SQL2036N $PROCESS_LOG |  wc -l `;	
				if [ $ErrorCnt -gt 0 ]
				then
					echo "Load data into $1 did not complete at: `date`\r\n" >>$ERRORFILE;
					cat ${WORKINGDIR}/LOAD_$1.TXT >>$ERRORFILE;
					cat $ERRORFILE >>$PROCESS_LOG;
					cat $ERRORFILE >>$LOGFILE;
					sendError
					exit 1
				fi
				ErrorCnt=`grep SQLSTATE= $PROCESS_LOG |  wc -l `;	
				if [ $ErrorCnt -gt 0 ]
				then
					echo "Load data into $1 did not complete at: `date`\r\n" >>$ERRORFILE;
					cat ${WORKINGDIR}/LOAD_$1.TXT >>$ERRORFILE;
					if [ -e "$IXFDIR/$1.ixf" ] 
					then
						# rm "$IXFDIR/$1.ixf"
						echo "$1 failed." > $1
					fi
					cat $ERRORFILE >>$PROCESS_LOG;
				fi
				#echo "db2 LOAD QUERY TABLE LENOVO.$1" 
				db2 LOAD QUERY TABLE LENOVO.$1 > $TABLECHECK;
				j=0
				status=""
				while read line;do
   					(( j=j+1 ))
   					if [ $j = 2 ]
   					then
      					status=$line
      					break
				    fi
				done < $TABLECHECK;
				
				echo "The table LENOVO.$1 Status: \"$status\" " >>$PROCESS_LOG;
				if [ $status != "Normal" ]
				then
					echo "Warning: The table LENOVO.$1 Status: \"$status\" is not Normal after load data." >>$ERRORFILE;
				fi
				rm $TABLECHECK;
				
			fi
			db2 CONNECT reset >>$PROCESS_LOG;

			echo "database connection reset" >>$PROCESS_LOG;
			# only delete the export file when everything is fine.
			if [ $ErrorCnt = 0 ] 
			then
				if [ -e "$IXFDIR/$1.ixf" ] 
				then
					rm "$IXFDIR/$1.ixf"
				fi
				# remove message files
				if [ -e "${WORKINGDIR}/LOAD_$1.TXT" ]
				then
					rm "${WORKINGDIR}/LOAD_$1.TXT"
				fi
				echo "Load data into $1 completed sucessfully at: `date`\r\n" >>$PROCESS_LOG;
				cat $PROCESS_LOG >> $LOGFILE;
				rm $PROCESS_LOG
			fi
		fi
	fi
}

loadAll(){
	i=0
		while [ $i -lt ${#table[*]}  ]
		do
			processData ${table[$i]}
			(( i=i+1 ))
	done
}

# check if there is any failed process
reprocess(){	
	i=0
	while [ $i -lt ${#table[*]}  ]
	do
		if [ -e ${table[$i]} ]
		then
			processData ${table[$i]}
		fi
		(( i=i+1 ))
	done
}
################################# Main Logic ##########################################

Name="`basename $0` - ${version}" 

currentYear=`date +%Y`
currentMonth=`date +%m`
currentDay=`date +%d`

table[0]="TIER_1_CRU"
table[1]="SDF"
table[2]="SDF_PIVOT"
table[3]="MTYPE_PART"
table[4]="PARTS"
table[5]="MTYPE_LOAD"
table[6]="WAC_FAMILY_CAP"
table[7]="RTO"
table[8]="BTGSVPAC"
table[9]="BTGMACH"

PROCESS_LOG="PROCESS_LOG.log"
WORKINGDIR="$HOME/cronjobs/loaddata"
IXFDIR="$HOME"
FEEDDIR="$HOME/lsrfeed"
LOGFILE="${WORKINGDIR}/loaddata.log";
ERRORFILE="${WORKINGDIR}/loaddata.err";
ERRORBODYFILE="$FEEDDIR/file_io/cron_log/errorbody-`date +%Y%m%d`.txt"
TABLECHECK="${WORKINGDIR}/table_check.txt"
SENDFROM="LSR-"$USER
DAYS_MON_END=2
WEEKDAYS="MON|TUE|WED|THU|FRI|SAT|SUN"
weekday=`echo $1 | tr [a-z] [A-Z]`
goodWeekDay=`echo $WEEKDAYS | grep $weekday | wc -l `

if [ -e ${PROCESS_LOG} ] 
then
	rm ${PROCESS_LOG}
fi

if [ -e ${LOGFILE} ] 
then
	rm ${LOGFILE}
fi
echo " $Name started at: `date` \r\n" > $LOGFILE

if [ -e ${ERRORFILE} ] 
then
	rm ${ERRORFILE}
fi

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

cd ${WORKINGDIR};
 
echo "" >> $LOGFILE;
# check userid and password 

if [ -z "$LENDBUSERID" ] 
then
	echo "$Name. LENDB user name does not setup properly in id file. \r\n" >> $ERRORFILE;
	sendError
	exit 1
fi

if [ -z "$LENDBPWD" ] 
then
	echo "$Name. LENDB user password does not setup properly in id file. \r\n" >> $ERRORFILE;
	sendError
	exit 1
fi

if [ -z "$LSRUSERID" ] 
then
	echo "$Name. LSR user id does not setup properly in id file. \r\n" >> $ERRORFILE;
	sendError
	exit 1
fi

if [ -z "$LSRPWD" ] 
then
	echo "$Name. LSR user password does not setup properly in id file. \r\n" >> $ERRORFILE;
	sendError
	exit 1
fi

if [ -z "$PEWUSERID" ] 
then
	echo "$Name. PEW user id does not setup properly in id file. \r\n" >> $ERRORFILE;
	sendError
	exit 1
fi

if [ -z "$PEWPWD" ] 
then
	echo "$Name. PEW user password does not setup properly in id file. \r\n" >> $ERRORFILE;
	sendError
	exit 1
fi
# 1 parameter is required.
if [ $# == 1 ]
then
	case "$1" in
		"all")
			loadAll
			;;
		"TIER_1_CRU")
			processData $1			
			;;
		"BTGMACH")
			processData $1			
			;;
		"BTGSVPAC")
			processData $1			
			;;
		"SDF")
			processData $1			
			;;
		"SDF_PIVOT")
			processData $1			
			;;
		"MTYPE_PART")
			processData $1			
			;;
		"PARTS")
			processData $1			
			;;
		"MTYPE_LOAD")
			processData $1			
			;;
		"WAC_FAMILY_CAP")
			processData $1			
			;;
		"RTO")
			processData $1	
			;;		
		*)
			if [[ $1 == ?(+|-)+([0-9]) ]]
			then
				DAYS_MON_END=$1
				lday=$(lastday $currentYear $currentMonth)
				# echo "the last day is: $lday"
				eval '(('dayleft=$lday-$currentDay'))'
				#echo "days left: ${dayleft}, today: ${DAYS_MON_END}"
				if [ ${dayleft} == ${DAYS_MON_END} ] 
				then
					loadAll
				else	
					# check if there is any failed process
					reprocess
				fi
			# C3783: added parameter Mon-Sun
			elif [[ $goodWeekDay -eq 1 && ${#weekday} == 3 ]]
			then
				thisWeek=`date +%a | tr [a-z] [A-Z]`
				
				if [[ $thisWeek == $weekday ]] 
				then					
					loadAll
				else
					# check if there is any failed process
					reprocess
				fi
			else
				echo "$Name. Invalid parameter. " >> $ERRORFILE;
				sendError
				exit 1
			fi
			;;
	esac
else
	echo "$Name. Missing parameter. Usage: importdata.sh <parameter>." >> $ERRORFILE;
	sendError
	exit 1
fi
if [ -e "$ERRORFILE" ] 
then
	sendError
fi
echo " $Name ended at: `date` \r\n" >> $LOGFILE

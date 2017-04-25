# Name: stop_cron_jobs.sh 
# ---------------------------------
# Description:  This script stops the all_cron_jobs processes on AIX 
# ---------------------------------
# Arguments:   none
# ---------------------------------
# Change History - # First Released 2007-08-08
# 12-07-2007 - JWF - renamed from stopLSRFeed.sh
#                  
#

echo Stoping all the LSRFeed schedules ...
crontab -r
if [ $? != 0 ]; then
  echo "Error: crontab not successful. all_cron_jobs is not stopped.";
  exit 1;
fi
echo "Success: all_cron_jobs is stopped.";
exit 0;

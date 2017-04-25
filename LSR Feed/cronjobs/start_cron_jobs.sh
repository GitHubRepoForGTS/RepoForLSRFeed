# Name:  start_cron_jobs.sh
# ---------------------------------
# Description:  This script starts the all cron jobs processes on AIX 
# ---------------------------------
# Arguments:   none
# ---------------------------------
# Change History - # First Released 2007-08-08
# 12-07-2007 - JWF - renamed from startLSRFeedCron.sh 
#                  - changed reference to LSRFeed.cron to all_cron_jobs.con
#

echo Starting the LSRFeed schedules ...
crontab $HOME/cronjobs/all_cron_jobs.cron
if [ $? != 0 ]; then
  echo "Error: crontab not successful. all_cron_jobs.con did not started.";
  exit 1;
fi
echo "Success: all_cron_jobs started.";
exit 0;

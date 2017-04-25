

# setup where to find the JAVA environment 
export JAVA_HOME=/usr/java14
export PATH=$JAVA_HOME/bin:$PATH

# setup the db2 environment

export NOTIFYDAYS=10

case "$USER" in
	"lsrdev" )
		if [ -f /db2len02/sqllib/db2profile ]; then
		  . /db2len02/sqllib/db2profile;
		fi
		#LSRFeed password
		export LSRUSERID="lsrdev"
		export LSRPWD="abc1wits"
		export LSRPWDCHANGEDATE="2007-12-17"
		export LSRPWDEXPIRYDAYS="90"
		
		
		export LENDBUSERID="jwfowler"
		export LENDBPWD="nov11nov"
		export LENDBPWDCHANGEDATE="2007-10-01"
		export LENDBPWDEXPIRYDAYS="90"
		
	;;

	"lsruat" )
		if [ -f /db2len03/sqllib/db2profile ]; then
		  . /db2len03/sqllib/db2profile;
		fi
		#LSRFeed password
		export LSRUSERID="lsruat"
		export LSRPWD="xxxxxxxx"
		export LSRPWDCHANGEDATE="yyyy-MM-dd"
		export LSRPWDEXPIRYDAYS="90"
		
		export LENDBUSERID="xxxxxxxx"
		export LENDBPWD="xxxxxxxx"
		export LENDBPWDCHANGEDATE="yyyy-MM-dd"
		export LENDBPWDEXPIRYDAYS="90"
	;;

	"lsrprod" )
		if [ -f /db2len01/sqllib/db2profile ]; then
		  . /db2len01/sqllib/db2profile;
		fi
		#LSRFeed password
		export LSRUSERID="lsrprod"
		export LSRPWD="xxxxxxxxx"
		export LENDBPWDCHANGEDATE="yyyy-MM-dd"
		export LENDBPWDEXPIRYDAYS="90"
		
		export LENDBUSERID="xxxxxxxx"
		export LENDBPWD="xxxxxxxx"
		export LENDBPWDCHANGEDATE="yyyy-MM-dd"
		export LENDBPWDEXPIRYDAYS="90"
	;;
	* )

		
		if [ -f /db2len02/sqllib/db2profile ]; then
			  . /db2len02/sqllib/db2profile;
		fi
		#LSRFeed password
		export LSRUSERID="lsrdev"
		export LSRPWD="abc1wits"
		export LSRPWDCHANGEDATE="2007-11-21"
		export LSRPWDEXPIRYDAYS="90"
		export LSRNOTIFYDAYS="10"

		export LENDBUSERID="jwfowler"
		export LENDBPWD="nov11nov"
		export LENDBPWDCHANGEDATE="2007-12-10"
		export LENDBPWDEXPIRYDAYS="90"
		export LENDBNOTIFYDAYS="10"
	;;

esac


if [ -s "$MAIL" ]           # This is at Shell startup.  In normal
then echo "$MAILMSG"        # operation, the Shell checks
fi                          # periodically.

PS1="`whoami`@`hostname`::"'$PWD'"> "

alias       ls='ls -F'
alias       la='ls -a'
alias       ll='ls -l'
alias   aixterm='aixterm -ls -sb -sl 1000 -title "`hostname` #1"&'
alias      cls='tput clear'
alias -x l='ls -l'
set -o vi

# Name:  save_mail_attachments.awk
#
# USAGE:  cat /var/spool/mail/<username> | awk -f save_mail_attachments.awk
#
# Description: 	This script saves to the TARGET DIRECTORY  uuencoded (also base64) zip or text files
#  				to the name in the mime-header listed as filename=""
#
#  				CONVERTS SPACES to UNERSCORE in the filename
#
# 				The following is what is expected in the mail file (without the leading hash symbol)
#
# 				Content-type: application/zip
# 				       name="ACCEPTED_CA_20080605.zip"
# 				Content-transfer-encoding: base64
# 				Content-Disposition: attachment; 
# 				filename="ACCEPTED_CA_20080605.zip"
#				or
# 				Content-type: application/octet-stream
# 				       name="xxxx"
# 				Content-transfer-encoding: base64
# 				Content-Disposition: attachment; 
# 				filename="xxx"
#
# Change History 
# 12/14/2008	Mark Ma  - Initial
# 02/04/2009	Mark Ma  - change all keyword to lowercase to fit both Notes 7 and 8
# 07/05/2010	Mark Ma  - C4168 added parse email for PN_EXCLUDE    
# -------------------------------------------------------------------------------
# 

BEGIN {FilePermissions = "644";

  sendFrom = "";
  subject = "";
  fileName = "";
  state = "IDLE";
  counter = 0 ;
  selected =0;
  contentEncoding = "";
  TARGET_DIR = datadir;
  subject_key1="Accepted/Unaccepted Files";
  subject_key2="Approval Summary and Rejection Detail Files";
  subject_key3="PN_EXCLUDE";
  print  > "result.tmp";
  
}
#
# f u n c t i o n s
#
function Reset() {
  sendFrom = "";
  subject = "";
  fileName = "";
  state = "IDLE";
  selected = 0;
  contentEncoding = "";
  
  
}
function PrintVars() {
  print "Send From = "sendFrom;
  print "Subject = " subject;
  print "Attached File Name = "fileName;
  print "Content Encoding = **"contentEncoding"**"; 
  print "Counter = " counter;
  
 
}
#
# Main
#
{
   if (state=="GET_ENCODED_FILE") {      
      MyData = "";      
      while (length($0) < 2) {
         # skip blank lines
         getline;
      }
      if (match(contentEncoding,"base64")>0) {  
      print TARGET_DIR"/"fileName;
         MyData = "begin-base64 "FilePermissions" "TARGET_DIR"/"fileName;
      } else if (length(ContEncoding)<0) {
         MyData = "begin "FilePermissions" "fileName;
      } # **** WHAT IF OTHER ENCODING ??????
      
      while (length($0) > 2) {
         # get the uuencoded attachment
         MyData = MyData"\n"$0;
         getline;
      }
      MyData = MyData"\n====\n";
      #print MyData;
      print MyData | "uudecode";
      close("uudecode");
      state="GET_CONTENT";
      next;
   } else
   if (state=="GET_CONTENT") {
      	#if (Counter++ > 8) {
      	#   Reset();   # bombed out!!
      	#   next;
      	#}
      	if ((ct=match($0,"^From "))>0 && NF == 7) {      	
			 if(selected !=1){
			 	Reset();
			 	state = "GET_CONTENT";
			 	sendFrom = $2;
			 	print "Send From(1): "sendFrom;        
			 	print ++counter;
			 }else{
			 	# process one mail at a time.
			 	exit;			 	
			 }
		 }
		if ((ct=match($0,"^Subject: "))>0) {
			subject = substr($0,9);			
			if ( (ct=index(subject, subject_key1)>0) || (ct=index(subject, subject_key2)>0) || (ct=index(subject, subject_key3)>0)){
				#print "good.subject is match...";
				#print subject; 
			 }else{
			 	#print "bad.subject is not match ...";
				Reset();
				next; 				     
			 }
		}
		
      	if ((ct=match(tolower($0),"content-type: application/zip"))>0) {
      		selected = 1;	
      		print "zip file found...";
      	}
      	if ((ct=match(tolower($0),"content-type: application/octet-stream"))>0) {
			selected = 1;	
			print "text file found...";
      	}
      	if ((ct=match(tolower($0),"content-disposition: attachment; filename="))>0 && selected ==1) {
       		gsub("\"","",$0);   # remove quotes around the name
       		gsub(" ","_",$0);   # replace spaces with underscore
       		fileName = substr($0,ct+42);  
       		print "Send_From: "sendFrom >> "result.tmp"; 
       		print "Attached_files: "fileName >> "result.tmp";  
       		print "Subject: "subject >> "result.tmp";  
      	}
      	if ((ct=match(tolower($0),"content-transfer-encoding: "))>0 && selected ==1){
			contentEncoding = substr($0,28);      
      		state = "GET_ENCODED_FILE";
      		PrintVars();
      	}
      
   	} else
   	if (state=="IDLE") {
      	if ((ct=match($0,"^From "))>0 && NF == 7) {      	
	 		state = "GET_CONTENT";
	 		sendFrom = $2;
	 		#print "Send From (" state "): "sendFrom;        
	 		#print ++counter;
	 		++counter;
     	}
   	}
}
END {
	if(selected==1){
		print "mail_number: " counter >> "result.tmp";
	}else{
		print "No mail to be processed.";
		system("rm result.tmp");
	}
	
}

#!/usr/bin/env Rscript

######## DO NOT MODIFY ###########################################

#Get the command line arguments of the input and output file
args <- commandArgs(TRUE)
input_file <- args[1]
output_file <- args[2]

#open the input_file
surveys <- read.csv(file = input_file, header=TRUE)

######### YOUR DOFILE/R Cleaning code goes here ##################
#Here are some example modifications - they are totally random and should be removed
#please TEST your Rscript! If you do something wrong it will
#cause havoc...correctable havoc, but still not fun

#example removal of a duplicate survey


#example transformation/correction on a single cell by surveyUUID
#Note that the comma at the end of the comparison is important to index correctly
surveys[surveys$instanceID == "uuid:81a96974-395e-4bdd-9778-64c357df4ff1",]$durationConsentV = 150

#example transformation on all cells in a column
#remove all leading zeros
for (row in 1:nrow(surveys)) {
    if(!is.na(surveys[row,"e_phonenumber"]) && substring(surveys[row,"e_phonenumber"], 1, 1) == "0") {
        surveys[row,"e_phonenumber"] = substring(surveys[row,"e_phonenumber"], 2)
        write(substring(surveys[row,"e_phonenumber"], 2),stdout())
    }
}



###################################################################

######## DO NOT MODIFY ###########################################

#write the results survey as a CSV to the output file
write.csv(surveys,file = output_file,na="")

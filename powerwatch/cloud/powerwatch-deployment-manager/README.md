Powerwatch Deployment Manager
============================

The powerwatch deployment manager automatically consumes surveys from surveyCTO,
populates a postgres deployment table along with putting incentivized users into
the OINK database.

The process is:

1) Read surveys from surveyCTO 
 - There are two surveys, an entry survey and an exit/switch device survey
2) Run Rscript on the survey to clean the files. The Rscripts contain all of the
necessary cleaning information/changes from the survey, both static and dynamic
3) After cleaning it processes the surveys and generates two tables:
 - A powerwatch deployment table which is a list of all powerwatchIDs, the beginning of a deployment, and the end of that deployment
 - A respondent table that for each respondent lists if they currently have a powerwatch, when they got it, and which one

It generates these tables by going through all of the surveys in time order
repeatedly, deploying, and undeploying powerwatches as necessary until all surveys
are processed. If there is a survey that it can't process then it flags that survey.

It then writes the surveys to one table, and the R-scripted survey to another
table along with whether the survey was correctly processed or had an error somewhere.

Deployment managers can view surveys that are erroring and correct them in
the Rscript until they stop erroring or simply lookup which respondent has which
powerwatch at the moment.

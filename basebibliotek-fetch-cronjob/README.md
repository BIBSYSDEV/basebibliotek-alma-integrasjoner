# Basebibliotek fetch-cronjob


Runs nightly and fetches the past 7 days of changes.
This lambda contacts basebibliotek and retrieves the changes.
It creates a .txt files with bibNR and pushes those to S3.

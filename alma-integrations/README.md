# Alma integrations

This module contains two lambdas that are triggered by uploading a .txt file with bibNr to a S3 bucket.
The Resource Sharing Parthner (RSP) lambda updates Alma partner, and the Library User Management (LUM) lambda updates Alma Users.
The two lambdas acts as adaptors converting basebibliotek xml to Alma partner and Alma User.
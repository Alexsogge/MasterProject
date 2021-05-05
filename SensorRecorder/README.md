# Sensor Recorder
This WearOs application records the motion sensors to analyse an users hand movements.
All recorded values are stored in .csv files which are packed into zips. These files can be uploaded to a [Webserver](../Webserver).


# Configuration
When starting for the first time, the configuration menu opens. Here you have to set the Server name, an identification name to distinguish this device from others during the authentication process and which/how the recorded values should be stored.  
After all settings have been done, press the apply button. 

# HandWash detection
During the recording process, the application also observes gestures to determine hand washing moments. If this happens, a notification pops up, where the user is asked whether he has just washed his hands. If he presses yes some evaluation questions have to be answered. 
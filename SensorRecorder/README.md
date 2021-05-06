# Sensor Recorder
This WearOs application records the motion sensors to analyse an users hand movements.
All recorded values are stored in .csv files which are packed into zips. These files can be uploaded to a [Webserver](../Webserver).


# Configuration
When starting for the first time, the configuration menu opens. Here you have to set the Server name, an identification name to distinguish this device from others during the authentication process and which/how the recorded values should be stored.  
After all settings have been done, press the apply button.  
At this point a authentication request with the given identification is send to the web server. Before the user can upload files, these request has to be granted. 

# HandWash detection
During the recording process, the application also observes gestures to determine hand washing moments. If this happens, a notification pops up, where the user is asked whether he has just washed his hands. If he presses yes some evaluation questions have to be answered.

# Architecture
In this chapter I would like to explain how the app works in order to give a rough overview.

## LifeCycle
### Startup
Everything starts in [MainActivity](./src/main/java/unifr/sensorrecorder/MainActivity.java). At first there happens some initialization stuff. During the call of `loadConfigs()`, it's checked if there already the initial configuration has been. If this isn't the case we open the [ConfActivity](./src/main/java/unifr/sensorrecorder/ConfActivity.java) and set a flag, to signalise, that we're waiting for these configs.  
If the configurations already are set, we continue with the initialisation of our services by the call of `initServices()`. Otherwise these step is done after we return from the ConfActivity. At this part we now check if the application has the rights to access the external storage. If this isn't the case we have to prompt the request for this permission first, otherwise we can continue with the start of our services.  
Here we start the [SensorRecordingManager](./src/main/java/unifr/sensorrecorder/SensorRecordingManager.java). This is the main service of our application. Here happens all the magic. Due to we need to call some functions from outside, we use a binder to access the instance of this service. Just some boiler code which calls `onServiceConnected` in `ServiceConnection`, when the service is created.  

In `onStartCommand()` we initialize all required data and start the recording (ToDo: This is not mentioned, but the application crashes if the sensorManager is initialized but gets no sensor events -> fix this).  

### Recording
The recording process is started by the call of `startRecording` in the `SensorRecordingManager`.  
To start the recording we load the current configuration, clear all data container and activated the used ones (Default and all available sensors). After that we init all other recording modules and do some other setup stuff. At the end we register all used sensors to the Android sensorManager. Now everything happens by SensorEvents in the registered SensorListenerServices.  

#### SensorListenerService
Each sensor runs in its own Thread and [SensorListenerService](./src/main/java/unifr/sensorrecorder/SensorListenerService.java). In the given rate, the system sends SensorEvents to the SensorEvenListeners, which are handled in `onSensorChanged()`. Basically we simply save the given value and timestamp in a Buffer at each call. Additionally we estimate in a very rough way if there has been a certain impact at one of the axis. If yes we start the microphone recording within the SensorRecordingManager.  
If the buffer runs full, we have to flush it. Since we shouldn't run time consuming tasks in the sensor events, we run a new Async Task which does the job. To do this we init the task with a copy of the buffer and execute the Runnable. During this process we write the values onto the disk, as configured in the settings. After that we call the `flushBuffer()` function in our SensorRecordingManager. This simply forwards the buffered values to the [HandWashDetection](./src/main/java/unifr/sensorrecorder/HandWashDetection.java), which does its prediction stuff.  

### Stop recording
To stop the recording we call `stopRecording()` in SensorRecordingManager. At first we do a last flush of the sensors and unregister them from the android sensor manager. Since our sensor services run in separate Threads we have to wait until there finished. Since we're not allowed to block the main thread, we outsource this to a new Async Task as like before. After all sensors are closed its time to clean up. We close all streams who write to the disk, and other recordings and save all generated files in a new directory to pack them together.  
Now we're in idle state where we could start a new recording or upload some data.

## Upload data


## DataProcessor
The (DataProcessor](./src/main/java/unifr/sensorrecorder/DataContainer/DataProcessor.java) handles everything that has to do with writing to the storage. Since there are many pointer where we write some data, there exists on instance in our Application, which is accessible via the static call `DataProcessorProvider.getProcessor()`. The initialization and managing stuff happens in SensorRecorderManager during `startRecording` and `stopRecording`. Thereby we can write new data at any time, for example by calling `writePrediction()`.

### DataContainer
This data structure provides some basic mechanisms which we use in our process. There are different types of container we use depending on the way we want to write data. The class [DataContainer](./src/main/java/unifr/sensorrecorder/DataContainer/DataContainer.java) is the base class which simply holds a File instance and manages it. We use this if we do the writing in a special way, for example the microphone recording.  
[OutputStreamContainer](./src/main/java/unifr/sensorrecorder/DataContainer/OutputStreamContainer) expands the basic container by the ability to write strings to the contained file. In most cases we use this container. Since we have to open and close the output stream we have to add each of these containers to the `streamContainers` in our DataProcessor. Furthermore we could use a [ZipContainer](./src/main/java/unifr/sensorrecorder/DataContainer/ZipContainer.java) if we want to automatically write into zip files. These containers are used by the sensors, since there are a lot of values which we want to store.

   


  


 
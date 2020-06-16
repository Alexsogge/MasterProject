# Architecture
This is a concept document of the architecture


# Components
Description and function of the individual components, that have to be implemented.
## WearOS app
This is one of the key components. It continuously records sensor data and sends them to the learner. 

If hand wash event is triggered -> last x sensor data has to be send. 

Requests newest trained model. 

Furthermore it communicates with the soap dispenser for verifying ground truth data


Considerations:
  - Just send data for hand wash events or noise too?
  - Mark start and end of hand wash? Not that good.
    - How to recognize start and end in last x data points.
    - If possible determine on server for higher energy efficiency
  


## Learner
Receives sensor data, applies them to the model, relearn, publish new model.

Considerations:
  - User specific model or global?
    - If user based -> Accounts, Database for individual models

## Model
We use Tensorflow [1] to compute the model. After that we can convert it to a tensorflow lite model .tflite to use it in android [2]. 

This model has to be send to the WearOS app, where it will be loaded.

In default setup, the .tflite file is stored in an assets folder of source. This path ist just accessible during build time and read-only afterwards. Therefore we have to store and read it from another location.   
Model can be saved in external storage [3]. To read the .tflite file from outside the assets folder, use [4].

# Communication
Specification of communication endpoints and messages between the applications

## SensorData
A package of sensor data contains last x sensor data.

### Message
List of measure points in the following form:   
   `[[time, value[0], ..., value[n]], [time, value[0], ..., value[n]], ...]`

We need the time of each recording, due to the android sensor manager doesn't guarantee, that they are evenly spreaded.


### Transport
Use HTTP-POST requests. Place data list as JSON-string in body. 

Considerations:
  - use dictionary with time as key instead of list


## Learner
Provides HTTP-Server.
  - Receives HTTP-POST from WearOS and extracts sensor data from JSON body.
  - Reveives HTTP-GET from WearOS to pull model, responses with new .tflite file.


## Soap dispenser
Probably Bluetooth. Needs to connect -> discover for devices. Configure WearOS App as passive device which just receives pairing requests to save energy.

Trigger hand wash events

### Message
Has to contain event type, time

## Overview
![connection overview](images/ArchitectureGraph.png "Architecture graph")

---------------------
Ref:  
\[1\]: https://www.tensorflow.org/  
\[2\]: https://www.tensorflow.org/lite/guide/get_started#2_convert_the_model_format  
\[3\]: https://developer.android.com/training/data-storage  
\[4\]: https://github.com/amitshekhariitbhu/Android-TensorFlow-Lite-Example/issues/15  
[1]: https://www.tensorflow.org/  
[2]: https://www.tensorflow.org/lite/guide/get_started#2_convert_the_model_format  
[3]: https://developer.android.com/training/data-storage  
[4]: https://github.com/amitshekhariitbhu/Android-TensorFlow-Lite-Example/issues/15
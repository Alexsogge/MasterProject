# Data recording infrastructure of obsessive hand washing

Obsessive hand washing can be harmful to the skin. Therefore we need a way to detect this process. Since Smart watches are available to a wide range of users we can use their internal sensors to observe their movements. With help of machine learning its possible to detect hand wash gestures out of this movements. This requires lots of data for training.

We want to use these smart watches to also record and deliver all movement data so that they can be used for further training. This should be happen on a daily base without much user interaction.



## Deliverables
- Wear OS application which is executed in background. This records all sensor data while the watch is disconnected from charging dock. Until the watch is charged, the recording stops and is uploaded to a webserver.
- Webserver which collects all incoming recording sessions and offers a overview and evaluation of recordings.
- Uses tflite or onnx runntime models to detect hand wash gestures and notifies the user.

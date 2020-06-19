# Sensor Recording
To get the motion data if an hand wash event is triggered, we have to continuously read the corresponding sensors and save the last n values.  
N have to be large enough to cover all significant sensor values which occur during a normal hand wash session.

Due to the fact, that we have to listen to the sensors on an wearable device the whole time, we have to implement this part in an most possible energy efficient way.  
We use several options that Android provides us.

# Sensor batching
Sensor Batching [1] enables to save multiple sensor values in a hardware buffer. They are stored in a FIFO with specific length, without waking up the main __application processor (AP)__. This leads to significant power savings.

We can activate this behavior by specify a __maximum report latency__ while registering at the SensorManager. This sets the maximum time, until events have to be reported.

In an optimal scenario we would set it to the maximal value, that allows to fill up the FIFO completely. Thereby the AP would be active as less as possible without loosing sensor values.  
But an optimal world would be boring, right?

## Problems
#### Other apps are evil.  
If a application register for the same sensor it has to specify a sampling period. This sets the frequency how often a new sensor event is generated.  
For example in our case 50HZ. With a FIFO size of 1000 and 3 sensor values per event, we can set the report latency to ~6.6 seconds. 

If another app registers to the same sensor, the lower period, namely the higher frequency, would be applied for both applications. Therefore it could be, that there are much more values reported to the FIFO than expected and this leads to value loss.

A similar behavior also exists with the report latency. If the maximal report latency of one application acquires a FIFO flush, all stored sensor values are reported to all registered applications. This leads in general to less AP  wake ups and therefore less energy consumption. Not as perfect like in our single app case, but that means no information loss. So it's OK.

#### Doze and Suspend
Yeah dozing [2][3] is one of our friends in terms of power saving, but can be very ugly for our information consistency. In general it's restricts CPU operation which causes, that our app can't do anything.  
No operations -> less power consumption. But what does this mean for our batching?

Fortunately the sensor batching does not hold a wake-lock which allows the AP to enter the suspend mode, which get us to our lovely power savings. Furthermore the FIFO is still capable to store new sensor events (I mean I think so).
To our bad luck, the acceleration sensor which we use is a __non-wake-up__ sensor. This means it can't enforce the AP to wake up if the report delay is exceeded. Therefore the FIFO will start to override old values if its get full. Again information loss.

We need a mechanism to wake up the AP when our FIFO gets full.

# Wakelocks
Wakelocks[4][5] are the counter part of energy efficient suspend. They keep the device awake to complete important work.  
Conversely, that means we prevent the device to save power. Therefore we should use wake locks with care and just if there absolutely necessary. 

As mentioned as before the sensor batch i.e. the __Sensors HAL__ [6] doesn't hold a wake lock. So we to do it by our own. 
In our case we need wake locks just to flush our FIFO. But at first we should consider when we have to trigger the AP to wake up.

There are two possible options where we can place such a wake lock:

#### Wake up during hand wash event
We could try to use other mechanisms like wake-up sensors [7] to detect a possible hand wash event and enforce the FIFO to flush. This requires, that the FIFO is large enough to store all previous relevant values from beginning of the event to its discovery.  

#### Regular waking up of the AP
As shown in section "Sensor batching" we can estimate the time when the FIFO could be full.
We can use the AlarmManager[8] to trigger specific events at an certain time. But the minimum time gap between two alarms which allows the AP to go sleep is longer than our max report delay.  
The AlarmManager also provides Scheduled repeating alarms[9] with a flexible minimum time gap. We need to set up some things to enable them to fire in Doze mode.

In the same section of the documentation where this is mentioned, they suggest the new WorkManager API which seems to do exactly what we want. So lets have a look.


# Schedule tasks with WorkManager
The WorkManager [10] enables to schedule tasks that are executed in background.



---------------------------------------------
Ref:  
[1]: https://source.android.com/devices/sensors/batching  
[2]: https://developer.android.com/training/monitoring-device-state/doze-standby.html  
[3]: https://source.android.com/devices/sensors/suspend-mode  
[4]: https://developer.android.com/training/scheduling/wakelock  
[5]: https://developer.android.com/reference/android/os/PowerManager#PARTIAL_WAKE_LOCK  
[6]: https://source.android.com/devices/sensors/sensors-hal2  
[7]: https://source.android.com/devices/sensors/suspend-mode#wake-up_sensors  
[8]: https://developer.android.com/reference/android/app/AlarmManager  
[9]: https://developer.android.com/training/scheduling/alarms  
[10]: https://developer.android.com/topic/libraries/architecture/workmanager




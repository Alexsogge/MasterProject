#include <stdio.h>
#include <time.h>
#include <unistd.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>

//android header
#include <android/sensor.h>
#include <android/looper.h>

// project header
#include "DataContainer.h"


void die(char *err){
	printf("%s\n", err);
	exit(1);
}

int main(int argv, char *argc[]){
  ASensorManager *sensor_manager = ASensorManager_getInstance();
  ASensorList sensor_list = NULL;
	DataContainer *dataContainer = new DataContainer("/data/local/tmp/");
	
  if( sensor_manager == NULL )
    die("error, sensor_manager");
  
  int sensor_count = ASensorManager_getSensorList(sensor_manager, &sensor_list);
  for(int count = 0 ; count < sensor_count ; count++){
    printf("*********************************\n");
    printf("sensor_name :%s\n", ASensor_getName(sensor_list[count]));
    printf("sensor_vendor :%s\n", ASensor_getVendor(sensor_list[count]));
    int sensor_type = ASensor_getType(sensor_list[count]);
    switch (sensor_type){
      case ASENSOR_TYPE_ACCELEROMETER:
      printf("ASENSOR_TYPE_ACCELEROMETER\n");
      break;
      case ASENSOR_TYPE_MAGNETIC_FIELD:
      printf("ASENSOR_TYPE_MAGNETIC_FIELD\n");
      break;
      case ASENSOR_TYPE_GYROSCOPE:
      printf("ASENSOR_TYPE_GYROSCOPE\n");
      break;
      case ASENSOR_TYPE_LIGHT:
      printf("ASENSOR_TYPE_LIGHT\n");
      break;
      case ASENSOR_TYPE_PROXIMITY:
      printf("ASENSOR_TYPE_PROXIMITY\n");
      break;
      default:
      printf("ASENSOR_TYPE_UNKNOWN\n");
    }
  }
  
  int looperID = 1;
  //sensor queue
	ASensorEventQueue *sensor_queue = ASensorManager_createEventQueue(sensor_manager, ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS), looperID, NULL, NULL );

	if( !sensor_queue )
		die("sensor_queue");

	//sensor
	ASensor const *sensor_acc = ASensorManager_getDefaultSensor(sensor_manager, ASENSOR_TYPE_ACCELEROMETER);
	if( sensor_acc == NULL )
		die("no sensor found: sensor_acc");
		
	ASensor const *sensor_gyro = ASensorManager_getDefaultSensor(sensor_manager, ASENSOR_TYPE_GYROSCOPE);
	if( sensor_gyro == NULL )
		die("no sensor found: sensor_gyro");
		
	// ASensor const *sensor_magneto = ASensorManager_getDefaultSensor(sensor_manager, ASENSOR_TYPE_MAGNETIC_FIELD);
	// if( sensor_magneto == NULL )
	// 	die("no sensor found: sensor_magneto");

	//enable
	if( ASensorEventQueue_enableSensor(sensor_queue, sensor_acc) < 0)
  //if(ASensorEventQueue_registerSensor(sensor_queue, sensor_acc, 20000, 600000))
		die("sensor acc enable");
	if( ASensorEventQueue_enableSensor(sensor_queue, sensor_gyro) < 0)
		die("sensor gyro enable");
	// if( ASensorEventQueue_enableSensor(sensor_queue, sensor_magneto) < 0)
	// 	die("sensor magneto enable");
  printf("Min delay acc: %d\n", ASensor_getMinDelay(sensor_acc));
	// printf("Min delay gyro: %d\n", ASensor_getMinDelay(sensor_gyro));
	ASensorEventQueue_setEventRate(sensor_queue, sensor_acc, 20000);
	ASensorEventQueue_setEventRate(sensor_queue, sensor_gyro, 20000);
	// ASensorEventQueue_setEventRate(sensor_queue, sensor_magneto, 20000);

	printf("%s enabled\n", ASensor_getName(sensor_acc));
	printf("%s enabled\n", ASensor_getName(sensor_gyro));
	// printf("%s enabled\n", ASensor_getName(sensor_magneto));

  
  for (;;) {
 		int ther_is_event = ASensorEventQueue_hasEvents(sensor_queue);
		if( ther_is_event == 0 )
			continue;
		ASensorEvent sEvent;
		// memset(sEvent, 0, sizeof(sEvent));


		int ident = ALooper_pollAll( 0, NULL, NULL, NULL );
		if (ident != looperID) {
			goto sec;
		}
		while(ASensorEventQueue_getEvents(sensor_queue, &sEvent, 1) > 0){
			if (sEvent.type == ASENSOR_TYPE_ACCELEROMETER) {
				dataContainer->AddNewAcceleration(sEvent.timestamp, sEvent.acceleration.x, sEvent.acceleration.y, sEvent.acceleration.z);
			}
			if (sEvent.type == ASENSOR_TYPE_GYROSCOPE){
				dataContainer->AddNewGyroscope(sEvent.timestamp, sEvent.data[0], sEvent.data[1], sEvent.data[2]);
			}
			if (sEvent.type == ASENSOR_TYPE_MAGNETIC_FIELD){
				dataContainer->AddNewMagnetic(sEvent.timestamp, sEvent.magnetic.x, sEvent.magnetic.y, sEvent.magnetic.z);
			}
			// printf("%f %f %f %f %f %f\n", sEvent.acceleration.x, sEvent.acceleration.y, sEvent.acceleration.z, sEvent.acceleration.pitch, sEvent.acceleration.roll, sEvent.acceleration.azimuth);
			// dataContainer->AddNewReading(sEvent.acceleration.x, sEvent.acceleration.y, sEvent.acceleration.z, sEvent.acceleration.pitch, sEvent.acceleration.roll, sEvent.acceleration.azimuth);
		}
		
    
		sec:
		sleep( 1 );
	}

  
  //disable
  ASensorEventQueue_disableSensor(sensor_queue, sensor_acc );
	printf("%s disabled\n", ASensor_getName(sensor_acc));
	ASensorEventQueue_disableSensor(sensor_queue, sensor_gyro );
	printf("%s disabled\n", ASensor_getName(sensor_gyro));

	//destroy
	ASensorManager_destroyEventQueue(sensor_manager, sensor_queue);
  
  return 0;
}

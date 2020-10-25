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


void die(char *err){
	printf("%s\n", err);
	exit(1);
}

int main(int argv, char *argc[]){
  FILE *fp;
  time_t rawtime;
  struct tm * timeinfo;
  ASensorManager *sensor_manager = ASensorManager_getInstance();
  ASensorList sensor_list = NULL;
  int sensor_count = 0;
  int sensor_type  = 0;
  int count ;
  
  
  if( sensor_manager == NULL )
    die("error, sensor_manager");
  
  sensor_count = ASensorManager_getSensorList(sensor_manager, &sensor_list);
  for( count = 0 ; count < sensor_count ; count++){
    printf("*********************************\n");
    printf("sensor_name :%s\n", ASensor_getName(sensor_list[count]));
    printf("sensor_vendor :%s\n", ASensor_getVendor(sensor_list[count]));
    sensor_type = ASensor_getType(sensor_list[count]);
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
	ASensor const *sensor_acc = ASensorManager_getDefaultSensor( sensor_manager, ASENSOR_TYPE_ACCELEROMETER );
	if( sensor_acc == NULL )
		die("sensor_acc");

	//enable
	if( ASensorEventQueue_enableSensor(sensor_queue, sensor_acc) < 0)
  //if(ASensorEventQueue_registerSensor(sensor_queue, sensor_acc, 20000, 600000))
		die("sensor enable");
  printf("Min delay: %d\n", ASensor_getMinDelay(sensor_acc));
  ASensorEventQueue_setEventRate(sensor_queue, sensor_acc, 20000);

	printf("%s enabled\n", ASensor_getName(sensor_acc));

	//read data
	const int kNumEvents = 20;
	const int kTimeoutMilliSecs = 10000;
  
  int i ;
  
  fp = fopen("/data/local/tmp/test.txt", "w+");
  fprintf(fp, "Test fprintf...\n");
  fputs("Test fputs..\n", fp);
  fflush(fp);
  
  char file_write_buffer[256*100];
  int written_lines = 0;
  int numAvaiableEvents = 0;
  float val_x=0, val_y=0, val_z=0;
  int collected_data = 0;
  int max_collected_data = 100;
  
  for (int j=0; j < 60*12000; j++) {
 		i = ASensorEventQueue_hasEvents(sensor_queue);
		if( i == 0 )
			continue;
		ASensorEvent data[kNumEvents];
		memset(data, 0, sizeof(data));


		int ident = ALooper_pollAll( kTimeoutMilliSecs, NULL, NULL, NULL );
		if (ident != looperID) {
			goto sec;
		}
    numAvaiableEvents = ASensorEventQueue_getEvents(sensor_queue, data, kNumEvents);
		if (numAvaiableEvents <= 0) {
			goto sec;
		}

		// displaySensorData(ASENSOR_TYPE_ACCELEROMETER, data );

    /*
    for (int x = 0; x < numAvaiableEvents; x++){
      printf("[%d] : X=%f Y=%f Z=%f\n", x, data[x].acceleration.x, data[x].acceleration.y, data[x].acceleration.z);
    }*/
    val_x += data[0].acceleration.x;
    val_y += data[0].acceleration.y;
    val_z += data[0].acceleration.z;
    collected_data++;
    if (collected_data > max_collected_data){
      val_x /= max_collected_data;
      val_y /= max_collected_data;
      val_z /= max_collected_data;
      
      time(&rawtime);
      timeinfo = localtime(&rawtime);
      
      char str[128];   
      sprintf(str, "%d:%d:%d", timeinfo->tm_hour, timeinfo->tm_min, timeinfo->tm_sec);
      char line[256];
      
      sprintf(line, "[%s] : X=%f Y=%f Z=%f\n", str, val_x, val_y, val_z );
      strcat(file_write_buffer, line);
      written_lines++;
      if (written_lines>30){
        fputs(file_write_buffer, fp);
        fflush(fp);
        strcpy(file_write_buffer, "###############\n");
        written_lines = 0;
      }
      collected_data = 0;
      val_x = 0;
      val_y = 0;
      val_z = 0;
    }
    
    
		sec:
		sleep( 0.01 );
	}
  fputs("Ended read...\n", fp);
  fflush(fp);
  
  //disable
  ASensorEventQueue_disableSensor(sensor_queue, sensor_acc );
	printf("%s disabled\n", ASensor_getName(sensor_acc));

	//destroy
	ASensorManager_destroyEventQueue(sensor_manager, sensor_queue);
  
  /*
  fp = fopen("/data/local/tmp/test.txt", "w+");
  fprintf(fp, "Test fprintf...\n");
  fputs("Test fputs..\n", fp);
  */
  /*
  i = 0;
  while (i++ < 5){
    time(&rawtime);
    timeinfo = localtime(&rawtime);
    
    char str[128];   
    sprintf(str, "[%d:%d:%d]\n", timeinfo->tm_hour, timeinfo->tm_min, timeinfo->tm_sec);
    fputs(str, fp);
    fflush(fp);
    sleep(60);
  }*/
  fclose(fp);
  return 0;
}

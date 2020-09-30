#include <stddef.h>
#include <stdio.h>
#include <time.h>
#include <unistd.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include "SensorData.h"

#ifndef DATACONTAINER_H
#define DATACONTAINER_H

#define STD_BUFFER_SIZE 1000

class DataContainer
{
private:
  size_t m_buffer_size = STD_BUFFER_SIZE;
  size_t m_buffer_pointer = 0;
  size_t m_acc_pointer = 0;
  size_t m_gyro_pointer = 0;
  size_t m_magnetic_pointer = 0;
  FILE *m_file;
public:
  SensorData m_accelerationBuffer[STD_BUFFER_SIZE];
  DataContainer(FILE *file);
  
  void AddNewReading(unsigned long long time_stamp);
  void AddNewAcceleration(unsigned long long time_stamp,float x, float y, float z);
  void AddNewGyroscope(unsigned long long time_stamp,float pitch, float roll, float azimuth);
  void AddNewMagnetic(float x, float y, float z);
  void FlushBuffer();
};

#endif
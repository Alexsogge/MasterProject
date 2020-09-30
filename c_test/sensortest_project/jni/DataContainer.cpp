#include "DataContainer.h"
#include <chrono>

using namespace std::chrono;
DataContainer::DataContainer(FILE *file){
  m_file = file;
  /*
  for(size_t i = 0; i < m_buffer_size; i++){
    m_accelerationBuffer[i] = new SensorData();
  }
  */
}

void DataContainer::AddNewReading(unsigned long long time_stamp){
  //clock_t currentCycles = clock();
  //float currentTime = (currentCycles / (double)CLOCKS_PER_SEC) * 1000;
  //unsigned long long ms = duration_cast< milliseconds >(system_clock::now().time_since_epoch()).count();
  // auto value = std::chrono::duration_cast<std::chrono::milliseconds>(ms);
  // long millis = value.count();
  //unsigned long long millis = ms;
  // printf("[%llu]\n", time_stamp);
  
  // printf("[%f] %f %f %f %f %f %f\n", currentTime, x, y, z, pitch, roll, azimuth);
  m_accelerationBuffer[m_buffer_pointer].time_stamp = time_stamp;
  m_accelerationBuffer[m_buffer_pointer].value_x = 0;
  m_accelerationBuffer[m_buffer_pointer].value_y = 0;
  m_accelerationBuffer[m_buffer_pointer].value_z = 0;
  m_accelerationBuffer[m_buffer_pointer].value_pitch = 0;
  m_accelerationBuffer[m_buffer_pointer].value_roll = 0;
  m_accelerationBuffer[m_buffer_pointer].value_azimuth = 0;
  // m_accelerationBuffer[m_buffer_pointer].value_magnetic_x = 0;
  // m_accelerationBuffer[m_buffer_pointer].value_magnetic_y = 0;
  // m_accelerationBuffer[m_buffer_pointer].value_magnetic_z = 0;
  m_buffer_pointer++;
  if(m_buffer_pointer == m_buffer_size - 100)
    FlushBuffer();
}

void DataContainer::AddNewAcceleration(unsigned long long time_stamp, float x, float y, float z){
  if(m_acc_pointer >= m_buffer_pointer)
    AddNewReading(time_stamp);
  m_accelerationBuffer[m_acc_pointer].value_x = x;
  m_accelerationBuffer[m_acc_pointer].value_y = y;
  m_accelerationBuffer[m_acc_pointer].value_z = z;
  // printf("acc: %f %f %f\n", x, y, z);
  m_acc_pointer++;
}

void DataContainer::AddNewGyroscope(unsigned long long time_stamp, float pitch, float roll, float azimuth){
  if(m_gyro_pointer >= m_buffer_pointer)
    AddNewReading(time_stamp);
  m_accelerationBuffer[m_gyro_pointer].value_pitch = pitch;
  m_accelerationBuffer[m_gyro_pointer].value_roll = roll;
  m_accelerationBuffer[m_gyro_pointer].value_azimuth = azimuth;
  // printf("gyro: %f %f %f\n", pitch, roll, azimuth);
  m_gyro_pointer++;
}

void DataContainer::AddNewMagnetic(float x, float y, float z){
  /*
  if(m_magnetic_pointer >= m_buffer_pointer)
    AddNewReading();
  m_accelerationBuffer[m_magnetic_pointer].value_magnetic_x = x;
  m_accelerationBuffer[m_magnetic_pointer].value_magnetic_y = y;
  m_accelerationBuffer[m_magnetic_pointer].value_magnetic_z = z;
  m_magnetic_pointer++;
  */
}

void DataContainer::FlushBuffer(){
  char line[128];
  // determine maximal entrys where acceleration and gyroscope are avaiable
  size_t min_pointer = m_acc_pointer;
  if(min_pointer > m_gyro_pointer)
    min_pointer = m_gyro_pointer;
  // if(min_pointer > m_magnetic_pointer)
  //   min_pointer = m_magnetic_pointer;
  // flush all possible entrys to file
  for (size_t i = 0; i < min_pointer; i++) {
    //sprintf(line, "%lu\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\n", m_accelerationBuffer[i].time_stamp, m_accelerationBuffer[i].value_x, m_accelerationBuffer[i].value_y, m_accelerationBuffer[i].value_z, m_accelerationBuffer[i].value_pitch, m_accelerationBuffer[i].value_roll, m_accelerationBuffer[i].value_azimuth, m_accelerationBuffer[i].value_magnetic_x, m_accelerationBuffer[i].value_magnetic_y, m_accelerationBuffer[i].value_magnetic_z);
    sprintf(line, "%llu\t%f\t%f\t%f\t%f\t%f\t%f\n", m_accelerationBuffer[i].time_stamp, m_accelerationBuffer[i].value_x, m_accelerationBuffer[i].value_y, m_accelerationBuffer[i].value_z, m_accelerationBuffer[i].value_pitch, m_accelerationBuffer[i].value_roll, m_accelerationBuffer[i].value_azimuth);
    fputs(line, m_file);
  }
  
  // it is possible that for one sensor there already have been more values
  // set unflushed values to beginning of buffer
  for (size_t i = min_pointer; i < m_buffer_pointer; i++){
    m_accelerationBuffer[i - min_pointer] = m_accelerationBuffer[i];
  }
  // reset pointer
  m_buffer_pointer -= min_pointer;
  m_acc_pointer -= min_pointer;
  m_gyro_pointer -= min_pointer;
  // m_magnetic_pointer -= m_magnetic_pointer;
  
  printf("Flush...\n");
  fflush(m_file);
  printf("Flushed\n");
}
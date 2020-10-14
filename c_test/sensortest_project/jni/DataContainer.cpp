#include "DataContainer.h"
#include <chrono>

using namespace std::chrono;
DataContainer::DataContainer(const std::string &file_path){
	m_file_acc = fopen((file_path + "sensor_data_acc.csv").c_str(), "w+");
  m_file_gyro = fopen((file_path + "sensor_data_gyro.csv").c_str(), "w+");
  m_file_magneto = fopen((file_path + "sensor_data_magneto.csv").c_str(), "w+");
}

DataContainer::~DataContainer(){
  fclose(m_file_acc);
  fclose(m_file_gyro);
  fclose(m_file_magneto);
}

void DataContainer::AddNewAcceleration(unsigned long long time_stamp, float x, float y, float z){
  m_accelerationBuffer[m_acc_pointer].time_stamp = time_stamp;
  m_accelerationBuffer[m_acc_pointer].value_x = x;
  m_accelerationBuffer[m_acc_pointer].value_y = y;
  m_accelerationBuffer[m_acc_pointer].value_z = z;
  // printf("acc: %f %f %f\n", x, y, z);
  m_acc_pointer++;
  if(m_acc_pointer >= m_buffer_size - 100)
    FlushBufferAcc();
}

void DataContainer::AddNewGyroscope(unsigned long long time_stamp, float x, float y, float z){
  m_gyroscopeBuffer[m_gyro_pointer].time_stamp = time_stamp;
  m_gyroscopeBuffer[m_gyro_pointer].value_x = x;
  m_gyroscopeBuffer[m_gyro_pointer].value_y = y;
  m_gyroscopeBuffer[m_gyro_pointer].value_z = z;
  // printf("acc: %f %f %f\n", x, y, z);
  m_gyro_pointer++;
  if(m_gyro_pointer >= m_buffer_size - 100)
    FlushBufferGyro();
}

void DataContainer::AddNewMagnetic(unsigned long long time_stamp, float x, float y, float z){
  m_magnetometerBuffer[m_magnetic_pointer].time_stamp = time_stamp;
  m_magnetometerBuffer[m_magnetic_pointer].value_x = x;
  m_magnetometerBuffer[m_magnetic_pointer].value_y = y;
  m_magnetometerBuffer[m_magnetic_pointer].value_z = z;
  // printf("acc: %f %f %f\n", x, y, z);
  m_magnetic_pointer++;
  if(m_magnetic_pointer >= m_buffer_size - 100)
    FlushBufferMagneto();
}

void DataContainer::FlushBuffer(){
  FlushBufferAcc();
  FlushBufferGyro();
  FlushBufferMagneto();
}

void DataContainer::FlushBufferAcc(){
  char line[128];
  for (size_t i = 0; i < m_acc_pointer; i++) {
    //sprintf(line, "%lu\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\n", m_accelerationBuffer[i].time_stamp, m_accelerationBuffer[i].value_x, m_accelerationBuffer[i].value_y, m_accelerationBuffer[i].value_z, m_accelerationBuffer[i].value_pitch, m_accelerationBuffer[i].value_roll, m_accelerationBuffer[i].value_azimuth, m_accelerationBuffer[i].value_magnetic_x, m_accelerationBuffer[i].value_magnetic_y, m_accelerationBuffer[i].value_magnetic_z);
    sprintf(line, "%llu\t%f\t%f\t%f\n", m_accelerationBuffer[i].time_stamp, m_accelerationBuffer[i].value_x, m_accelerationBuffer[i].value_y, m_accelerationBuffer[i].value_z);
    fputs(line, m_file_acc);
  }
  
  // reset pointer
  m_acc_pointer = 0;
  
  // write file
  printf("Flush acc...\n");
  fflush(m_file_acc);
  printf("Flushed acc\n");
}

void DataContainer::FlushBufferGyro(){
  char line[128];
  for (size_t i = 0; i < m_gyro_pointer; i++) {
    //sprintf(line, "%lu\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\n", m_accelerationBuffer[i].time_stamp, m_accelerationBuffer[i].value_x, m_accelerationBuffer[i].value_y, m_accelerationBuffer[i].value_z, m_accelerationBuffer[i].value_pitch, m_accelerationBuffer[i].value_roll, m_accelerationBuffer[i].value_azimuth, m_accelerationBuffer[i].value_magnetic_x, m_accelerationBuffer[i].value_magnetic_y, m_accelerationBuffer[i].value_magnetic_z);
    sprintf(line, "%llu\t%f\t%f\t%f\n", m_gyroscopeBuffer[i].time_stamp, m_gyroscopeBuffer[i].value_x, m_gyroscopeBuffer[i].value_y, m_gyroscopeBuffer[i].value_z);
    fputs(line, m_file_gyro);
  }
  
  // reset pointer
  m_gyro_pointer = 0;
  
  // write file
  printf("Flush gyro...\n");
  fflush(m_file_gyro);
  printf("Flushed gyro\n");
}

void DataContainer::FlushBufferMagneto(){
  char line[128];
  for (size_t i = 0; i < m_magnetic_pointer; i++) {
    //sprintf(line, "%lu\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\t%f\n", m_accelerationBuffer[i].time_stamp, m_accelerationBuffer[i].value_x, m_accelerationBuffer[i].value_y, m_accelerationBuffer[i].value_z, m_accelerationBuffer[i].value_pitch, m_accelerationBuffer[i].value_roll, m_accelerationBuffer[i].value_azimuth, m_accelerationBuffer[i].value_magnetic_x, m_accelerationBuffer[i].value_magnetic_y, m_accelerationBuffer[i].value_magnetic_z);
    sprintf(line, "%llu\t%f\t%f\t%f\n", m_magnetometerBuffer[i].time_stamp, m_magnetometerBuffer[i].value_x, m_magnetometerBuffer[i].value_y, m_magnetometerBuffer[i].value_z);
    fputs(line, m_file_magneto);
  }
  
  // reset pointer
  m_magnetic_pointer = 0;
  
  // write file
  printf("Flush magneto...\n");
  fflush(m_file_magneto);
  printf("Flushed magneto\n");
}
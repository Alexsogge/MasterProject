#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <linux/i2c-dev.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>


int main(){
  int file;
  int adapter_nr = 5; /* probably dynamically determined */
  char filename[20];
  
  snprintf(filename, 19, "/dev/i2c-%d", adapter_nr);
  file = open(filename, O_RDWR);
  if (file < 0) {
    /* ERROR HANDLING; you can check errno to see what went wrong */
    puts("No File");
    printf("%d", errno);
    exit(1);
  }
  int addr = 0x38; /* The I2C address */
  if (ioctl(file, I2C_SLAVE_FORCE, addr) < 0) {
    /* ERROR HANDLING; you can check errno to see what went wrong */
    puts("Wrong address");
    printf("%d\n", errno);
    exit(1);
  }
  char buf[10] = {0};
  float data;
  char channel;


  for (int i = 0; i<4; i++) {
    // Using I2C Read
    if (read(file,buf,2) != 2) {
        /* ERROR HANDLING: i2c transaction failed */
        printf("Failed to read from the i2c bus.\n");
        printf("%d", errno);

        printf("\n\n");
    } else {
        data = (float)((buf[0] & 0b00001111)<<8)+buf[1];
        data = data/4096*5;
        channel = ((buf[0] & 0b00110000)>>4);
        printf("Channel %02d Data:  %04f\n",channel,data);
    }
  }
  
  return 1;
}



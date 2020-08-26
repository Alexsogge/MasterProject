#include <stdio.h>
#include <time.h>
#include <unistd.h>

main() {
    FILE *fp;
    time_t rawtime;
    struct tm * timeinfo;

    fp = fopen("/data/local/tmp/test.txt", "w+");
    fprintf(fp, "Test fprintf...\n");
    fputs("Test fputs..\n", fp);

    int i = 0;
    while (i++ < 200){
	time(&rawtime);
	timeinfo = localtime(&rawtime);

	char str[128];   
       	sprintf(str, "[%d:%d:%d]\n", timeinfo->tm_hour, timeinfo->tm_min, timeinfo->tm_sec);
	fputs(str, fp);
        fflush(fp);
	sleep(60);
    }
    fclose(fp);

}

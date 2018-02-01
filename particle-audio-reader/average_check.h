#include "math.h"
#include "application.h"

//stores the value to a running average array
void running_average(double *freq, double value, int index);

//return the average of the array freq_values
double find_average(double *freq_values, int length);

//get the values of specific freq
void get_frequencies_mag(int *frequencies, int length, int step_size, double *vReal,double *frequencies_values);

//checks to see if the new average is above\below a std_dev of the old average
double check_st_dev(double *freq_values, int length, double old_avg, String freq_name, String time);

//reports and publishes to cloud a general report of the desired frequency
void report(double avg, String freq_name, String time);

//reports and publishes to cloud any drop in magnitude of the specific freq 
void report_drop(double old_avg, double new_avg, String freq_name, String time);

//checks a single value drop below or above std_dev of old average -- very sensative
void check_value_drop(double average, double curr_value, double std, String name, String time);

/****************** HELPER FUNCTIONS ******************/

double sum(double x[], int arr_count);

double mean(double sum, int arr_count);

double sum_sqr(double x[], int arr_count);

double st_dev(double* freq_values, double mean, int arr_count);

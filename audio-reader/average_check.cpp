#include <average_check.h>


/* should we check magnitude in log ?? */ 


//insert current magnitude of freq into running average array
void running_average(double *freq, double value, int index) {
    freq[index] = value;
}

//calculates for the average of the running average array
double find_average(double *freq_values, int length) {
    double total;
    total = sum(freq_values, length);
    return total / length;
}

void check_value_drop(double average, double curr_value, double std, String name, String time){
    if (average - std > curr_value || average + std < curr_value){
        report_drop(average, curr_value, name, time);
    }
}

//get the values of specific freq
void get_frequencies_mag(int *frequencies, int length, int step_size, double *vReal, double *frequencies_values){
    int freq_index;
    
/* should mae sure the the freq_index is correct */
    for (int i=0; i<length; i++) {

        
        
        freq_index = (int) frequencies[i] / step_size;
        frequencies_values[i] = vReal[freq_index];
        
        // Serial.println("specific frequency index: ");
        // Serial.println(frequencies[i]);
        // Serial.println(freq_index);
    }
    
}

//reports if below st_dev, returns the new average
double check_st_dev(double *freq_values, int length, double old_avg, String freq_name, String time){

    
    double new_avg = find_average(freq_values, length);
    double std = st_dev(freq_values, old_avg, length);
    
    if (freq_name == "70Hz"){
        Serial.println("In check_st_dev *************");
        Serial.println("STD:");
        Serial.println(std/2);

        Serial.println("old avg:");
        Serial.println(old_avg);
        
        Serial.println("new avg:");
        Serial.println(new_avg);
    
        
    }
    
    if ((old_avg - (std/2)) > new_avg || (old_avg + (std/2)) < new_avg) {
        report_drop(old_avg,new_avg, freq_name, time);
    }
    
    return new_avg;
    
}

void report(double avg, String freq_name, String time) {
    String output = "";
    output = freq_name + " | Average: " + String(avg) + " Time: " + String(time);
    
    Serial.println("report");
    Serial.println(output);
    
    // publish to cloud
    // Particle.publish("Average: ",output,60,PRIVATE);
}

void report_drop(double old_avg, double new_avg, String freq_name, String time) {
    
    Serial.println("report Drop");
    
    String output = "";
    output = freq_name + " | Old Average: " + String(old_avg) + " New Average: " + String(new_avg) + " Time: " + String(time);
    // publish to cloud

    Serial.println(output);
    // Particle.publish("Drop in Magnitude: ",output,60,PRIVATE);
}

/****************** HELPER FUNCTIONS ******************/

double sum(double x[], int arr_count){
  int i = 0;
  double my_sum = 0;

  for (i = 0; i < arr_count; i++){
    my_sum += x[i];
  }
  return my_sum;
}

double mean(double sum, int arr_count) {
  return sum / arr_count;
}

double sum_sqr(double x[], int arr_count){
  int i = 0;
  double my_sum = 0;

  for (i = 0; i < arr_count; i++){
    my_sum += x[i] * x[i];
  }
  return my_sum;
}

double st_dev(double* freq_values, double mean, int arr_count) {
    double ret_value = 0.0;
    for (int i=0; i < arr_count; i++){
        ret_value += pow(freq_values[i]-mean, 2);
    }
    return sqrt(ret_value / arr_count);
}

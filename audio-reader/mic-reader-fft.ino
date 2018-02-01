// This #include statement was automatically added by the Particle IDE.
#include "average_check.h"

// This #include statement was automatically added by the Particle IDE.
#include "FFT.h"

//So that it works offline ( doesnt try to connect to cloud)
SYSTEM_MODE(MANUAL);

#include "math.h"


#define MICROPHONE_PIN A0
#define LENGTH 50
#define DELAY 0 //in millisec

// Custom constants for FFT
#define FFT_FORWARD 0x01
#define FFT_REVERSE 0x00

// Windowing type
#define FFT_LIB_REV 0x02c

/* Windowing type */
#define FFT_WIN_TYP_RECTANGLE 0x00 /* rectangle (Box car) */
#define FFT_WIN_TYP_HAMMING 0x01 /* hamming */
#define FFT_WIN_TYP_HANN 0x02 /* hann */
#define FFT_WIN_TYP_TRIANGLE 0x03 /* triangle (Bartlett) */
#define FFT_WIN_TYP_BLACKMAN 0x04 /* blackmann */
#define FFT_WIN_TYP_FLT_TOP 0x05 /* flat top */
#define FFT_WIN_TYP_WELCH 0x06 /* welch */

/*Mathematial constants*/
#define twoPi 6.28318531
#define fourPi 12.56637061

/*Sampling variables*/
#define SAMPLES 512 //has to be in multiples of 2 because of the exponent power for the fft
//attempt for step size of 5 interval = 390.625
const double samplingInterval = 390.625; // this is actually in microsec --> 1/samplingFrequency
double samplingFrequency = 1.0/(1e-6*samplingInterval);
int step_size = samplingFrequency / (SAMPLES-1);

/*optional threshold for finding peaks in a range*/
#define MIN_THRESHOLD 30
#define MAX_THRESHOLD 100


double vReal[SAMPLES];
double vImag[SAMPLES]; 
int audioStartIdx = 0, audioEndIdx = 0;

int delay_milli = 1000 / samplingInterval;

//string variables for output
char output_peak[50]; 

//to keep track of the 6 specific target frequencies
int running_avg_index = 0;
double frequencies_values[6];
int frequencies_names[] = {45,50,55,60,65,70};



double freq_45[LENGTH];
double freq_50[LENGTH];
double freq_55[LENGTH];
double freq_60[LENGTH];
double freq_65[LENGTH];
double freq_70[LENGTH];

double freq_45Avg = 0.0;
double freq_50Avg = 0.0;
double freq_55Avg = 0.0;
double freq_60Avg = 0.0;
double freq_65Avg = 0.0;
double freq_70Avg = 0.0;

// double freq_45Std = 0.0;
// double freq_50Std = 0.0;
// double freq_55Std = 0.0;
// double freq_60Std = 0.0;
// double freq_65Std = 0.0;
// double freq_70Std = 0.0;



// version without timers
unsigned long lastRead = micros();
unsigned int delayTime = 0;
unsigned long last_min_read = Time.minute();

// the setup routine runs once when you press reset:
void setup() {
  // initialize serial communication at 9600 bits per second:
  Serial.begin(9600);
  pinMode(MICROPHONE_PIN, INPUT);
  
  // 1/8000th of a second is 125 microseconds
  lastRead = micros();
  
  //
  last_min_read = Time.minute();
}

// the loop routine runs over and over again forever:
void loop() {
    
    //Imaginary part must be zeroed in case of looping to avoid wrong calculations and overflows, Real part just to make sure. 
    for(uint16_t i =0; i<SAMPLES; i++) {
        vReal[i] = 0.0;
        vImag[i] = 0.0; 
    }

    //collect input from mic 
    for(uint16_t i =0;i<SAMPLES;i++) {
        listenAndSend(delay_milli);
    }

    Serial.println("#####################Starting Point #####################");
    
    // Compute desired windowing
    Windowing(vReal,SAMPLES,FFT_WIN_TYP_RECTANGLE, FFT_FORWARD);
    Serial.println("windowing succeded ");
    
    // Compute FFT
    Compute(vReal, vImag, SAMPLES, FFT_FORWARD);
    Serial.println("compute succeded ");
    
    // Compute magnitudes, stores in vReal
    ComplexToMagnitude(vReal, vImag, SAMPLES);
    Serial.println("complex to mag succeded ");
    
    //retruns the peak freq in the sample
    double major_peak = MajorPeak(vReal, SAMPLES, samplingFrequency); 
    Serial.println("majorpeak succeded ");

    //returns 3 major peaks 
    String x = majorPeakFrequency(vReal, SAMPLES, samplingFrequency);
    Serial.println("Major Freq: ");
    Serial.println(x);
    
    //returns 3 major peaks in between a range 
    String local_freq = majorPeakFrequencyRange(vReal, SAMPLES, samplingFrequency, MIN_THRESHOLD, MAX_THRESHOLD);
    Serial.println("Major Freq between Ranges" + String(MIN_THRESHOLD) + " and " + String(MAX_THRESHOLD) + ": ");
    Serial.println(local_freq);
    
    
    Serial.println("##################### Checking Averages   #####################"); 
    

    get_frequencies_mag(frequencies_names, 6, step_size, vReal,frequencies_values);
    
    running_average(freq_45, frequencies_values[0], running_avg_index );
    running_average(freq_50, frequencies_values[1], running_avg_index );
    running_average(freq_55, frequencies_values[2], running_avg_index );
    running_average(freq_60, frequencies_values[3], running_avg_index );
    running_average(freq_65, frequencies_values[4], running_avg_index );
    running_average(freq_70, frequencies_values[5], running_avg_index );
    
    
    String time = Time.timeStr();
    
    //check the actual value rather than an average    
    check_value_drop(freq_45Avg, frequencies_values[0], st_dev(freq_45, freq_45Avg, LENGTH), "45Hz", time);
    check_value_drop(freq_50Avg, frequencies_values[1], st_dev(freq_50, freq_50Avg, LENGTH), "50Hz", time);
    check_value_drop(freq_55Avg, frequencies_values[2], st_dev(freq_55, freq_55Avg, LENGTH), "55Hz", time);
    check_value_drop(freq_60Avg, frequencies_values[3], st_dev(freq_60, freq_60Avg, LENGTH), "60Hz", time);
    check_value_drop(freq_65Avg, frequencies_values[4], st_dev(freq_65, freq_65Avg, LENGTH), "65Hz", time);
    check_value_drop(freq_70Avg, frequencies_values[5], st_dev(freq_70, freq_70Avg, LENGTH), "70Hz", time);
    
    //increment index
    
    running_avg_index ++; 
    Serial.println("Running Average Index:");
    Serial.println(running_avg_index);
    if (running_avg_index  == LENGTH) {
        running_avg_index  = 0;
        
        //perhaps zero the running average array (not very efficient since it creates a large drop)
        // for (int i=0; i < LENGTH; i++){
        //     freq_45[i] = 0.0;
        //     freq_50[i] = 0.0;
        //     freq_55[i] = 0.0;
        //     freq_60[i] = 0.0;
        //     freq_65[i] = 0.0;
        //     freq_70[i] = 0.0;
        // }

    }
    

    
    /*  should i change the std_dev only after some time, since it takes time to feel the change?, and continuing to update will constatly change the std_avg 
        to work in favor of the new avg. ???? 
    */
    
    freq_45Avg = check_st_dev(freq_45, LENGTH, freq_45Avg, "45Hz", time);
    freq_50Avg = check_st_dev(freq_50, LENGTH, freq_50Avg, "50Hz", time);
    freq_55Avg = check_st_dev(freq_55, LENGTH, freq_55Avg, "55Hz", time);
    freq_60Avg = check_st_dev(freq_60, LENGTH, freq_60Avg, "60Hz", time);
    freq_65Avg = check_st_dev(freq_65, LENGTH, freq_65Avg, "65Hz", time);
    freq_70Avg = check_st_dev(freq_70, LENGTH, freq_70Avg, "70Hz", time);
    

    //need to report only every 10 minutes !!! 
    // delay(600000) ?? 10 minutes
    // Serial.println(last_min_read);
    // Serial.println(Time.minute());
    // Serial.println(Time.minute() - last_min_read);
    if (Time.minute() - last_min_read >= 10) {
        last_min_read = Time.minute();
        report(freq_45Avg, "45Hz", time);
        report(freq_50Avg, "50Hz", time);
        report(freq_55Avg, "55Hz", time);
        report(freq_60Avg, "60Hz", time);
        report(freq_65Avg, "65Hz", time);
        report(freq_70Avg, "70Hz", time);
    }



    

    Serial.println("#####################Ending Period  #####################");
    Serial.println();
    
    delay(DELAY);
}


/* READING THE INPUT MIC */


void listenAndSend(int delay) {
    unsigned long startedListening = millis();
    unsigned int count = 0;
    while ((millis() - startedListening) < delay) {
        unsigned long time = micros();
        
        if (lastRead > time) {
            // time wrapped?
            //lets just skip a beat for now, whatever.
            lastRead = time;
        }
        
        //125 microseconds is 1/8000th of a second
        if ((time - lastRead) > samplingInterval) {
            lastRead = time;
            
            readMic();
            count++;
        }
    }
}

void readMic(void) {
    double value = analogRead(MICROPHONE_PIN);
    if (audioEndIdx >= SAMPLES) {
        audioEndIdx = 0;
    }
    vReal[audioEndIdx++] = value;
    delay(delayTime);
    
}

/* ******************************************************************************************* */

/*triggers a publish only if a threshold frquency have been triggered */
void report_freq(double frequency, double min_threshold, double max_threshold) {
    if ( min_threshold <= frequency && frequency <= max_threshold) {
        //convert double to string
        snprintf(output_peak, 50, "%f", frequency);
        // publish the peak to cloud
        Particle.publish("Major Peak: ",output_peak,60,PRIVATE);

    } 
    //can add a trigger to show missing frqencies in threshold range
    // else {
    //     Particle.publish("Missing Frquencies! ","",60,PRIVATE);
    // }
}




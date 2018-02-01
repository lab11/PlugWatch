#include "math.h"
#include "application.h"

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


void Compute(double *vReal, double *vImag, uint16_t samples, uint8_t dir);

void Compute(double *vReal, double *vImag, uint16_t samples, uint8_t power, uint8_t dir);

void Windowing(double *vData, uint16_t samples, uint8_t windowType, uint8_t dir);

void ComplexToMagnitude(double *vReal, double *vImag, uint16_t samples);

double MajorPeak(double *vD, uint16_t samples, double samplingFrequency);

String majorPeakFrequency(double *vD, uint16_t samples, double samplingFrequency);

String majorPeakFrequencyRange(double *vD, uint16_t samples, double samplingFrequency, double min_threshold, double max_threshold);

/*################ HELPER FUNCTIONS ################################################*/


/* Private functions */

void Swap(double *x, double *y);

uint8_t Exponent(uint16_t value);



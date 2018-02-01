#include <FFT.h>


/*################ FFT COMPUTATIONS ###############################################################*/

void Compute(double *vReal, double *vImag, uint16_t samples, uint8_t dir)
{
  Compute(vReal, vImag, samples, Exponent(samples), dir);
}

void Compute(double *vReal, double *vImag, uint16_t samples, uint8_t power, uint8_t dir)
{
/* Computes in-place complex-to-complex FFT */
  /* Reverse bits */
  uint16_t j = 0;
  for (uint16_t i = 0; i < (samples - 1); i++) {
    if (i < j) {
      Swap(&vReal[i], &vReal[j]);
      Swap(&vImag[i], &vImag[j]);
    }
    uint16_t k = (samples >> 1);
    while (k <= j) {
      j -= k;
      k >>= 1;
    }
    j += k;
  }
  /* Compute the FFT  */
  double c1 = -1.0;
  double c2 = 0.0;
  uint16_t l2 = 1;
  for (uint8_t l = 0; (l < power); l++) {
    uint16_t l1 = l2;
    l2 <<= 1;
    double u1 = 1.0;
    double u2 = 0.0;
    for (j = 0; j < l1; j++) {
       for (uint16_t i = j; i < samples; i += l2) {
          uint16_t i1 = i + l1;
          double t1 = u1 * vReal[i1] - u2 * vImag[i1];
          double t2 = u1 * vImag[i1] + u2 * vReal[i1];
          vReal[i1] = vReal[i] - t1;
          vImag[i1] = vImag[i] - t2;
          vReal[i] += t1;
          vImag[i] += t2;
       }
       double z = ((u1 * c1) - (u2 * c2));
       u2 = ((u1 * c2) + (u2 * c1));
       u1 = z;
    }
    c2 = sqrt((1.0 - c1) / 2.0);
    if (dir == FFT_FORWARD) {
      c2 = -c2;
    }
    c1 = sqrt((1.0 + c1) / 2.0);
  }
  /* Scaling for reverse transform */
  if (dir != FFT_FORWARD) {
    for (uint16_t i = 0; i < samples; i++) {
       vReal[i] /= samples;
       vImag[i] /= samples;
    }
  }
}

void ComplexToMagnitude(double *vReal, double *vImag, uint16_t samples)
{
/* vM is half the size of vReal and vImag */
  for (uint16_t i = 0; i < samples; i++) {
    vReal[i] = sqrt(pow(vReal[i],2) + pow(vImag[i],2));
  }
}

void Windowing(double *vData, uint16_t samples, uint8_t windowType, uint8_t dir)
{
/* Weighing factors are computed once before multiple use of FFT */
/* The weighing function is symetric; half the weighs are recorded */
  double samplesMinusOne = (double(samples) - 1.0);
  for (uint16_t i = 0; i < (samples >> 1); i++) {
    double indexMinusOne = double(i);
    double ratio = (indexMinusOne / samplesMinusOne);
    double weighingFactor = 1.0;
    /* Compute and record weighting factor */
    switch (windowType) {
    case FFT_WIN_TYP_RECTANGLE: /* rectangle (box car) */
      weighingFactor = 1.0;
      break;
    case FFT_WIN_TYP_HAMMING: /* hamming */
      weighingFactor = 0.54 - (0.46 * cos(twoPi * ratio));
      break;
    case FFT_WIN_TYP_HANN: /* hann */
      weighingFactor = 0.54 * (1.0 - cos(twoPi * ratio));
      break;
    case FFT_WIN_TYP_TRIANGLE: /* triangle (Bartlett) */
      weighingFactor = 1.0 - ((2.0 * abs(indexMinusOne - (samplesMinusOne / 2.0))) / samplesMinusOne);
      break;
    case FFT_WIN_TYP_BLACKMAN: /* blackmann */
      weighingFactor = 0.42323 - (0.49755 * (cos(twoPi * ratio))) + (0.07922 * (cos(fourPi * ratio)));
      break;
    case FFT_WIN_TYP_FLT_TOP: /* flat top */
      weighingFactor = 0.2810639 - (0.5208972 * cos(twoPi * ratio)) + (0.1980399 * cos(fourPi * ratio));
      break;
    case FFT_WIN_TYP_WELCH: /* welch */
      weighingFactor = 1.0 - pow((indexMinusOne - samplesMinusOne / 2.0) / (samplesMinusOne / 2.0),2);
      break;
    }
    if (dir == FFT_FORWARD) {
      vData[i] *= weighingFactor;
      vData[samples - (i + 1)] *= weighingFactor;
    }
    else {
      vData[i] /= weighingFactor;
      vData[samples - (i + 1)] /= weighingFactor;
    }
  }
}


/*Returns the most significant freq peak in the sampling period */
double MajorPeak(double *vD, uint16_t samples, double samplingFrequency)
{
  double maxY = 0;
  uint16_t IndexOfMaxY = 0;
  for (uint16_t i = 1; i < ((samples >> 1) - 1); i++) {
    if ((vD[i-1] < vD[i]) && (vD[i] > vD[i+1])) {
    //   if (vD[i] > maxY && vD[i] > 1000) { // adding an threshold of magnitude at least 1000 ?? 
        if (vD[i] > maxY) {  

            maxY = vD[i];
            IndexOfMaxY = i;
        }
    }
  }
  
  
  double delta = 0.5 * ((vD[IndexOfMaxY-1] - vD[IndexOfMaxY+1]) / (vD[IndexOfMaxY-1] - (2.0 * vD[IndexOfMaxY]) + vD[IndexOfMaxY+1]));
  double interpolatedX =  ((IndexOfMaxY + delta) * samplingFrequency) / (samples-1) ;
  double interpolatedX_o =   ((IndexOfMaxY) * samplingFrequency) / (samples-1)  ; //doesn't average --> provides a freq approx of sampplingfreq*index
  
  Serial.println("Index: ");
  Serial.println(IndexOfMaxY);
  
  Serial.println("average peak: ");
  Serial.println(interpolatedX);
  Serial.println("absolute peak: ");
  Serial.println(interpolatedX_o);
  /* retuned value: interpolated frequency peak apex */
  return(interpolatedX);
}

/*returns 3 major peaks, and their respective magnitudes in log */
String majorPeakFrequency(double *vD, uint16_t samples, double samplingFrequency) {
  double amp;
  int ind;
  String result = "";
  double maxY = 2;
  double maxY2 = 1;
  double maxY3 = 0;
  int IndexOfMaxY = 0;
  int IndexOfMaxY2 = 0;
  int IndexOfMaxY3 = 0;
  for (uint16_t i = 1; i < ((samples >> 1) - 1); i++) {
    if ((vD[i-1] < vD[i]) && (vD[i] > vD[i+1])) {
      if(vD[i] >= maxY3){
              maxY3 = vD[i];
        IndexOfMaxY3 = i;
      }
      if(vD[i] >= maxY2){
        amp = maxY3;
        ind = IndexOfMaxY3;
        maxY3 = maxY2;
              IndexOfMaxY3 = IndexOfMaxY2; 
        maxY2 = vD[i];
        IndexOfMaxY2 = i;
      
        if (vD[i] >= maxY) {
          maxY3 = amp;
          IndexOfMaxY3 = ind;
          maxY2 = maxY;
                IndexOfMaxY2 = IndexOfMaxY;
          maxY = vD[i];
          IndexOfMaxY = i;
        }
      }
    }
  }
  double delta1 = 0.5 * ((vD[IndexOfMaxY-1] - vD[IndexOfMaxY+1]) / (vD[IndexOfMaxY-1] - (2.0 * vD[IndexOfMaxY]) + vD[IndexOfMaxY+1]));
  double interpolatedX = ((IndexOfMaxY + delta1)  * samplingFrequency) / (samples-1);
  // retuned value: interpolated frequency peak apex
  double delta2 = 0.5 * ((vD[IndexOfMaxY2-1] - vD[IndexOfMaxY2+1]) / (vD[IndexOfMaxY2-1] - (2.0 * vD[IndexOfMaxY2]) + vD[IndexOfMaxY2+1]));
  double interpolatedX2 = ((IndexOfMaxY2 + delta2)  * samplingFrequency) / (samples-1);
  ////////////////////////////////////////
  double delta3 = 0.5 * ((vD[IndexOfMaxY3-1] - vD[IndexOfMaxY3+1]) / (vD[IndexOfMaxY3-1] - (2.0 * vD[IndexOfMaxY3]) + vD[IndexOfMaxY3+1]));
  double interpolatedX3 = ((IndexOfMaxY3 + delta3)  * samplingFrequency) / (samples-1);
//  result = "|" + String(int(interpolatedX)) + "," + String(int(interpolatedX2)) + "," + String(int(interpolatedX3)) + "," + String(int((10*log(maxY)))) + "," + String(int((10*log(maxY2)))) + "," + String(int((10*log(maxY3))));
  result = "|" + String(int(interpolatedX)) + "Hz, mag: " + String(int((10*log(maxY)))) + "| "  + String(int(interpolatedX2)) + "Hz, mag: " + String(int((10*log(maxY2)))) + "| "  + String(int(interpolatedX3)) + "Hz, mag: " + String(int((10*log(maxY3)))) + "| ";  
  return(result);  

}


/*returns 3 major peaks, and their respective magnitudes in log */
String majorPeakFrequencyRange(double *vD, uint16_t samples, double samplingFrequency, double min_threshold, double max_threshold) {
  double amp;
  double curr_freq;
  int ind;
  String result = "";
  double maxY = 2;
  double maxY2 = 1;
  double maxY3 = 0;
  int IndexOfMaxY = 0;
  int IndexOfMaxY2 = 0;
  int IndexOfMaxY3 = 0;
  for (uint16_t i = 1; i < ((samples >> 1) - 1); i++) {
      
/* should take a look at the curr if its correct!!!!! */      
      curr_freq = (i  * samplingFrequency) / (samples-1);
    if ((vD[i-1] < vD[i]) && (vD[i] > vD[i+1]) && ( min_threshold <= curr_freq && curr_freq <= max_threshold)) {
      if(vD[i] >= maxY3 ) {
              maxY3 = vD[i];
        IndexOfMaxY3 = i;
      }
      if(vD[i] >= maxY2 ){
        amp = maxY3;
        ind = IndexOfMaxY3;
        maxY3 = maxY2;
              IndexOfMaxY3 = IndexOfMaxY2; 
        maxY2 = vD[i];
        IndexOfMaxY2 = i;
      
        if (vD[i] >= maxY) {
          maxY3 = amp;
          IndexOfMaxY3 = ind;
          maxY2 = maxY;
                IndexOfMaxY2 = IndexOfMaxY;
          maxY = vD[i];
          IndexOfMaxY = i;
        }
      }
    }
  }
  double delta1 = 0.5 * ((vD[IndexOfMaxY-1] - vD[IndexOfMaxY+1]) / (vD[IndexOfMaxY-1] - (2.0 * vD[IndexOfMaxY]) + vD[IndexOfMaxY+1]));
  double interpolatedX = ((IndexOfMaxY + delta1)  * samplingFrequency) / (samples-1);
  // retuned value: interpolated frequency peak apex
  double delta2 = 0.5 * ((vD[IndexOfMaxY2-1] - vD[IndexOfMaxY2+1]) / (vD[IndexOfMaxY2-1] - (2.0 * vD[IndexOfMaxY2]) + vD[IndexOfMaxY2+1]));
  double interpolatedX2 = ((IndexOfMaxY2 + delta2)  * samplingFrequency) / (samples-1);
  ////////////////////////////////////////
  double delta3 = 0.5 * ((vD[IndexOfMaxY3-1] - vD[IndexOfMaxY3+1]) / (vD[IndexOfMaxY3-1] - (2.0 * vD[IndexOfMaxY3]) + vD[IndexOfMaxY3+1]));
  double interpolatedX3 = ((IndexOfMaxY3 + delta3)  * samplingFrequency) / (samples-1);
//  result = "|" + String(int(interpolatedX)) + "," + String(int(interpolatedX2)) + "," + String(int(interpolatedX3)) + "," + String(int((10*log(maxY)))) + "," + String(int((10*log(maxY2)))) + "," + String(int((10*log(maxY3))));
  result = "|" + String(int(interpolatedX)) + "Hz, mag: " + String(int((10*log(maxY)))) + "| "  + String(int(interpolatedX2)) + "Hz, mag: " + String(int((10*log(maxY2)))) + "| "  + String(int(interpolatedX3)) + "Hz, mag: " + String(int((10*log(maxY3)))) + "| ";  
  return(result);  

}


/*################ HELPER FUNCTIONS ################################################*/


/* Private functions */

void Swap(double *x, double *y)
{
  double temp = *x;
  *x = *y;
  *y = temp;
}

uint8_t Exponent(uint16_t value)
{
  /* Calculates the base 2 logarithm of a value */
  uint8_t result = 0;
  while (((value >> result) & 1) != 1) result++;
  return(result);
}




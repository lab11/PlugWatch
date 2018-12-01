Particle provisioning script
===========================

The script performs several functions:

1) Updates the core firmware on each particle to 0.7.0
2) Flashes the pre-deploy firmware which waits for a firmware update
3) Gets the device ID of the particle and appends it to a google spreadsheet for tracking
4) Gets the board ID of a particle to associate it with a specific plugwatch board
4) prints labels with the board ID and device ID for the plugwatch cases

## Usage

### Install the dependencies
```
$ pip install pygsheets
$ pip install pyqrcode
$ pip install pyscreen
$ pip install brother_ql #note that you may need to add ~/.local/bin to your path
```

### Get the google sheets key


### Run the script

Connect the particle and the printer to run the script

```
$ ./claim.py -p PRODUCT_ID
```

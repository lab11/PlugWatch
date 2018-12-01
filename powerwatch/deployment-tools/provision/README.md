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

### Setup keys

https://pygsheets.readthedocs.io/en/latest/authorization.html

At a high level you need to enable the google drive and google sheets APIs,
the create a consent form and a client oauth crediential.

You must also put a valid particle auth token in the particle-config.json file

### Run the script

Connect the particle and the printer to run the script.

```
$ sudo ./claim.py -p PRODUCT_ID
```

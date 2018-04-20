# Electron Sensor Board Rev 3
This sensor board is part of the [GridWatch](http://grid.watch) project.

The goal of this board is to test data collection in Accra. It contains 
an IMU with accel/mag and temp, two different microphones (one analog and
one digial), some memory, and a ultra-low power timer for future low-power 
optimizations. 

This directory will host development for all electron software and firmware.


Getting Started
---------------

Install the `particle-cli`, ideally just:

    npm install -g particle-cli

But I had to then also:

    npm i -g node-gyp
    ls ~/.particle     # note node version, mine was v5.4.1
    nvm use 5.4.1
    cd ~/.particle/node_modules/serialport
    node-gyp rebuild

Then blow out that terminal becase you don't really want that `nvm use` to persist.
If everything's hunky dory, you can now run `particle --version` or some such.

#### Installing dfu-util For Flashing Firmware

In order to use "particle flash" to locally re-flash the Electron firmware over USB, you will need dfu-util installed. On MacOS this should be sufficient:

    brew install dfu-util
    
Particle docs have more details for getting them on Windows or with MacPorts: https://docs.particle.io/faq/particle-tools/installing-dfu-util/core/

### Compiling

    particle compile electron
    
#### Using the Particle IDE

Particle has extended the Atom IDE to make a "Particle Dev" app: https://docs.particle.io/guide/tools-and-features/dev/

It's not really that different that the web IDE, including actually sending compilation to their cloud, but it allows you to work with local (and git-controlled) files, and also importantly doesn't have the horrible mouse scrolling of the web IDE that often leads to accidental "browser back" commands that lose your edits.

Install Particle Dev from the link above, run it, and:

    File->Open-> navigate to the "firmware" directory and select "open" (do not choose any individual file in it).
    Choose "Electron" as the target device in the bottom bar icon (may default to Photon).
    Log in to your particle account if it doesn't show you already logged in (left side of bottom bar or Particle-> menu).
    Compile using the Cloud-Checkmark icon in the left bar.
    
If all goes well you should see "Compiling in the cloud..." in the bottom bar and then a "Success!" message, and the firmware binary should appear in the `firmware` directory with a name starting like `electron_0.7.0_firmware_XXXXXXXX.bin`.

Note: if you hit the compile button and it instantly reports "Success!" without delay, and doesn't actually produce a firmware image, it probably means despite showing you logged in that you should logout and re-login.

#### Compiling Locally

The paricle CLI and IDE will use the cloud still, which is annoyingly slow IMHO.
While there's theoretically a path to _actual_ local compilation, down that road
lies madness. Your best bet is their [Atom plugin](https://atom.io/packages/particle-dev-local-compiler).
You'll need to install Docker first. Compiles are still slower than I'd like,
but only a few seconds at least. 

#### Cryptic error / error you didn't have before?

For example `error: 'LOW_BAT_UC' was not declared in this scope`

Make sure that you're building for the right device (an `electron`).
The IDE in particular tends to like to forget what you're building for,
especially across open/closing.

### Claim the particle (must do once)

  0. Plug in particle via USB
  1. Enter 'listening mode': Hold `MODE` button for three seconds, until the
     LED starts blinking blue.
  2. `particle identify`
  3. `particle device add <device id from last step>`

### Flashing

  1. Enter 'dfu mode': Reset and then hold `MODE` button for three seconds,
     until the LED starts blinking yellow.
  2. `particle flash --usb <filename>.bin`
  
#### Flashing made easier

`stty -f /dev/tty.usbmodem14321 14400 && particle flash --usb $(ls -t | grep firmware | head -1)`

The first part puts the particle into DFU mode. Note that you'll need to replace the `14321` in the above with whatever ID the USB Particle shows up on your machine (just use tab-completion).  The last part chooses the most
recent file based on filesystem timestamp.
  
### Listening to Serial

I recommend `particle serial monitor --follow`

Annoyingly, every time the particle resets, the modem disappears and reappears.
The `--follow` flag will automatically reconnect, but there's a bit of a delay,
so you're gonna lose the first few messages. I've never solved this puzzle.

### Listening to the Event Stream

The `Particle.publish()` command allows electron-initiated events to be published to the Particle Cloud; webhooks can then be used to do something with these events. However webhooks need to be specified per event name (or event name's starting prefix), which is somewhat obnoxious for development. 

Thankfully the entire event-stream for a Particle "product" is available as an HTML5 Server Side Event; the stream is accessible per-product via a URL like https://api.particle.io/v1/products/PRODUCT_SLUG/events?access_token=TOKENVALUE

where `PRODUCT_SLUG` (Particle terminology) is the short alpha-numeric name of the product (to get this click on a specific product in the Particle web console https://console.particle.io/products and pull the "slug" from the resulting URL, e.g. https://console.particle.io/PRODUCT_SLUG/devices).

#### Generating an API Token

As you see above the API requires an access token; this can be generated from the Web UI but the following code is useful for generating one with no expiration (which can be useful to avoid a situation like where your curl script for dumping the event stream doesn't check for API access errors and thus you get email from Particle complaining about hammering their API with failed authentication attempts):

`curl https://api.particle.io/oauth/token -u particle:particle -d grant_type=password -d 'username=YOUR_PARTICLE_ACCOUNT_EMAIL_ADDRESS' -d 'password=YOUR_PARTICLE_ACCOUNT_PASSWORD’ -d 'expires_in=0'`

The particle account should be one with access to the product(s) whose event streams you want to listen to. `particle:particle` is *NOT* a placeholder; it's ignored so you can leave it as is. The `-d 'expires_in=0'` is the key to creating the infinite-life token. If successful you should see curl return something like:

`{"token_type":"bearer","access_token":"0123456789abcdef0123456789abcdef01234567",
{"token":"0123456789abcdef0123456789abcdef01234567","expires_at":null,"client":"__PASSWORD_ONLY__"}`

You can then paste the `access_token` value into the `TOKENVALUE` part of the API URL above.






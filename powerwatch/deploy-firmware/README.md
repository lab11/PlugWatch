Firmware Deployment Script
==========================

The firmware deployment script takes a product binary, uploads it,
releases it to a product, then locks all devices in that product
to the firmware release.

The locking step seems to help devices better upgrade. I don't really understand
why that is the case.

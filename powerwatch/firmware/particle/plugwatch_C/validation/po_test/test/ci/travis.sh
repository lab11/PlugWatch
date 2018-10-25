#!/bin/bash
bash <(curl -sL https://master.po-util.com/ci-install)
po lib clean . -f &> /dev/null
yes "no" | po lib setup # change to "yes" to prefer libraries from GitHub
po electron build

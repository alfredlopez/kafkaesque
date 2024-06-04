#!/bin/sh

#
# Place this file as .profile under the project root directory
#

# Unzip encrypted contents...
#unzip -q -o -P $(< /app/pgp_key_to_keys) encrypted_props.zip

# Decrypt the strategy_dashboards
#cat /home/vcap/app/pgp_key_to_keys | gpg --cipher-algo AES --output /home/vcap/app/strategy_dashboards/strategy_dashboards.tar -v --no-use-agent --batch --passphrase-fd 0 -d /home/vcap/app/strategy_dashboards/strategy_dashboards-$RUNNING_ENV.tar
#cd /home/vcap/app/strategy_dashboards
#tar -xf /home/vcap/app/strategy_dashboards/strategy_dashboards.tar

#install BouncyCastle in the Java ext folder and update java.security
cd ~
cp /home/vcap/app/bouncycastle/* /home/vcap/app/.java-buildpack/open_jdk_jre/lib/ext/.
cp /home/vcap/app/java_security/* /home/vcap/app/.java-buildpack/open_jdk_jre/lib/security/.
mkdir -p /home/vcap/app/data/kafka/connect


package com.asanasoft.common.verticle;

import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

public class ZookeeperInstance extends DefaultQuartzVerticle {
    private Logger logger = LoggerFactory.getLogger(ZookeeperInstance.class);
    private ZooKeeperServerMain zookeeperServer;
    private Thread zkThread;
    /**
     * Code taken from org.apache.zookeeper.server.ZookeeperServerMain
     * @throws Exception
     */
    @Override
    public void start() throws Exception {
        logger.info("Configuring Zookeeper...");
        QuorumPeerConfig quorumPeerConfig = new QuorumPeerConfig();
        Properties properties = new Properties();
        properties.putAll(config().getJsonObject("config").getMap());
        quorumPeerConfig.parseProperties(properties);

        zookeeperServer = new ZooKeeperServerMain();
        final ServerConfig serverConfig = new ServerConfig();
        serverConfig.readFrom(quorumPeerConfig);

        logger.info("Starting Zookeeper...");


        zkThread = new Thread(() -> {
            try {
                zookeeperServer.runFromConfig(serverConfig);
            }
            catch (IOException e) {
                logger.error("Zookeeper failed to start", e);
            }
        });

        zkThread.start();
        logger.info("Zookeeper started...");
    }

    @Override
    public void stop() throws Exception {
        zkThread.interrupt();
    }
}

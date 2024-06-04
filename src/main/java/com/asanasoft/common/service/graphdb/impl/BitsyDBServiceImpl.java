package com.asanasoft.common.service.graphdb.impl;

import com.asanasoft.common.service.graphdb.BitsyDBService;
import io.vertx.core.Vertx;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by lopealf on 6/13/17.
 */
public class BitsyDBServiceImpl extends AbstractGraphDBService implements BitsyDBService {
    private Logger logger = LoggerFactory.getLogger(BitsyDBServiceImpl.class);

    public BitsyDBServiceImpl() {

    }

    public BitsyDBServiceImpl(final Graph newGraphDB, Vertx newVertx) {
        logger.debug("Creating BitsyDBServiceImpl...");
        setGraphDB(newGraphDB);
        setVertx(newVertx);
    }
}

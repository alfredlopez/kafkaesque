package com.asanasoft.common.service.graphdb;

import com.asanasoft.common.service.AbstractFactory;

/**
 * Created by lopealf on 6/12/17.
 */
public class GraphDBServiceFactory extends AbstractFactory<Class<? extends GraphDBService>> {
    @Override
    public boolean init() {
//        getComponents().put("OrientDB", ()-> OrientDBGraphDBService.class);
        getComponents().put("Bitsy",    ()-> BitsyDBService.class);
        return true;
    }
}

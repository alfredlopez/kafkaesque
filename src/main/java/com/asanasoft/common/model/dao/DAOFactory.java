package com.asanasoft.common.model.dao;

import com.asanasoft.common.service.AbstractFactory;

public class DAOFactory extends AbstractFactory<DAO> {

    @Override
    public boolean init() {
        getComponents().put("mysql",        () -> new MySQLJdbcDAO());
        getComponents().put("sqlserver",    () -> new MSSQLJdbcDAO());

        return true;
    }
}

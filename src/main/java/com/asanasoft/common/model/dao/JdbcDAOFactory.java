package com.asanasoft.common.model.dao;

import com.asanasoft.common.service.AbstractFactory;

public class JdbcDAOFactory extends AbstractFactory<JdbcDAO> {
    @Override
    public boolean init() {
        getComponents().put("mysql",        () -> new MySQLJdbcDAO());
        getComponents().put("sqlserver",    () -> new MSSQLJdbcDAO());

        return true;
    }
}

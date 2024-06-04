package com.asanasoft.common.model.dao;

import com.asanasoft.common.Context;

public class MySQLJdbcDAO extends JdbcDAO {
    @Override
    public String buildSql(Context args) {
        return super.buildSql(args) + limit(args);
    }

    @Override
    protected String limit(Context args) {
        String result = "";

        if (getPageSize(args) > 0) {
            result = " LIMIT " + getPageSize(args);
        }

        return result;
    }
}

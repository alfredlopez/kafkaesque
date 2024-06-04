package com.asanasoft.common.model.dao;

import com.asanasoft.common.Context;

public class MSSQLJdbcDAO extends JdbcDAO {
    @Override
    protected String select(Context args) {
        String result = "SELECT " + limit(args);

        return result + columns(args);
    }

    @Override
    protected String limit(Context args) {
        String result = " ";

        if (getPageSize(args) > 0) {
            result = " TOP " + getPageSize(args) + " ";
        }

        return result;
    }
}

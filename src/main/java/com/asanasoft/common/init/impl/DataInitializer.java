package com.asanasoft.common.init.impl;

import com.asanasoft.common.Context;
import com.asanasoft.common.init.AbstractInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DataInitializer extends AbstractInitializer {
    private Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Override
    public boolean init(Context newContext) {
        boolean result = true;
        String datasourceName = (String)newContext.getValue("datasourceName");

        if (datasourceName != null) {
            DataSource dataSource = null;

            try {
                InitialContext initialContext = new InitialContext();
                dataSource = ((DataSources)initialContext.lookup("dataSourceObj")).getDataSource(datasourceName);
            } catch (Exception e) {
                logger.error("An error occurred looking up datasourceObj", e);
                dataSource = DataSources.getInstance().getDataSource(datasourceName);
            }

            Connection connection = null;
            try {
                String sql = Environment.loadStringFromFile((String)newContext.getValue("ddlFile"));
                String[] sqlStatements = sql.split(";");
                connection = dataSource.getConnection();

                for (String statement : sqlStatements) {
                    connection.createStatement().execute(statement);
                }
            } catch (Exception e) {
                logger.error("An error occurred initializing data:", e);
                result = false;
            }
            finally {
                try {
                    if (!connection.isClosed()) {
                        connection.close();
                    }
                } catch (SQLException e) {
                }
            }
        }

        return result;
    }
}

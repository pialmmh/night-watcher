package com.tb.nw.plugins.mysql;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Minimal connection helper. Opens a fresh JDBC connection per probe — the
 * tick interval is long compared to MySQL's accept-and-auth cost, so pooling
 * isn't worth the dependency surface for the first cut.
 */
@ApplicationScoped
public class MysqlConnections {

    @Inject MysqlConfig cfg;

    public Connection openLocal() throws SQLException {
        return open(cfg.localHost());
    }

    public Connection openRemote() throws SQLException {
        String host = cfg.remoteHost()
                .orElseThrow(() -> new SQLException("nw.mysql.remote-host not configured"));
        return open(host);
    }

    public Connection open(String host) throws SQLException {
        String url = "jdbc:mariadb://" + host + ":" + cfg.port() + "/?connectTimeout="
                + (cfg.connectTimeoutSec() * 1000)
                + "&socketTimeout=" + (cfg.queryTimeoutSec() * 1000);
        Properties p = new Properties();
        p.setProperty("user", cfg.username());
        p.setProperty("password", cfg.password());
        return DriverManager.getConnection(url, p);
    }
}
